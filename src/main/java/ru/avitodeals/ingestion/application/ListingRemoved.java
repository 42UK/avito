package ru.avitodeals.ingestion.application;

import java.time.Instant;
import java.util.UUID;

public record ListingRemoved(
		UUID listingId,
		String sourceId,
		String externalId,
		Instant observedAt) {
}
