package ru.avitodeals.listing.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ListingCatalog {

	ListingChange upsert(ListingData listing, Instant observedAt);

	List<ListingSnapshot> markMissing(
			String sourceId,
			Set<String> presentExternalIds,
			Instant observedAt);

	default Optional<ListingNotificationDetails> findNotificationDetails(UUID listingId) {
		return Optional.empty();
	}
}
