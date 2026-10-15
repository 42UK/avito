package ru.avitodeals.ingestion.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.listing.application.ListingCatalog;
import ru.avitodeals.listing.application.ListingChange;
import ru.avitodeals.listing.application.ListingData;
import ru.avitodeals.listing.application.ListingSnapshot;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@Service
public class IngestionPersistence {

	private final ListingCatalog catalog;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	public IngestionPersistence(ListingCatalog catalog, ApplicationEventPublisher eventPublisher, Clock clock) {
		this.catalog = catalog;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	/**
	 * Missing rows are removals only for an explicitly authoritative, full-source snapshot.
	 * A filtered search result cannot establish that a listing was removed from its source.
	 */
	@Transactional
	public IngestionReport persist(
			String sourceId,
			IngestionRequest request,
			List<RawListing> fetched,
			Instant observedAt) {
		if (fetched == null) {
			throw new IllegalStateException("Listing source returned no snapshot");
		}
		LinkedHashMap<String, RawListing> distinct = new LinkedHashMap<>();
		for (RawListing rawListing : fetched) {
			if (rawListing == null || rawListing.externalId() == null || rawListing.externalId().isBlank()) {
				throw new IllegalArgumentException("Every source listing must have an external ID");
			}
			distinct.putIfAbsent(rawListing.externalId(), rawListing);
		}

		Instant persistenceObservedAt = observedAt == null ? clock.instant() : observedAt;
		int created = 0;
		int priceChanges = 0;
		for (RawListing rawListing : distinct.values()) {
			ListingData data = toListingData(sourceId, rawListing);
			ListingChange change = catalog.upsert(data, persistenceObservedAt);
			ListingSnapshot listing = change.listing();
			eventPublisher.publishEvent(new ListingObserved(listing.id(), listing.sourceId(),
					listing.externalId(), request.searchId(), persistenceObservedAt));
			if (change.created() || change.reactivated()) {
				created++;
				eventPublisher.publishEvent(new ListingIngested(listing.id(), listing.sourceId(),
						listing.externalId(), request.searchId(), persistenceObservedAt));
			}
			if (change.priceChanged()) {
				priceChanges++;
				eventPublisher.publishEvent(new ListingPriceChanged(listing.id(), change.previousPrice(),
						change.previousCurrency(), listing.price(), listing.currency(), persistenceObservedAt));
			}
		}

		int removed = 0;
		if (request.authoritativeSourceSnapshot()) {
			Set<String> presentIds = distinct.keySet();
			List<ListingSnapshot> removedListings =
					catalog.markMissing(sourceId, presentIds, persistenceObservedAt);
			for (ListingSnapshot listing : removedListings) {
				eventPublisher.publishEvent(new ListingRemoved(
						listing.id(), listing.sourceId(), listing.externalId(), persistenceObservedAt));
			}
			removed = removedListings.size();
		}

		return new IngestionReport(distinct.size(), fetched.size() - distinct.size(),
				created, priceChanges, removed);
	}

	private ListingData toListingData(String sourceId, RawListing rawListing) {
		return new ListingData(sourceId, rawListing.externalId(), rawListing.title(),
				rawListing.description(), rawListing.price(), rawListing.currency(), rawListing.condition(),
				rawListing.location(), rawListing.url(), rawListing.sellerExternalId(), rawListing.sellerName());
	}
}
