package ru.avitodeals.listing.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ListingSnapshot(
		UUID id,
		String sourceId,
		String externalId,
		BigDecimal price,
		String currency,
		String status,
		Instant firstSeenAt,
		Instant lastSeenAt) {
}
