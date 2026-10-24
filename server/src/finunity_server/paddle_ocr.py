import asyncio
import importlib.util
import json
from io import BytesIO
from numbers import Real
from threading import Lock
from typing import Any

from PIL import Image

from finunity_server.baidu_ocr import parse_baidu_holdings
from finunity_server.config import Settings
from finunity_server.models import ModelRecognitionResult
from finunity_server.service import (
    RecognitionError,
    RecognitionInvalidOutputError,
    RecognitionNotConfiguredError,
    RecognitionUpstreamError,
)


def _result_payload(result: Any) -> dict[str, Any]:
    """Read both PaddleOCR result objects and their serialized JSON form."""

    if isinstance(result, dict):
        payload: Any = result
    else:
        payload = getattr(result, "json", None)
        if callable(payload):
            payload = payload()
        if isinstance(payload, str):
            try:
                payload = json.loads(payload)
            except json.JSONDecodeError:
                return {}
        if not isinstance(payload, dict):
            payload = getattr(result, "res", None)
    if not isinstance(payload, dict):
        return {}
    nested = payload.get("res")
    return nested if isinstance(nested, dict) else payload


def _first_present(payload: dict[str, Any], *names: str) -> Any:
    for name in names:
        if name in payload and payload[name] is not None:
            return payload[name]
    return []


def _as_sequence(value: Any) -> list[Any]:
    if value is None or isinstance(value, (str, bytes)):
        return [] if value is None else [value]
    try:
        return list(value)
    except TypeError:
        return [value]


def _numbers(value: Any) -> list[float]:
    if hasattr(value, "tolist"):
        value = value.tolist()
    if isinstance(value, (list, tuple)):
        result: list[float] = []
        for item in value:
            result.extend(_numbers(item))
        return result
    if isinstance(value, Real) and not isinstance(value, bool):
        return [float(value)]
    return []


def _location(box: Any) -> dict[str, int]:
    coordinates = _numbers(box)
    if len(coordinates) < 4:
        return {"left": 0, "top": 0, "width": 0, "height": 0}
    xs = coordinates[0::2]
    ys = coordinates[1::2]
    left, right = min(xs), max(xs)
    top, bottom = min(ys), max(ys)
    return {
        "left": max(0, int(left)),
        "top": max(0, int(top)),
        "width": max(0, int(right - left)),
        "height": max(0, int(bottom - top)),
    }


def paddle_results_to_baidu_payload(results: Any) -> dict[str, Any]:
    """Normalize PaddleOCR output for the existing positioned holding parser."""

    if isinstance(results, dict) or hasattr(results, "json") or hasattr(results, "res"):
        raw_results = [results]
    else:
        raw_results = _as_sequence(results)

    words_result: list[dict[str, Any]] = []
    for raw_result in raw_results:
        payload = _result_payload(raw_result)
        if not payload:
            continue
        texts = _as_sequence(_first_present(payload, "rec_texts", "texts"))
        scores = _as_sequence(_first_present(payload, "rec_scores", "scores"))
        boxes = _as_sequence(_first_present(payload, "rec_boxes", "rec_polys", "dt_polys", "boxes"))
        for index, raw_text in enumerate(texts):
            text = str(raw_text).strip()
            if not text:
                continue
            raw_score = scores[index] if index < len(scores) else 0.75
            try:
                score = float(raw_score)
            except (TypeError, ValueError):
                score = 0.75
            words_result.append(
                {
                    "words": text,
                    "location": _location(boxes[index] if index < len(boxes) else None),
                    "probability": {"average": score},
                }
            )
    return {"words_result": words_result}


class PaddleOCRHoldingRecognizer:
    """Run PaddleOCR locally and reuse FinUnity's positioned holding parser."""

    model = "paddleocr-local"

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._pipeline: Any | None = None
        self._pipeline_lock = Lock()

    @property
    def available(self) -> bool:
        return importlib.util.find_spec("paddleocr") is not None and importlib.util.find_spec(
            "paddle"
        ) is not None

    def _get_pipeline(self) -> Any:
        if self._pipeline is not None:
            return self._pipeline
        with self._pipeline_lock:
            if self._pipeline is not None:
                return self._pipeline
            try:
                from paddleocr import PaddleOCR
            except ImportError as exc:
                raise RecognitionNotConfiguredError(
                    "服务端尚未安装 PaddleOCR 运行时，请安装 paddlepaddle 和 paddleocr"
                ) from exc
            self._pipeline = PaddleOCR(
                device=self._settings.paddleocr_device,
                lang=self._settings.paddleocr_lang,
                enable_mkldnn=self._settings.paddleocr_enable_mkldnn,
                use_doc_orientation_classify=self._settings.paddleocr_use_doc_orientation_classify,
                use_doc_unwarping=self._settings.paddleocr_use_doc_unwarping,
                use_textline_orientation=self._settings.paddleocr_use_textline_orientation,
            )
            return self._pipeline

    def _recognize_sync(self, image_bytes: bytes) -> dict[str, Any]:
        try:
            import numpy as np

            with Image.open(BytesIO(image_bytes)) as image:
                image_array = np.asarray(image.convert("RGB"))
        except (OSError, ValueError) as exc:
            raise RecognitionInvalidOutputError("图片内容无法读取") from exc

        try:
            results = self._get_pipeline().predict(image_array)
            return paddle_results_to_baidu_payload(results)
        except RecognitionError:
            raise
        except Exception as exc:
            raise RecognitionUpstreamError("PaddleOCR 本地推理失败，请检查运行时和模型") from exc

    async def recognize(
        self,
        *,
        image_bytes: bytes,
        media_type: str,
        account_name: str | None,
        currency_hint: str | None,
    ) -> ModelRecognitionResult:
        del media_type, account_name
        payload = await asyncio.to_thread(self._recognize_sync, image_bytes)
        return parse_baidu_holdings(payload, currency_hint=currency_hint)
