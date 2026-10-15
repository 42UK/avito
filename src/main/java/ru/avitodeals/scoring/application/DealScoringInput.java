package ru.avitodeals.scoring.application;

import ru.avitodeals.pricing.application.PersistedMarketPrice;
import ru.avitodeals.product.application.ExtractedAttributes;
import ru.avitodeals.product.application.ProductMatchResult;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record DealScoringInput(
		UUID listingId,
		BigDecimal candidatePrice,
		String candidateCurrency,
		String candidateCondition,
		PersistedMarketPrice persistedMarketPrice,
		ProductMatchResult productMatch,
		ExtractedAttributes attributes) {

	public DealScoringInput {
		Objects.requireNonNull(listingId, "listingId");
		Objects.requireNonNull(candidatePrice, "candidatePrice");
		Objects.requireNonNull(candidateCurrency, "candidateCurrency");
		Objects.requireNonNull(candidateCondition, "candidateCondition");
		Objects.requireNonNull(persistedMarketPrice, "persistedMarketPrice");
		Objects.requireNonNull(productMatch, "productMatch");
		Objects.requireNonNull(attributes, "attributes");
		Objects.requireNonNull(productMatch.statusCode(), "productMatch.status");
		Objects.requireNonNull(productMatch.matcherVersion(), "productMatch.matcherVersion");
		if (productMatch.matcherVersion().isBlank()) {
			throw new IllegalArgumentException("Product matcher version must not be blank");
		}
		if (productMatch.listingId() == null || !listingId.equals(productMatch.listingId())) {
			throw new IllegalArgumentException("Product match must belong to the candidate listing");
		}
		if (candidatePrice.signum() < 0 || !candidateCurrency.matches("[A-Za-z]{3}")) {
			throw new IllegalArgumentException("Candidate price or ISO currency is invalid");
		}
		if (!conditionCode(candidateCondition) || !conditionCode(persistedMarketPrice.conditionCode())) {
			throw new IllegalArgumentException("Candidate and market snapshot conditions must be known condition codes");
		}
		if (productMatch.confidence() == null
				|| productMatch.confidence().signum() < 0
				|| productMatch.confidence().compareTo(BigDecimal.ONE) > 0) {
			throw new IllegalArgumentException("Product match confidence must be between 0 and 1");
		}
	}

	private static boolean conditionCode(String condition) {
		return condition.equals("NEW") || condition.equals("GOOD") || condition.equals("FAIR")
				|| condition.equals("DEFECTIVE") || condition.equals("UNKNOWN");
	}
}
