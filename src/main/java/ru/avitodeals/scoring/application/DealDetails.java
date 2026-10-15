package ru.avitodeals.scoring.application;

import ru.avitodeals.listing.application.ListingPricePoint;
import ru.avitodeals.listing.application.ListingView;
import ru.avitodeals.pricing.application.MarketPriceView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DealDetails(
		UUID id,
		ListingView listing,
		BigDecimal discountPercent,
		BigDecimal dealScore,
		BigDecimal riskScore,
		BigDecimal confidence,
		List<String> flags,
		List<Map<String, Object>> explanation,
		Instant computedAt,
		MarketPriceView marketPrice,
		List<DealComparable> comparables,
		List<UUID> outlierIds,
		List<ListingPricePoint> priceHistory,
		String comparablesBasis) {

	public DealDetails {
		flags = List.copyOf(flags);
		explanation = List.copyOf(explanation);
		comparables = List.copyOf(comparables);
		outlierIds = List.copyOf(outlierIds);
		priceHistory = List.copyOf(priceHistory);
	}
}
