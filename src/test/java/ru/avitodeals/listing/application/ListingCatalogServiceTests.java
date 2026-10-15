package ru.avitodeals.listing.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.listing.domain.Listing;
import ru.avitodeals.listing.domain.ListingStatus;
import ru.avitodeals.listing.infrastructure.persistence.ListingPriceHistoryRepository;
import ru.avitodeals.listing.infrastructure.persistence.ListingRepository;
import ru.avitodeals.shared.application.TransactionScopedKeyLock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:listing-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@EntityScan(basePackages = "ru.avitodeals.listing.domain")
@EnableJpaRepositories(basePackages = "ru.avitodeals.listing.infrastructure.persistence")
@Import({ListingCatalogService.class, TransactionScopedKeyLock.class})
class ListingCatalogServiceTests {

	private static final Instant FIRST_SEEN = Instant.parse("2026-10-08T09:00:00Z");
	private static final Instant SECOND_SEEN = FIRST_SEEN.plusSeconds(60);
	private static final Instant THIRD_SEEN = SECOND_SEEN.plusSeconds(60);

	@Autowired
	private ListingCatalogService catalog;

	@Autowired
	private ListingRepository listingRepository;

	@Autowired
	private ListingPriceHistoryRepository priceHistoryRepository;

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void concurrentUpsertsForTheSameSourceAreSerializedThroughCommit() throws Exception {
		var pool = Executors.newFixedThreadPool(2);
		var start = new CountDownLatch(1);
		try {
			var first = pool.submit(() -> {
				start.await();
				return catalog.upsert(listingData("concurrent-1", "100.00"), FIRST_SEEN);
			});
			var second = pool.submit(() -> {
				start.await();
				return catalog.upsert(listingData("concurrent-1", "100.00"), FIRST_SEEN);
			});
			start.countDown();
			List<ListingChange> changes = List.of(first.get(10, TimeUnit.SECONDS),
					second.get(10, TimeUnit.SECONDS));

			assertEquals(1, changes.stream().filter(ListingChange::created).count());
			assertEquals(0, changes.stream().filter(ListingChange::priceChanged).count());
			Listing persisted = listingRepository.findBySourceAndExternalId("mock-source", "concurrent-1")
					.orElseThrow();
			assertEquals(1, priceHistoryRepository.findByListingIdOrderByObservedAtAsc(
					persisted.getId()).size());
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void repeatedListingRefreshesLastSeenWithoutDuplicatingPriceHistory() {
		ListingData initial = listingData("history-1", "100.00");
		ListingChange created = catalog.upsert(initial, FIRST_SEEN);
		ListingChange repeated = catalog.upsert(initial, SECOND_SEEN);
		ListingChange priceChanged = catalog.upsert(listingData("history-1", "80.00"), THIRD_SEEN);

		assertTrue(created.created());
		assertFalse(repeated.created());
		assertFalse(repeated.priceChanged());
		assertTrue(priceChanged.priceChanged());
		assertEquals(new BigDecimal("100.00"), priceChanged.previousPrice());
		assertEquals(2, priceHistoryRepository.findByListingIdOrderByObservedAtAsc(
				created.listing().id()).size());

		Listing persisted = listingRepository.findById(created.listing().id()).orElseThrow();
		assertEquals(FIRST_SEEN, persisted.getFirstSeenAt());
		assertEquals(THIRD_SEEN, persisted.getLastSeenAt());
		assertEquals(new BigDecimal("80.00"), persisted.getPrice());
	}

	@Test
	void authoritativeSnapshotsRemoveMissingListingsOnlyOnce() {
		ListingChange created = catalog.upsert(listingData("remove-1", "50.00"), FIRST_SEEN);

		var removed = catalog.markMissing("mock-source", Set.of(), SECOND_SEEN);
		var repeatedRemoval = catalog.markMissing("mock-source", Set.of(), THIRD_SEEN);

		assertEquals(1, removed.size());
		assertEquals(0, repeatedRemoval.size());
		assertEquals(ListingStatus.REMOVED.name(), removed.getFirst().status());
		assertEquals(ListingStatus.REMOVED, listingRepository.findById(created.listing().id())
				.orElseThrow().getStatus());
		assertEquals(1, priceHistoryRepository.findByListingIdOrderByObservedAtAsc(
				created.listing().id()).size());
	}

	private ListingData listingData(String externalId, String price) {
		return new ListingData("mock-source", externalId, "Listing " + externalId, null,
				new BigDecimal(price), "RUB", "GOOD", "Moscow",
				"https://example.invalid/" + externalId, null, null);
	}
}
