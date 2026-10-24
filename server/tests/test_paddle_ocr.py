from types import SimpleNamespace

import pytest

from finunity_server.config import Settings
from finunity_server.paddle_ocr import (
    PaddleOCRHoldingRecognizer,
    paddle_results_to_baidu_payload,
)
from finunity_server.service import RecognitionNotConfiguredError


def test_paddle_results_are_normalized_to_positioned_ocr_lines() -> None:
    result = SimpleNamespace(
        json={
            "res": {
                "rec_texts": ["沪深300ETF", "510300", "100", "3.50", "3.60", "360.00"],
                "rec_scores": [0.99, 0.98, 0.97, 0.96, 0.95, 0.94],
                "rec_boxes": [
                    [20, 100, 160, 130],
                    [170, 100, 230, 130],
                    [240, 100, 280, 130],
                    [290, 100, 330, 130],
                    [340, 100, 380, 130],
                    [390, 100, 450, 130],
                ],
            }
        }
    )

    payload = paddle_results_to_baidu_payload([result])

    assert payload["words_result"][0]["words"] == "沪深300ETF"
    assert payload["words_result"][0]["location"] == {
        "left": 20,
        "top": 100,
        "width": 140,
        "height": 30,
    }
    assert payload["words_result"][0]["probability"] == {"average": 0.99}


def test_paddle_results_support_polygon_boxes() -> None:
    payload = paddle_results_to_baidu_payload(
        [
            {
                "rec_texts": ["510300"],
                "rec_scores": [0.9],
                "rec_polys": [[[10, 20], [50, 18], [52, 40], [12, 42]]],
            }
        ]
    )

    assert payload["words_result"][0]["location"] == {
        "left": 10,
        "top": 18,
        "width": 42,
        "height": 24,
    }


def test_paddle_recognizer_reports_missing_runtime() -> None:
    recognizer = PaddleOCRHoldingRecognizer(Settings(ocr_provider="paddleocr"))
    recognizer._get_pipeline = lambda: (_ for _ in ()).throw(  # type: ignore[method-assign]
        RecognitionNotConfiguredError("missing")
    )

    with pytest.raises(RecognitionNotConfiguredError):
        recognizer._get_pipeline()
