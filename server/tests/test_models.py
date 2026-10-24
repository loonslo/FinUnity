from finunity_server.models import AssetType, CostSource, RecognizedHolding


def test_missing_cost_is_never_replaced_with_market_value() -> None:
    holding = RecognizedHolding(
        asset_type=AssetType.STOCK,
        name="示例股票",
        security_code="600000",
        quantity=100,
        average_cost=None,
        total_cost=None,
        cost_source=CostSource.NOT_SHOWN,
        current_price=12,
        market_value=1_200,
        currency="CNY",
        confidence=0.95,
        needs_review=False,
        review_reasons=[],
    )

    assert holding.average_cost is None
    assert holding.total_cost is None
    assert holding.needs_review is True
    assert "截图未明确显示持仓成本" in holding.review_reasons


def test_total_cost_can_derive_average_cost() -> None:
    holding = RecognizedHolding(
        asset_type=AssetType.FUND,
        name="示例基金",
        security_code="000001",
        quantity=2_000,
        average_cost=None,
        total_cost=3_000,
        cost_source=CostSource.DISPLAYED_TOTAL,
        current_price=None,
        market_value=None,
        currency="CNY",
        confidence=0.95,
        needs_review=False,
        review_reasons=[],
    )

    assert holding.average_cost == 1.5
    assert holding.needs_review is False
