package ru.avitodeals.scoring.application;

import ru.avitodeals.listing.application.ListingView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DealCard(
		UUID id,
		ListingView listing,
		BigDecimal discountPercent,
		BigDecimal dealScore,
		BigDecimal riskScore,
		BigDecimal confidence,
		List<String> flags,
		List<Map<String, Object>> explanation,
		Instant computedAt) {

	public DealCard {
		flags = List.copyOf(flags);
		explanation = List.copyOf(explanation);
	}
}
