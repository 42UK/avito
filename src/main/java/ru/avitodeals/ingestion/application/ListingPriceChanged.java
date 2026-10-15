package ru.avitodeals.ingestion.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ListingPriceChanged(
		UUID listingId,
		BigDecimal previousPrice,
		String previousCurrency,
		BigDecimal currentPrice,
		String currentCurrency,
		Instant observedAt) {
}
