import base64
import json
import math
import re
from typing import Any, Protocol

from openai import APITimeoutError, AsyncOpenAI, OpenAIError
from pydantic import ValidationError

from finunity_server.config import Settings
from finunity_server.models import ModelRecognitionResult
from finunity_server.prompts import HOLDING_RECOGNITION_PROMPT, build_user_context


class RecognitionError(Exception):
    """Base error exposed to the API layer."""


class RecognitionNotConfiguredError(RecognitionError):
    pass


class RecognitionTimeoutError(RecognitionError):
    pass


class RecognitionUpstreamError(RecognitionError):
    pass


class RecognitionInvalidOutputError(RecognitionError):
    pass


class HoldingRecognizer(Protocol):
    model: str

    async def recognize(
        self,
        *,
        image_bytes: bytes,
        media_type: str,
        account_name: str | None,
        currency_hint: str | None,
    ) -> ModelRecognitionResult: ...


class OpenAIHoldingRecognizer:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self.model = settings.openai_model
        self._client: AsyncOpenAI | None = None

    def _get_client(self) -> AsyncOpenAI:
        if not self._settings.openai_api_key:
            raise RecognitionNotConfiguredError("服务端尚未配置 OPENAI_API_KEY")
        if self._client is None:
            kwargs: dict[str, object] = {
                "api_key": self._settings.openai_api_key,
                "timeout": self._settings.openai_timeout_seconds,
                "max_retries": 2,
            }
            if self._settings.openai_base_url:
                kwargs["base_url"] = self._settings.openai_base_url
            self._client = AsyncOpenAI(**kwargs)
        return self._client

    async def recognize(
        self,
        *,
        image_bytes: bytes,
        media_type: str,
        account_name: str | None,
        currency_hint: str | None,
    ) -> ModelRecognitionResult:
        data_url = f"data:{media_type};base64,{base64.b64encode(image_bytes).decode('ascii')}"
        try:
            response = await self._get_client().responses.parse(
                model=self.model,
                text_format=ModelRecognitionResult,
                instructions=HOLDING_RECOGNITION_PROMPT,
                input=[
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "input_text",
                                "text": build_user_context(
                                    account_name=account_name,
                                    currency_hint=currency_hint,
                                ),
                            },
                            {"type": "input_image", "image_url": data_url, "detail": "high"},
                        ],
                    }
                ],
                store=False,
            )
        except APITimeoutError as exc:
            raise RecognitionTimeoutError("大模型识别超时") from exc
        except OpenAIError as exc:
            raise RecognitionUpstreamError("大模型服务暂时不可用") from exc
        except ValidationError as exc:
            raise RecognitionInvalidOutputError("大模型返回结果不符合持仓结构") from exc

        if response.output_parsed is None:
            raise RecognitionInvalidOutputError("大模型未返回可解析结果")
        return response.output_parsed


class QwenHoldingRecognizer:
    """Recognize holding screenshots through Qwen's OpenAI-compatible vision API."""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self.model = settings.qwen_model
        self._client: AsyncOpenAI | None = None

    def _get_client(self) -> AsyncOpenAI:
        if not self._settings.qwen_api_key:
            raise RecognitionNotConfiguredError("服务端尚未配置 QWEN_API_KEY")
        if self._client is None:
            self._client = AsyncOpenAI(
                api_key=self._settings.qwen_api_key,
                base_url=self._settings.qwen_base_url,
                timeout=self._settings.qwen_timeout_seconds,
                max_retries=self._settings.qwen_max_retries,
            )
        return self._client

    @staticmethod
    def _parse_result(content: str) -> ModelRecognitionResult:
        normalized = content.strip()
        if normalized.startswith("```"):
            normalized = normalized.removeprefix("```json").removeprefix("```").strip()
            normalized = normalized.removesuffix("```").strip()
        try:
            payload = json.loads(normalized)
            if not isinstance(payload, dict):
                raise RecognitionInvalidOutputError("千问返回结果不是 JSON 对象")
            return ModelRecognitionResult.model_validate(
                QwenHoldingRecognizer._normalize_payload(payload)
            )
        except (json.JSONDecodeError, TypeError, ValidationError) as exc:
            raise RecognitionInvalidOutputError("千问返回结果不符合持仓 JSON 结构") from exc

    @staticmethod
    def _number(value: Any) -> float | None:
        if value is None or isinstance(value, bool):
            return None
        try:
            parsed = float(value)
        except (TypeError, ValueError):
            return None
        return parsed if math.isfinite(parsed) else None

    @staticmethod
    def _currency(value: Any) -> str | None:
        if not isinstance(value, str):
            return None
        normalized = value.strip().upper()
        return normalized if re.fullmatch(r"[A-Z]{3,5}", normalized) else None

    @staticmethod
    def _holding_from_model(item: dict[str, Any]) -> dict[str, Any] | None:
        name = item.get("name") or item.get("pair") or item.get("symbol")
        if not isinstance(name, str) or not name.strip():
            return None
        raw_type = str(item.get("asset_type") or "").upper()
        asset_type = {
            "POSITION": "CRYPTO",
            "SPOT": "CRYPTO",
            "STABLECOIN": "CRYPTO",
            "WALLET": "CASH",
        }.get(raw_type, raw_type)
        if asset_type not in {"STOCK", "ETF", "FUND", "CASH", "CRYPTO", "UNKNOWN"}:
            asset_type = "CRYPTO" if item.get("pair") else "UNKNOWN"
        contract_fields = {
            "pair",
            "position_quantity",
            "position_size_usdt",
            "entry_price",
            "entry_price_usdt",
            "mark_price",
            "mark_price_usdt",
            "liquidation_price",
            "leverage",
        }
        is_contract = raw_type in {"POSITION", "CONTRACT", "PERPETUAL", "FUTURES"} or bool(
            contract_fields.intersection(item)
        )
        currency = QwenHoldingRecognizer._currency(
            item.get("currency")
            or ("USDT" if any(key.endswith("_usdt") for key in item) else None)
        )
        quantity_unit = str(
            item.get("quantity_unit") or item.get("position_quantity_unit") or ""
        ).strip().upper() or None
        quantity_kind = str(item.get("quantity_kind") or "").strip().upper()
        raw_quantity = (
            item.get("quantity")
            if item.get("quantity") is not None
            else item.get("units", item.get("position_quantity"))
        )
        quantity = QwenHoldingRecognizer._number(raw_quantity)
        if quantity is None and item.get("position_size_usdt") is not None:
            quantity = QwenHoldingRecognizer._number(item.get("position_size_usdt"))
            quantity_unit = quantity_unit or "USDT"
            quantity_kind = "VALUE"
        if quantity_kind not in {"UNITS", "VALUE"}:
            quantity_kind = (
                "VALUE"
                if item.get("quantity_is_value") is True
                or (quantity_unit is not None and quantity_unit == currency)
                else "UNITS"
            )
        average_cost = QwenHoldingRecognizer._number(
            item.get("average_cost", item.get("entry_price_usdt", item.get("entry_price")))
        )
        current_price = QwenHoldingRecognizer._number(
            item.get("current_price", item.get("mark_price_usdt", item.get("mark_price")))
        )
        market_value = QwenHoldingRecognizer._number(
            item.get(
                "market_value",
                item.get("position_size_usdt", item.get("notional_value_usdt")),
            )
        )
        total_cost = QwenHoldingRecognizer._number(item.get("total_cost"))
        cost_source = item.get("cost_source")
        if cost_source not in {
            "DISPLAYED_AVERAGE",
            "DISPLAYED_TOTAL",
            "DISPLAYED_BOTH",
            "NOT_SHOWN",
        }:
            if average_cost is not None and total_cost is not None:
                cost_source = "DISPLAYED_BOTH"
            elif average_cost is not None:
                cost_source = "DISPLAYED_AVERAGE"
            elif total_cost is not None:
                cost_source = "DISPLAYED_TOTAL"
            else:
                cost_source = "NOT_SHOWN"
        reasons = item.get("review_reasons")
        if not isinstance(reasons, list):
            reasons = []
        if is_contract and (
            item.get("entry_price") is not None or item.get("entry_price_usdt") is not None
        ):
            reasons.append("合约开仓价按平均成本导入，请确认")
        if quantity_kind == "VALUE":
            reasons.append(
                f"持仓数量按 {quantity_unit or currency or '报价币种'} 金额处理，不是代币个数"
            )
        return {
            "asset_type": asset_type,
            "name": name.strip()[:200],
            "security_code": item.get("security_code") or item.get("pair") or item.get("symbol"),
            "quantity": quantity,
            "quantity_unit": quantity_unit,
            "quantity_kind": quantity_kind,
            "average_cost": average_cost,
            "total_cost": total_cost,
            "cost_source": cost_source,
            "current_price": current_price,
            "market_value": market_value,
            "currency": currency,
            "confidence": max(
                0.0,
                min(1.0, QwenHoldingRecognizer._number(item.get("confidence")) or 0.5),
            ),
            "needs_review": bool(item.get("needs_review", False)),
            "review_reasons": list(dict.fromkeys(str(reason) for reason in reasons))[:10],
        }

    @staticmethod
    def _normalize_payload(payload: dict[str, Any]) -> dict[str, Any]:
        """Convert common vision-model variants into the strict API contract."""
        raw_holdings = payload.get("holdings")
        if not isinstance(raw_holdings, list):
            raw_holdings = []
        normalized_holdings = [
            holding
            for item in raw_holdings
            if isinstance(item, dict)
            for holding in [QwenHoldingRecognizer._holding_from_model(item)]
            if holding is not None
        ]

        positions = payload.get("positions")
        if isinstance(positions, list):
            normalized_holdings.extend(
                holding
                for item in positions
                if isinstance(item, dict)
                for holding in [QwenHoldingRecognizer._holding_from_model(item)]
                if holding is not None
            )

        account_info = payload.get("account_info")
        if isinstance(account_info, dict):
            wallet_balance = QwenHoldingRecognizer._number(
                account_info.get("wallet_balance_usdt", account_info.get("wallet_balance"))
            )
            if wallet_balance is not None and wallet_balance > 0 and not any(
                str(item.get("name", "")).upper() == "USDT" for item in normalized_holdings
            ):
                normalized_holdings.append(
                    {
                        "asset_type": "CASH",
                        "name": "USDT",
                        "security_code": None,
                        "quantity": wallet_balance,
                        "average_cost": None,
                        "total_cost": None,
                        "cost_source": "NOT_SHOWN",
                        "current_price": 1,
                        "market_value": wallet_balance,
                        "currency": "USDT",
                        "confidence": 0.9,
                        "needs_review": True,
                        "review_reasons": ["钱包余额按现金资产导入，请确认账户币种"],
                    }
                )

        warnings = payload.get("warnings")
        if not isinstance(warnings, list):
            warnings = []
        if isinstance(positions, list):
            warnings.append("部分合约字段仅作为识别参考，未将保证金或名义价值当作数量")
        return {
            "detected_account_name": payload.get("detected_account_name"),
            "detected_provider": payload.get("detected_provider"),
            "as_of_date": payload.get("as_of_date"),
            "holdings": normalized_holdings,
            "warnings": list(dict.fromkeys(str(warning) for warning in warnings))[:20],
        }

    async def recognize(
        self,
        *,
        image_bytes: bytes,
        media_type: str,
        account_name: str | None,
        currency_hint: str | None,
    ) -> ModelRecognitionResult:
        data_url = f"data:{media_type};base64,{base64.b64encode(image_bytes).decode('ascii')}"
        instructions = (
            f"{HOLDING_RECOGNITION_PROMPT}\n\n"
            "如果图片中能看出银行、券商或基金平台名称，填写 detected_provider；"
            "如果能看出账户名称或页面日期，分别填写 detected_account_name 和 as_of_date。"
            "银行卡或钱包中明确显示的实际资产余额可以按 CASH 或 CRYPTO 返回；"
            "可用额度、保证金、可转出金额、收益和盈亏不要伪装成持仓，放入 warnings 说明。"
            "请只返回 JSON，不要返回 Markdown、解释或代码围栏。"
        )
        user_context = build_user_context(
            account_name=account_name,
            currency_hint=currency_hint,
        )
        try:
            response = await self._get_client().chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": instructions},
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": f"{user_context}\n请以 JSON 格式输出。"},
                            {"type": "image_url", "image_url": {"url": data_url}},
                        ],
                    },
                ],
                response_format={"type": "json_object"},
                max_completion_tokens=self._settings.qwen_max_output_tokens,
                temperature=0.1,
                extra_body={
                    "enable_thinking": self._settings.qwen_enable_thinking,
                    "max_pixels": self._settings.qwen_max_pixels,
                },
            )
        except APITimeoutError as exc:
            raise RecognitionTimeoutError("千问视觉识别超时") from exc
        except OpenAIError as exc:
            raise RecognitionUpstreamError("千问视觉识别服务暂时不可用") from exc

        content = response.choices[0].message.content if response.choices else None
        if not isinstance(content, str) or not content.strip():
            raise RecognitionInvalidOutputError("千问未返回可解析结果")
        return self._parse_result(content)
