package ru.avitodeals.pricing.application;

import ru.avitodeals.pricing.domain.ConfidenceLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MarketPriceResult(
		UUID productId,
		BigDecimal marketPrice,
		String currency,
		BigDecimal median,
		BigDecimal p25,
		BigDecimal p75,
		int sampleSize,
		BigDecimal confidenceScore,
		ConfidenceLevel confidenceLevel,
		List<UUID> outliers,
		int outliersCount,
		String method,
		Instant computedAt) {

	public MarketPriceResult {
		Objects.requireNonNull(productId, "productId");
		Objects.requireNonNull(marketPrice, "marketPrice");
		Objects.requireNonNull(currency, "currency");
		Objects.requireNonNull(median, "median");
		Objects.requireNonNull(p25, "p25");
		Objects.requireNonNull(p75, "p75");
		Objects.requireNonNull(confidenceScore, "confidenceScore");
		Objects.requireNonNull(confidenceLevel, "confidenceLevel");
		outliers = List.copyOf(Objects.requireNonNull(outliers, "outliers"));
		Objects.requireNonNull(method, "method");
		Objects.requireNonNull(computedAt, "computedAt");
		if (sampleSize < 0 || outliersCount != outliers.size()
				|| confidenceScore.signum() < 0 || confidenceScore.compareTo(BigDecimal.ONE) > 0) {
			throw new IllegalArgumentException("Invalid market price result values");
		}
	}

	public String confidenceLevelCode() {
		return confidenceLevel.name();
	}
}
