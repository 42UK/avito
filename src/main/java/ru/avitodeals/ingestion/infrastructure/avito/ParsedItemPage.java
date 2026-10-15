package ru.avitodeals.ingestion.infrastructure.avito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ParsedItemPage(
		String externalId,
		String title,
		String description,
		BigDecimal price,
		String currency,
		String url,
		String condition,
		List<String> categoryPath,
		Map<String, String> attributes,
		SellerType sellerType,
		ParsedDate publishedAt) {

	public ParsedItemPage {
		categoryPath = List.copyOf(categoryPath);
		attributes = Map.copyOf(attributes);
	}
}
