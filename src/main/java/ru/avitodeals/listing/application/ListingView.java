package ru.avitodeals.listing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ListingView(
		UUID id,
		String source,
		String externalId,
		String title,
		String description,
		BigDecimal price,
		String currency,
		String condition,
		String location,
		String url,
		String status,
		UUID sellerId,
		Instant firstSeenAt,
		Instant lastSeenAt,
		DataOrigin dataOrigin) {

	public ListingView(
			UUID id,
			String source,
			String externalId,
			String title,
			String description,
			BigDecimal price,
			String currency,
			String condition,
			String location,
			String url,
			String status,
			UUID sellerId,
			Instant firstSeenAt,
			Instant lastSeenAt) {
		this(id, source, externalId, title, description, price, currency, condition, location,
				url, status, sellerId, firstSeenAt, lastSeenAt, DataOrigin.DEMO);
	}
}
