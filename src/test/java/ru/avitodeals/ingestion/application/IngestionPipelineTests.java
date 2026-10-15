package ru.avitodeals.ingestion.application;

import org.junit.jupiter.api.Test;
import ru.avitodeals.listing.application.ListingCatalog;
import ru.avitodeals.listing.application.ListingChange;
import ru.avitodeals.listing.application.DataOrigin;
import ru.avitodeals.listing.application.ListingData;
import ru.avitodeals.listing.application.ListingSnapshot;
import ru.avitodeals.ingestion.infrastructure.MockListingSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestionPipelineTests {

	private static final Instant OBSERVED_AT = Instant.parse("2026-10-08T09:00:00Z");

	@Test
	void duplicateRowsAndRepeatedSnapshotsAreIdempotent() {
		RawListing item = rawListing("item-1", "100.00");
		MutableSource source = new MutableSource(List.of(item, item));
		FakeCatalog catalog = new FakeCatalog();
		List<Object> events = new ArrayList<>();
		IngestionPipeline pipeline = pipeline(source, catalog, events);

		IngestionReport firstRun = pipeline.ingest(IngestionRequest.fullSourceSnapshot());
		IngestionReport repeatedRun = pipeline.ingest(IngestionRequest.fullSourceSnapshot());

		assertEquals(new IngestionReport(1, 1, 1, 0, 0), firstRun);
		assertEquals(new IngestionReport(1, 1, 0, 0, 0), repeatedRun);
		assertEquals(1, catalog.listings.size());
		assertEquals(1, catalog.priceHistory.size());
		assertEquals(1, events.stream().filter(ListingIngested.class::isInstance).count());
		assertEquals(2, events.stream().filter(ListingObserved.class::isInstance).count());
	}

	@Test
	void priceChangesCreateHistoryAndMissingItemsAreRemovedOnce() {
		MutableSource source = new MutableSource(List.of(rawListing("item-1", "100.00")));
		FakeCatalog catalog = new FakeCatalog();
		List<Object> events = new ArrayList<>();
		IngestionPipeline pipeline = pipeline(source, catalog, events);

		pipeline.ingest(IngestionRequest.fullSourceSnapshot());
		source.setListings(List.of(rawListing("item-1", "80.00")));
		IngestionReport changed = pipeline.ingest(IngestionRequest.fullSourceSnapshot());
		source.setListings(List.of());
		IngestionReport removed = pipeline.ingest(IngestionRequest.fullSourceSnapshot());
		IngestionReport repeatedRemoval = pipeline.ingest(IngestionRequest.fullSourceSnapshot());

		assertEquals(1, changed.priceChanges());
		assertEquals(2, catalog.priceHistory.size());
		assertEquals(1, removed.removed());
		assertEquals(0, repeatedRemoval.removed());
		assertEquals("REMOVED", catalog.listings.get("item-1").status());
		assertEquals(1, events.stream().filter(ListingPriceChanged.class::isInstance).count());
		assertEquals(1, events.stream().filter(ListingRemoved.class::isInstance).count());
	}

	@Test
	void emptySearchSnapshotDoesNotRemoveListings() {
		MutableSource source = new MutableSource(List.of(rawListing("item-1", "100.00")));
		FakeCatalog catalog = new FakeCatalog();
		List<Object> events = new ArrayList<>();
		IngestionPipeline pipeline = pipeline(source, catalog, events);
		pipeline.ingest(IngestionRequest.fullSourceSnapshot());

		source.setListings(List.of());
		IngestionReport report = pipeline.ingest(IngestionRequest.forSearch(
				new SearchCriteria("unmatched", Map.of()), UUID.randomUUID()));

		assertEquals(0, report.removed());
		assertEquals("ACTIVE", catalog.listings.get("item-1").status());
		assertEquals(0, events.stream().filter(ListingRemoved.class::isInstance).count());
	}

	@Test
	void mockAdapterReturnsSourceNeutralRecordsAndHonorsSearchCriteria() {
		MockListingSource source = new MockListingSource();

		List<RawListing> results = source.fetch(new SearchCriteria("pixel", Map.of()));

		assertEquals("mock", source.sourceId());
		assertEquals(DataOrigin.DEMO, source.dataOrigin());
		assertEquals(1, results.size());
		assertTrue(results.getFirst().title().contains("Pixel"));
		assertFalse(List.of(RawListing.class.getRecordComponents()).stream()
				.anyMatch(component -> component.getName().toLowerCase().contains("avito")
						|| component.getName().equals("source")));
		assertInstanceOf(BigDecimal.class, results.getFirst().price());
		assertEquals("mock-1002", source.fetch(new SearchCriteria("", Map.of(
				"priceMax", new BigDecimal("20000"), "location", "санкт"))
		).getFirst().externalId());
	}

	private IngestionPipeline pipeline(MutableSource source, FakeCatalog catalog, List<Object> events) {
		return new IngestionPipeline(source, catalog, events::add,
				Clock.fixed(OBSERVED_AT, ZoneOffset.UTC));
	}

	private RawListing rawListing(String externalId, String price) {
		return new RawListing(externalId, "Test listing", "description", new BigDecimal(price),
				"RUB", "GOOD", "Moscow", "https://example.invalid/" + externalId, null, null);
	}

	private static final class MutableSource implements ListingSource {

		private List<RawListing> listings;

		private MutableSource(List<RawListing> listings) {
			this.listings = listings;
		}

		@Override
		public String sourceId() {
			return "test-source";
		}

		@Override
		public List<RawListing> fetch(SearchCriteria criteria) {
			return listings;
		}

		private void setListings(List<RawListing> listings) {
			this.listings = listings;
		}
	}

	private static final class FakeCatalog implements ListingCatalog {

		private final Map<String, ListingSnapshot> listings = new HashMap<>();
		private final List<BigDecimal> priceHistory = new ArrayList<>();

		@Override
		public ListingChange upsert(ListingData data, Instant observedAt) {
			ListingSnapshot existing = listings.get(data.externalId());
			if (existing == null) {
				ListingSnapshot created = new ListingSnapshot(UUID.randomUUID(), data.sourceId(),
						data.externalId(), data.price(), data.currency(), "ACTIVE", observedAt, observedAt);
				listings.put(data.externalId(), created);
				priceHistory.add(data.price());
				return new ListingChange(created, true, false, false, null, null);
			}
			boolean priceChanged = existing.price().compareTo(data.price()) != 0
					|| !existing.currency().equals(data.currency());
			ListingSnapshot updated = new ListingSnapshot(existing.id(), existing.sourceId(),
					existing.externalId(), data.price(), data.currency(), "ACTIVE",
					existing.firstSeenAt(), observedAt);
			listings.put(data.externalId(), updated);
			if (priceChanged) {
				priceHistory.add(data.price());
			}
			return new ListingChange(updated, false, false, priceChanged,
					priceChanged ? existing.price() : null,
					priceChanged ? existing.currency() : null);
		}

		@Override
		public List<ListingSnapshot> markMissing(
				String sourceId,
				Set<String> presentExternalIds,
				Instant observedAt) {
			List<ListingSnapshot> removed = new ArrayList<>();
			for (ListingSnapshot listing : List.copyOf(listings.values())) {
				if (listing.sourceId().equals(sourceId)
						&& listing.status().equals("ACTIVE")
						&& !presentExternalIds.contains(listing.externalId())) {
					ListingSnapshot marked = new ListingSnapshot(listing.id(), listing.sourceId(),
							listing.externalId(), listing.price(), listing.currency(), "REMOVED",
							listing.firstSeenAt(), listing.lastSeenAt());
					listings.put(listing.externalId(), marked);
					removed.add(marked);
				}
			}
			return removed;
		}
	}
}
