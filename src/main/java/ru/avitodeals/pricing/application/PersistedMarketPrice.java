package ru.avitodeals.pricing.application;

import ru.avitodeals.pricing.domain.ConditionGroup;

import java.util.Objects;
import java.util.UUID;

public record PersistedMarketPrice(
		UUID marketPriceId,
		ConditionGroup condition,
		MarketPriceResult result) {

	public PersistedMarketPrice {
		Objects.requireNonNull(marketPriceId, "marketPriceId");
		Objects.requireNonNull(condition, "condition");
		Objects.requireNonNull(result, "result");
	}

	public String conditionCode() {
		return condition.name();
	}
}
