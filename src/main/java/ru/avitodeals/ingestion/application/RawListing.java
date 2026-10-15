package ru.avitodeals.ingestion.application;

import java.math.BigDecimal;

public record RawListing(
		String externalId,
		String title,
		String description,
		BigDecimal price,
		String currency,
		String condition,
		String location,
		String url,
		String sellerExternalId,
		String sellerName) {
}
