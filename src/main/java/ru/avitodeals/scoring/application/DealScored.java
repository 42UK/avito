package ru.avitodeals.scoring.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record DealScored(
		UUID dealScoreId,
		UUID listingId,
		String listingTitle,
		String listingUrl,
		BigDecimal listingPrice,
		String currency,
		BigDecimal discountPercent,
		BigDecimal dealScore,
		BigDecimal riskScore,
		List<String> flags,
		Instant scoredAt) {

	public DealScored {
		Objects.requireNonNull(dealScoreId, "dealScoreId");
		Objects.requireNonNull(listingId, "listingId");
		Objects.requireNonNull(listingPrice, "listingPrice");
		Objects.requireNonNull(discountPercent, "discountPercent");
		Objects.requireNonNull(dealScore, "dealScore");
		Objects.requireNonNull(riskScore, "riskScore");
		Objects.requireNonNull(scoredAt, "scoredAt");
		if (listingTitle == null || listingTitle.isBlank()
				|| listingUrl == null || listingUrl.isBlank()
				|| currency == null || !currency.matches("[A-Za-z]{3}")) {
			throw new IllegalArgumentException("Listing title, URL and ISO currency are required");
		}
		flags = List.copyOf(Objects.requireNonNull(flags, "flags"));
	}
}
