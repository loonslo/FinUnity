from types import SimpleNamespace

import httpx
import pytest

from finunity_server.baidu_ocr import (
    BaiduHoldingRecognizer,
    parse_baidu_holdings,
)
from finunity_server.config import Settings
from finunity_server.models import ModelRecognitionResult
from finunity_server.service import (
    OpenAIHoldingRecognizer,
    QwenHoldingRecognizer,
    RecognitionInvalidOutputError,
)


class FakeResponses:
    def __init__(self, result: ModelRecognitionResult | None) -> None:
        self.result = result
        self.kwargs: dict[str, object] = {}

    async def parse(self, **kwargs: object) -> SimpleNamespace:
        self.kwargs = kwargs
        return SimpleNamespace(output_parsed=self.result)


class FakeClient:
    def __init__(self, result: ModelRecognitionResult | None) -> None:
        self.responses = FakeResponses(result)


class FakeChatCompletions:
    def __init__(self, content: str) -> None:
        self.content = content
        self.kwargs: dict[str, object] = {}

    async def create(self, **kwargs: object) -> SimpleNamespace:
        self.kwargs = kwargs
        return SimpleNamespace(
            choices=[SimpleNamespace(message=SimpleNamespace(content=self.content))]
        )


class FakeQwenClient:
    def __init__(self, content: str) -> None:
        self.chat = SimpleNamespace(completions=FakeChatCompletions(content))


@pytest.mark.asyncio
async def test_service_sends_data_url_and_pydantic_output_format() -> None:
    expected = ModelRecognitionResult(holdings=[], warnings=["没有持仓"])
    client = FakeClient(expected)
    recognizer = OpenAIHoldingRecognizer(Settings(openai_api_key="test"))
    recognizer._client = client  # type: ignore[assignment]

    actual = await recognizer.recognize(
        image_bytes=b"png-data",
        media_type="image/png",
        account_name=None,
        currency_hint="CNY",
    )

    assert actual == expected
    assert client.responses.kwargs["text_format"] is ModelRecognitionResult
    content = client.responses.kwargs["input"][0]["content"]  # type: ignore[index]
    assert content[1]["image_url"].startswith("data:image/png;base64,")
    assert client.responses.kwargs["store"] is False


@pytest.mark.asyncio
async def test_service_rejects_empty_parsed_output() -> None:
    recognizer = OpenAIHoldingRecognizer(Settings(openai_api_key="test"))
    recognizer._client = FakeClient(None)  # type: ignore[assignment]

    with pytest.raises(RecognitionInvalidOutputError):
        await recognizer.recognize(
            image_bytes=b"png-data",
            media_type="image/png",
            account_name=None,
            currency_hint=None,
        )


@pytest.mark.asyncio
async def test_qwen_service_sends_image_and_parses_json_output() -> None:
    expected = ModelRecognitionResult(holdings=[], warnings=["没有持仓"])
    client = FakeQwenClient(expected.model_dump_json())
    recognizer = QwenHoldingRecognizer(Settings(qwen_api_key="test"))
    recognizer._client = client  # type: ignore[assignment]

    actual = await recognizer.recognize(
        image_bytes=b"png-data",
        media_type="image/png",
        account_name="测试券商",
        currency_hint="CNY",
    )

    assert actual == expected
    kwargs = client.chat.completions.kwargs
    assert kwargs["model"] == "qwen3.7-flash"
    assert kwargs["response_format"] == {"type": "json_object"}
    assert kwargs["extra_body"] == {"enable_thinking": False, "max_pixels": 2_621_440}
    messages = kwargs["messages"]  # type: ignore[assignment]
    content = messages[1]["content"]  # type: ignore[index]
    assert content[1]["image_url"]["url"].startswith("data:image/png;base64,")  # type: ignore[index]


@pytest.mark.asyncio
async def test_qwen_service_normalizes_positions_and_wallet_balance() -> None:
    client = FakeQwenClient(
        '{"account_info":{"wallet_balance_usdt":455.46},'
        '"positions":[{"pair":"ZHIPUUSDT","position_size_usdt":299.82,'
        '"entry_price_usdt":94.37,"mark_price_usdt":94.57}]}'
    )
    recognizer = QwenHoldingRecognizer(Settings(qwen_api_key="test"))
    recognizer._client = client  # type: ignore[assignment]

    actual = await recognizer.recognize(
        image_bytes=b"jpeg-data",
        media_type="image/jpeg",
        account_name=None,
        currency_hint=None,
    )

    assert [item.name for item in actual.holdings] == ["ZHIPUUSDT", "USDT"]
    assert actual.holdings[0].quantity == 299.82
    assert actual.holdings[0].quantity_kind == "VALUE"
    assert actual.holdings[0].quantity_unit == "USDT"
    assert actual.holdings[0].market_value == 299.82
    assert actual.holdings[1].asset_type.value == "CASH"
    assert actual.holdings[1].quantity == 455.46


def test_qwen_contract_entry_price_is_reviewable_average_cost() -> None:
    normalized = QwenHoldingRecognizer._normalize_payload(
        {
            "positions": [
                {
                    "pair": "BTCUSDT",
                    "position_quantity": 0.5,
                    "entry_price_usdt": 62000,
                    "mark_price_usdt": 62500,
                }
            ]
        }
    )

    holding = normalized["holdings"][0]
    assert holding["average_cost"] == 62000
    assert holding["current_price"] == 62500
    assert "合约开仓价按平均成本导入，请确认" in holding["review_reasons"]


def test_baidu_parser_extracts_positioned_holding_row() -> None:
    result = parse_baidu_holdings(
        {
            "words_result": [
                {"words": "证券名称 证券代码 持仓数量 成本价 最新价 市值"},
                {
                    "words": "沪深300ETF 510300 100 3.50 3.60 360.00",
                    "probability": {"average": 0.98},
                    "location": {"left": 20, "top": 100, "width": 600, "height": 30},
                },
            ]
        },
        currency_hint="CNY",
    )

    assert len(result.holdings) == 1
    holding = result.holdings[0]
    assert holding.security_code == "510300"
    assert holding.name == "沪深300ETF"
    assert holding.quantity == 100
    assert holding.average_cost == 3.5
    assert holding.current_price == 3.6
    assert holding.market_value == 360
    assert holding.total_cost == 350


def test_baidu_parser_does_not_treat_available_quantity_as_cost() -> None:
    result = parse_baidu_holdings(
        {
            "words_result": [
                {"words": "证券代码 持仓数量 可用数量 成本价 最新价 市值"},
                {"words": "510300 100 80 3.50 3.60 360.00"},
            ]
        },
        currency_hint="CNY",
    )

    holding = result.holdings[0]
    assert holding.quantity == 100
    assert holding.average_cost == 3.5
    assert holding.current_price == 3.6
    assert holding.market_value == 360


@pytest.mark.asyncio
async def test_baidu_recognizer_fetches_token_and_calls_positioned_ocr() -> None:
    calls: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        if request.url.path.endswith("/oauth/2.0/token"):
            return httpx.Response(200, json={"access_token": "token", "expires_in": 3600})
        return httpx.Response(
            200,
            json={"words_result": [{"words": "交互测试ETF 510300 100 3.50 3.60 360.00"}]},
        )

    recognizer = BaiduHoldingRecognizer(
        Settings(baidu_ocr_api_key="key", baidu_ocr_secret_key="secret"),
        transport=httpx.MockTransport(handler),
    )
    result = await recognizer.recognize(
        image_bytes=b"png-data",
        media_type="image/png",
        account_name=None,
        currency_hint="CNY",
    )

    assert result.holdings[0].security_code == "510300"
    assert len(calls) == 2
    assert calls[1].url.path.endswith("/ocr/v1/general")
    assert calls[1].url.params["access_token"] == "token"
