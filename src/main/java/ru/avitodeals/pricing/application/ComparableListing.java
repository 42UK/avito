package ru.avitodeals.pricing.application;

import ru.avitodeals.pricing.domain.ConditionGroup;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record ComparableListing(
		UUID id,
		UUID productId,
		ConditionGroup condition,
		String currency,
		BigDecimal amount,
		boolean active) {

	public ComparableListing {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(productId, "productId");
		Objects.requireNonNull(condition, "condition");
		Objects.requireNonNull(currency, "currency");
		Objects.requireNonNull(amount, "amount");
		if (amount.signum() < 0) {
			throw new IllegalArgumentException("Comparable amount must not be negative");
		}
	}
}
