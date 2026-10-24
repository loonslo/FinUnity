import base64
import re
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Any

import httpx

from finunity_server.config import Settings
from finunity_server.models import ModelRecognitionResult, RecognizedHolding
from finunity_server.service import (
    RecognitionError,
    RecognitionInvalidOutputError,
    RecognitionNotConfiguredError,
    RecognitionTimeoutError,
    RecognitionUpstreamError,
)

BAIDU_TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
BAIDU_OCR_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/general"
SECURITY_CODE_PATTERN = re.compile(
    r"(?<![A-Za-z0-9])(?:(?:SH|SZ|HK)[.:]?)?(\d{5,6})(?!\d)", re.IGNORECASE
)
NUMBER_PATTERN = re.compile(r"[-+]?\d[\d,]*(?:\.\d+)?")
SKIP_LINE_MARKERS = (
    "合计",
    "总资产",
    "总市值",
    "账户余额",
    "可用资金",
    "冻结资金",
    "参考市值",
    "盈亏合计",
    "资产总额",
    "证券名称",
    "证券代码",
    "持仓数量",
    "持有数量",
    "成本价",
    "最新价",
    "市值",
)
AVAILABLE_HEADERS = ("可用数量", "可用股数", "可用份额")
COST_HEADERS = ("成本价", "单位成本", "持仓成本", "买入均价", "平均成本")
TOTAL_COST_HEADERS = ("总成本", "成本金额", "累计成本")
CURRENT_PRICE_HEADERS = ("最新价", "当前价", "市场价")
MARKET_VALUE_HEADERS = ("市值", "持仓市值", "市场价值")


@dataclass(frozen=True)
class BaiduOcrLine:
    text: str
    left: int = 0
    top: int = 0
    width: int = 0
    height: int = 0
    confidence: float = 0.75


def _clean_number(value: str) -> float | None:
    try:
        return float(value.replace(",", "").replace("，", ""))
    except ValueError:
        return None


def _asset_type_for_name(name: str) -> str:
    upper = name.upper()
    if "ETF" in upper or "交易型开放式" in name or "指数基金" in name:
        return "ETF"
    if "基金" in name or "LOF" in upper:
        return "FUND"
    if "股票" in name:
        return "STOCK"
    return "UNKNOWN"


def _baidu_lines(payload: dict[str, Any]) -> list[BaiduOcrLine]:
    raw_items = payload.get("words_result", [])
    if not isinstance(raw_items, list):
        return []
    lines: list[BaiduOcrLine] = []
    for item in raw_items:
        if not isinstance(item, dict):
            continue
        text = str(item.get("words", "")).strip()
        location = item.get("location")
        location = location if isinstance(location, dict) else {}
        probability = item.get("probability")
        probability = probability if isinstance(probability, dict) else {}
        average = probability.get("average")
        confidence = float(average) if isinstance(average, (int, float)) else 0.75
        lines.append(
            BaiduOcrLine(
                text=text,
                left=int(location.get("left", 0) or 0),
                top=int(location.get("top", 0) or 0),
                width=int(location.get("width", 0) or 0),
                height=int(location.get("height", 0) or 0),
                confidence=max(0.0, min(1.0, confidence)),
            )
        )
    return sorted(lines, key=lambda line: (line.top, line.left))


def parse_baidu_holdings(
    payload: dict[str, Any], *, currency_hint: str | None
) -> ModelRecognitionResult:
    """Convert Baidu positioned OCR lines into reviewable holding rows."""

    holdings: list[RecognizedHolding] = []
    warnings = ["OCR 返回文字后由本地规则解析，请确认识别结果"]
    lines = _baidu_lines(payload)
    header_text = " ".join(
        line.text for line in lines if any(marker in line.text for marker in SKIP_LINE_MARKERS)
    )
    has_available_column = any(header in header_text for header in AVAILABLE_HEADERS)
    has_total_cost_column = any(header in header_text for header in TOTAL_COST_HEADERS)
    has_current_price_column = any(header in header_text for header in CURRENT_PRICE_HEADERS)
    has_market_value_column = any(header in header_text for header in MARKET_VALUE_HEADERS)
    has_cost_column = any(header in header_text for header in COST_HEADERS)
    cost_index = 2 if has_available_column else 1
    current_index = cost_index + 1
    market_index = current_index + 1
    for line in lines:
        text = re.sub(r"[|｜]+", " ", line.text).strip()
        if not text or any(marker in text for marker in SKIP_LINE_MARKERS):
            continue
        code_match = SECURITY_CODE_PATTERN.search(text)
        if code_match is None:
            continue
        code = code_match.group(1)
        name = re.sub(r"[\s:：|｜,，;；]+$", "", text[: code_match.start()]).strip()
        if not name:
            name = "未命名持仓"
        numeric_values = [
            parsed
            for token in NUMBER_PATTERN.findall(text[code_match.end() :])
            if (parsed := _clean_number(token)) is not None
        ]
        if not numeric_values:
            continue

        quantity = numeric_values[0]
        average_cost = (
            numeric_values[cost_index]
            if has_cost_column and len(numeric_values) > cost_index
            else None
        )
        total_cost = (
            numeric_values[cost_index]
            if has_total_cost_column and len(numeric_values) > cost_index
            else None
        )
        current_price = (
            numeric_values[current_index]
            if has_current_price_column and len(numeric_values) > current_index
            else None
        )
        market_value = (
            numeric_values[market_index]
            if has_market_value_column and len(numeric_values) > market_index
            else None
        )
        if not has_cost_column and not has_total_cost_column and len(numeric_values) >= 2:
            average_cost = numeric_values[1]
        if not has_current_price_column and len(numeric_values) >= 3:
            current_price = numeric_values[2]
        if not has_market_value_column and len(numeric_values) >= 4:
            market_value = numeric_values[3]
        confidence = line.confidence
        review_reasons: list[str] = []
        if average_cost is None and total_cost is None:
            review_reasons.append("成本价未明确识别")
            confidence = min(confidence, 0.6)
        if current_price is None:
            review_reasons.append("最新价未明确识别")
            confidence = min(confidence, 0.7)
        if market_value is None:
            review_reasons.append("市值未明确识别")
            confidence = min(confidence, 0.75)
        if currency_hint is None:
            review_reasons.append("币种未明确")
        holdings.append(
            RecognizedHolding(
                asset_type=_asset_type_for_name(name),
                name=name,
                security_code=code,
                quantity=quantity if quantity > 0 else None,
                average_cost=(
                    average_cost if average_cost is not None and average_cost >= 0 else None
                ),
                total_cost=total_cost,
                cost_source=(
                    "DISPLAYED_BOTH"
                    if average_cost is not None and total_cost is not None
                    else (
                        "DISPLAYED_TOTAL"
                        if total_cost is not None
                        else ("DISPLAYED_AVERAGE" if average_cost is not None else "NOT_SHOWN")
                    )
                ),
                current_price=(
                    current_price if current_price is not None and current_price >= 0 else None
                ),
                market_value=(
                    market_value if market_value is not None and market_value >= 0 else None
                ),
                currency=currency_hint,
                confidence=confidence,
                needs_review=bool(review_reasons),
                review_reasons=review_reasons,
            )
        )
    if not holdings:
        warnings.append("未找到同时包含证券代码和数量的持仓行")
    return ModelRecognitionResult(holdings=holdings, warnings=warnings)


class BaiduHoldingRecognizer:
    model = "baidu-general-positioned"

    def __init__(
        self, settings: Settings, *, transport: httpx.AsyncBaseTransport | None = None
    ) -> None:
        self._settings = settings
        self._transport = transport
        self._access_token: str | None = None
        self._access_token_expires_at: datetime | None = None

    async def _token(self) -> str:
        api_key = self._settings.baidu_ocr_api_key
        secret_key = self._settings.baidu_ocr_secret_key
        if not api_key or not secret_key:
            raise RecognitionNotConfiguredError(
                "服务端尚未配置 BAIDU_OCR_API_KEY 和 BAIDU_OCR_SECRET_KEY"
            )
        now = datetime.now(UTC)
        if (
            self._access_token
            and self._access_token_expires_at
            and now < self._access_token_expires_at
        ):
            return self._access_token
        try:
            async with httpx.AsyncClient(
                timeout=self._settings.baidu_ocr_timeout_seconds, transport=self._transport
            ) as client:
                response = await client.post(
                    BAIDU_TOKEN_URL,
                    params={
                        "grant_type": "client_credentials",
                        "client_id": api_key,
                        "client_secret": secret_key,
                    },
                )
                response.raise_for_status()
                body = response.json()
        except httpx.TimeoutException as exc:
            raise RecognitionTimeoutError("百度 OCR 鉴权超时") from exc
        except (httpx.HTTPError, ValueError) as exc:
            raise RecognitionUpstreamError("百度 OCR 鉴权服务暂时不可用") from exc
        token = body.get("access_token") if isinstance(body, dict) else None
        if not isinstance(token, str) or not token:
            raise RecognitionUpstreamError("百度 OCR 鉴权失败")
        expires_in = body.get("expires_in", 2_592_000) if isinstance(body, dict) else 2_592_000
        expires_seconds = int(expires_in) if isinstance(expires_in, (int, float)) else 2_592_000
        self._access_token = token
        self._access_token_expires_at = now + timedelta(seconds=max(60, expires_seconds - 60))
        return token

    async def _recognize_words(self, image_bytes: bytes) -> dict[str, Any]:
        image_base64 = base64.b64encode(image_bytes).decode("ascii")
        if len(image_base64) > 4 * 1024 * 1024:
            raise RecognitionInvalidOutputError("图片超过百度 OCR 4MB 接口限制，请压缩后重试")
        try:
            token = await self._token()
            async with httpx.AsyncClient(
                timeout=self._settings.baidu_ocr_timeout_seconds, transport=self._transport
            ) as client:
                response = await client.post(
                    BAIDU_OCR_URL,
                    params={"access_token": token},
                    data={
                        "image": image_base64,
                        "language_type": "CHN_ENG",
                        "recognize_granularity": "big",
                        "detect_direction": "true",
                        "vertexes_location": "true",
                        "probability": "true",
                    },
                )
                response.raise_for_status()
                body = response.json()
        except RecognitionError:
            raise
        except httpx.TimeoutException as exc:
            raise RecognitionTimeoutError("百度 OCR 识别超时") from exc
        except (httpx.HTTPError, ValueError) as exc:
            raise RecognitionUpstreamError("百度 OCR 服务暂时不可用") from exc
        if not isinstance(body, dict):
            raise RecognitionInvalidOutputError("百度 OCR 返回结果格式无效")
        if body.get("error_code"):
            raise RecognitionUpstreamError("百度 OCR 返回错误")
        return body

    async def recognize(
        self,
        *,
        image_bytes: bytes,
        media_type: str,
        account_name: str | None,
        currency_hint: str | None,
    ) -> ModelRecognitionResult:
        del media_type, account_name
        return parse_baidu_holdings(
            await self._recognize_words(image_bytes), currency_hint=currency_hint
        )
