package ru.avitodeals.ingestion.application;

import java.time.Instant;
import java.util.UUID;

public record ListingIngested(
		UUID listingId,
		String sourceId,
		String externalId,
		UUID searchId,
		Instant observedAt) {
}
