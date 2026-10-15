package ru.avitodeals.search.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.ingestion.application.ListingObserved;
import ru.avitodeals.search.domain.SearchListing;
import ru.avitodeals.search.domain.SearchListingId;
import ru.avitodeals.search.infrastructure.persistence.SearchListingRepository;
import ru.avitodeals.shared.application.TransactionScopedKeyLock;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:search-listener-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@EntityScan(basePackages = "ru.avitodeals.search.domain")
@EnableJpaRepositories(basePackages = "ru.avitodeals.search.infrastructure.persistence")
@Import({SearchIngestionEventListener.class, TransactionScopedKeyLock.class})
class SearchIngestionEventListenerTests {

	@Autowired
	private SearchIngestionEventListener listener;

	@Autowired
	private SearchListingRepository searchListingRepository;

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void concurrentObservationsKeepTheEarliestFirstSeenAndLatestLastSeen() throws Exception {
		UUID searchId = UUID.randomUUID();
		UUID listingId = UUID.randomUUID();
		Instant earlier = Instant.parse("2026-10-08T09:00:00Z");
		Instant later = earlier.plusSeconds(60);
		var pool = Executors.newFixedThreadPool(2);
		var start = new CountDownLatch(1);
		try {
			var first = pool.submit(() -> {
				start.await();
				listener.onListingObserved(event(searchId, listingId, later));
				return null;
			});
			var second = pool.submit(() -> {
				start.await();
				listener.onListingObserved(event(searchId, listingId, earlier));
				return null;
			});
			start.countDown();
			first.get(10, TimeUnit.SECONDS);
			second.get(10, TimeUnit.SECONDS);

			SearchListing relation = searchListingRepository.findById(
					new SearchListingId(searchId, listingId)).orElseThrow();
			assertEquals(earlier, relation.getFirstSeenAt());
			assertEquals(later, relation.getLastSeenAt());
			assertEquals(1, searchListingRepository.count());
		} finally {
			pool.shutdownNow();
		}
	}

	private ListingObserved event(UUID searchId, UUID listingId, Instant observedAt) {
		return new ListingObserved(listingId, "mock", "listing-1", searchId, observedAt);
	}
}
