package ru.avitodeals.pricing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MarketPriceView(
		UUID id,
		UUID productId,
		String condition,
		BigDecimal marketPrice,
		String currency,
		BigDecimal median,
		BigDecimal p25,
		BigDecimal p75,
		int sampleSize,
		BigDecimal confidenceScore,
		String confidenceLevel,
		List<UUID> outlierIds,
		String method,
		Instant computedAt) {

	public MarketPriceView {
		outlierIds = List.copyOf(outlierIds);
	}
}
