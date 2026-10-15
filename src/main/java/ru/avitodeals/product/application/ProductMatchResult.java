package ru.avitodeals.product.application;

import ru.avitodeals.product.domain.MatchStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductMatchResult(
		UUID listingId,
		UUID productId,
		BigDecimal confidence,
		MatchStatus status,
		String matcherVersion) {

	public String statusCode() {
		return status.name();
	}
}
