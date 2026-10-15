package ru.avitodeals.listing.application;

import java.math.BigDecimal;

public record ListingData(
		String sourceId,
		String externalId,
		String title,
		String description,
		BigDecimal price,
		String currency,
		String condition,
		String location,
		String url,
		String sellerExternalId,
		String sellerName,
		DataOrigin dataOrigin) {

	public ListingData(
			String sourceId,
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
		this(sourceId, externalId, title, description, price, currency, condition, location, url,
				sellerExternalId, sellerName, DataOrigin.DEMO);
	}
}
