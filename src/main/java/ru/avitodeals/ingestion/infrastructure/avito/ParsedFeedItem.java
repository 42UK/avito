package ru.avitodeals.ingestion.infrastructure.avito;

import java.math.BigDecimal;

public record ParsedFeedItem(
		String externalId,
		String title,
		BigDecimal price,
		String currency,
		String url,
		String regionSlug,
		String description,
		ParsedDate publishedAtApprox) {
}
