package ru.avitodeals.search.application;

import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.search.domain.Search;
import ru.avitodeals.search.infrastructure.persistence.SearchRepository;
import ru.avitodeals.shared.application.TransactionScopedKeyLock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:search-claim-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@EntityScan(basePackages = "ru.avitodeals.search.domain")
@EnableJpaRepositories(basePackages = "ru.avitodeals.search.infrastructure.persistence")
@Import({SearchRunClaimService.class, TransactionScopedKeyLock.class,
		SearchRunClaimServiceTests.FixedClockConfiguration.class})
class SearchRunClaimServiceTests {

	private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

	@Autowired
	private SearchRepository repository;

	@Autowired
	private SearchRunClaimService claimService;

	@Autowired
	private EntityManager entityManager;

	@Test
	void dueSearchIsClaimedAndLastRunTimePersistsAcrossReload() throws ReflectiveOperationException {
		Search search = createSearch(true, null);
		repository.saveAndFlush(search);

		assertTrue(claimService.claimIfDue(search.getId()).isPresent());
		entityManager.flush();
		entityManager.clear();
		assertEquals(NOW, repository.findById(search.getId()).orElseThrow().getLastRunAt());
		assertTrue(claimService.claimIfDue(search.getId()).isEmpty());
	}

	@Test
	void searchIsNotDueUntilItsIntervalHasElapsed() throws ReflectiveOperationException {
		Search search = createSearch(true, NOW.minusSeconds(59 * 60L));
		repository.saveAndFlush(search);

		assertFalse(claimService.claimIfDue(search.getId()).isPresent());
		assertEquals(NOW.minusSeconds(59 * 60L),
				repository.findById(search.getId()).orElseThrow().getLastRunAt());
	}

	@Test
	void rateLimitedClaimCanBeReleasedWithoutWaitingForTheSearchInterval() throws ReflectiveOperationException {
		Search search = createSearch(true, null);
		repository.saveAndFlush(search);
		SearchRunSnapshot claim = claimService.claimIfDue(search.getId()).orElseThrow();

		claimService.releaseRateLimitedClaim(search.getId(), claim.claimedAt());

		entityManager.flush();
		entityManager.clear();
		assertNull(repository.findById(search.getId()).orElseThrow().getLastRunAt());
		assertTrue(claimService.claimIfDue(search.getId()).isPresent());
	}

	@Test
	void inactiveSearchIsNeverClaimed() throws ReflectiveOperationException {
		Search search = createSearch(false, null);
		repository.saveAndFlush(search);

		assertTrue(claimService.claimIfDue(search.getId()).isEmpty());
		assertNull(repository.findById(search.getId()).orElseThrow().getLastRunAt());
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void concurrentClaimsForOneSearchProduceOnlyOneRun() throws Exception {
		Search search = createSearch(true, null);
		repository.saveAndFlush(search);
		var pool = Executors.newFixedThreadPool(2);
		var start = new CountDownLatch(1);
		try {
			var first = pool.submit(() -> {
				start.await();
				return claimService.claimIfDue(search.getId()).isPresent();
			});
			var second = pool.submit(() -> {
				start.await();
				return claimService.claimIfDue(search.getId()).isPresent();
			});
			start.countDown();

			assertEquals(1, (int) (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
					+ (second.get(10, TimeUnit.SECONDS) ? 1 : 0));
		} finally {
			pool.shutdownNow();
		}
	}

	private Search createSearch(boolean active, Instant lastRunAt) throws ReflectiveOperationException {
		var constructor = Search.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		Search search = constructor.newInstance();
		search.setName("Test search");
		search.setQuery("Pixel");
		search.setFilters(new HashMap<>());
		search.setIntervalMinutes(60);
		search.setActive(active);
		search.setLastRunAt(lastRunAt);
		return search;
	}

	@Configuration
	static class FixedClockConfiguration {

		@Bean
		Clock clock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}
}
