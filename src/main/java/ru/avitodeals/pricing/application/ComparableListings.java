package ru.avitodeals.pricing.application;

import ru.avitodeals.pricing.domain.ConditionGroup;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ComparableListings(
		UUID candidateId,
		UUID productId,
		ConditionGroup condition,
		String currency,
		List<ComparableListing> listings) {

	public ComparableListings {
		Objects.requireNonNull(candidateId, "candidateId");
		Objects.requireNonNull(productId, "productId");
		Objects.requireNonNull(condition, "condition");
		Objects.requireNonNull(currency, "currency");
		if (!currency.matches("[A-Z]{3}")) {
			throw new IllegalArgumentException("Currency must be an uppercase ISO 4217 code");
		}
		listings = List.copyOf(Objects.requireNonNull(listings, "listings"));
	}
}
