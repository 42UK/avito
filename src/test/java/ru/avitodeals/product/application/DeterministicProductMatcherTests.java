package ru.avitodeals.product.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.listing.domain.Listing;
import ru.avitodeals.listing.domain.ListingCondition;
import ru.avitodeals.listing.domain.ListingStatus;
import ru.avitodeals.listing.infrastructure.persistence.ListingRepository;
import ru.avitodeals.product.domain.ListingProductMatch;
import ru.avitodeals.product.domain.MatchStatus;
import ru.avitodeals.product.infrastructure.persistence.ListingProductMatchRepository;
import ru.avitodeals.product.infrastructure.persistence.ProductAttributeRepository;
import ru.avitodeals.product.infrastructure.persistence.ProductRepository;
import ru.avitodeals.shared.application.TransactionScopedKeyLock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:product-matcher-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
@EntityScan(basePackages = {"ru.avitodeals.listing.domain", "ru.avitodeals.product.domain"})
@EnableJpaRepositories(basePackages = {
		"ru.avitodeals.listing.infrastructure.persistence",
		"ru.avitodeals.product.infrastructure.persistence"
})
@Import({
		DeterministicProductMatcher.class,
		DeterministicProductNormalizer.class,
		DeterministicAttributeExtractor.class,
		DeterministicProductClassifier.class,
		TransactionScopedKeyLock.class
})
class DeterministicProductMatcherTests {

	@Autowired
	private ProductMatcher matcher;

	@Autowired
	private ListingRepository listingRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductAttributeRepository productAttributeRepository;

	@Autowired
	private ListingProductMatchRepository matchRepository;

	@BeforeEach
	void clearPersistedData() {
		matchRepository.deleteAll();
		productAttributeRepository.deleteAll();
		productRepository.deleteAll();
		listingRepository.deleteAll();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void concurrentMatchingForOneListingPersistsOnlyOneMatchAndProduct() throws Exception {
		Listing listing = saveListing("iPhone 13 128GB");
		var pool = Executors.newFixedThreadPool(2);
		var start = new CountDownLatch(1);
		try {
			var first = pool.submit(() -> {
				start.await();
				return matcher.match(listing.getId(), listing.getTitle(), listing.getDescription());
			});
			var second = pool.submit(() -> {
				start.await();
				return matcher.match(listing.getId(), listing.getTitle(), listing.getDescription());
			});
			start.countDown();
			List<ProductMatchResult> results = List.of(
					first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

			assertEquals(results.getFirst().productId(), results.getLast().productId());
			assertEquals(1, productRepository.count());
			assertEquals(1, matchRepository.count());
			assertEquals(1, productAttributeRepository.count());
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void concurrentAliasesCreateOnlyOneCanonicalProduct() throws Exception {
		Listing english = saveListing("iPhone 13 128GB");
		Listing russian = saveListing("Айфон 13 128 ГБ");
		var pool = Executors.newFixedThreadPool(2);
		var start = new CountDownLatch(1);
		try {
			var first = pool.submit(() -> {
				start.await();
				return matcher.match(english.getId(), english.getTitle(), english.getDescription());
			});
			var second = pool.submit(() -> {
				start.await();
				return matcher.match(russian.getId(), russian.getTitle(), russian.getDescription());
			});
			start.countDown();
			List<ProductMatchResult> results = List.of(
					first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

			assertEquals(results.getFirst().productId(), results.getLast().productId());
			assertEquals(1, productRepository.count());
			assertEquals(2, matchRepository.count());
			assertEquals(1, productAttributeRepository.count());
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void exactAliasesPersistOneProductAndRepeatedMatchingIsIdempotent() {
		Listing englishListing = saveListing("iPhone 13 128GB");
		Listing russianListing = saveListing("Айфон 13 128 ГБ");

		ProductMatchResult first = matcher.match(
				englishListing.getId(), englishListing.getTitle(), englishListing.getDescription());
		ProductMatchResult repeated = matcher.match(
				englishListing.getId(), englishListing.getTitle(), englishListing.getDescription());
		ProductMatchResult alias = matcher.match(
				russianListing.getId(), russianListing.getTitle(), russianListing.getDescription());

		assertEquals(MatchStatus.MATCHED, first.status());
		assertEquals(new BigDecimal("0.9500"), first.confidence());
		assertEquals(first.productId(), repeated.productId());
		assertEquals(first.productId(), alias.productId());
		assertEquals(DeterministicProductMatcher.MATCHER_VERSION, first.matcherVersion());
		assertEquals(1, productRepository.count());
		assertEquals(2, matchRepository.count());
		assertEquals("128", productAttributeRepository.findByIdProductId(first.productId()).getFirst().getValue());

		ListingProductMatch stored = matchRepository.findById(englishListing.getId()).orElseThrow();
		assertEquals(MatchStatus.MATCHED, stored.getStatus());
		assertEquals(first.productId(), stored.getProductId());
		assertEquals(first.confidence(), stored.getConfidence());
		assertEquals(first.matcherVersion(), stored.getMatcherVersion());
	}

	@Test
	void unresolvedVariantPersistsAsAmbiguousWithoutAProductId() {
		Listing listing = saveListing("Redmi Note 12");

		ProductMatchResult result = matcher.match(listing.getId(), listing.getTitle(), listing.getDescription());

		assertEquals(MatchStatus.AMBIGUOUS, result.status());
		assertNull(result.productId());
		assertEquals(0, productRepository.count());
		ListingProductMatch stored = matchRepository.findById(listing.getId()).orElseThrow();
		assertEquals(MatchStatus.AMBIGUOUS, stored.getStatus());
		assertNull(stored.getProductId());
		assertEquals(new BigDecimal("0.0000"), stored.getConfidence());
	}

	@Test
	void conflictingStoragePersistsAsAmbiguousWithoutAProductId() {
		Listing listing = saveListing("iPhone 13 128GB");
		listing.setDescription("Продавец уточнил: 256 ГБ");
		listing = listingRepository.saveAndFlush(listing);

		ProductMatchResult result = matcher.match(listing.getId(), listing.getTitle(), listing.getDescription());

		assertEquals(MatchStatus.AMBIGUOUS, result.status());
		assertNull(result.productId());
		assertEquals(0, productRepository.count());
		assertEquals(MatchStatus.AMBIGUOUS,
				matchRepository.findById(listing.getId()).orElseThrow().getStatus());
	}

	@Test
	void accessoryRemainsUnmatchedAndStorageVariantsRemainSeparateProducts() {
		Listing accessory = saveListing("Чехол для iPhone 13");
		ProductMatchResult accessoryMatch = matcher.match(
				accessory.getId(), accessory.getTitle(), accessory.getDescription());
		assertEquals(MatchStatus.UNMATCHED, accessoryMatch.status());
		assertNull(accessoryMatch.productId());

		Listing base = saveListing("iPhone 15 128GB");
		Listing larger = saveListing("iPhone 15 256GB");
		ProductMatchResult baseMatch = matcher.match(base.getId(), base.getTitle(), base.getDescription());
		ProductMatchResult largerMatch = matcher.match(larger.getId(), larger.getTitle(), larger.getDescription());

		assertEquals(MatchStatus.MATCHED, baseMatch.status());
		assertEquals(MatchStatus.MATCHED, largerMatch.status());
		assertNotEquals(baseMatch.productId(), largerMatch.productId());
		assertEquals(2, productRepository.count());
	}

	@Test
	void exactMatchConfidenceMeetsTheGoldenDatasetThreshold() {
		Listing listing = saveListing("PS5 Disc Edition");

		ProductMatchResult result = matcher.match(listing.getId(), listing.getTitle(), listing.getDescription());

		assertTrue(result.confidence().compareTo(new BigDecimal("0.9000")) >= 0);
		assertNotNull(result.productId());
	}

	private Listing saveListing(String title) {
		Instant now = Instant.parse("2026-10-08T10:00:00Z");
		Listing listing = new Listing("mock", UUID.randomUUID().toString());
		listing.setTitle(title);
		listing.setDescription(null);
		listing.setPrice(new BigDecimal("100.00"));
		listing.setCurrency("RUB");
		listing.setCondition(ListingCondition.GOOD);
		listing.setUrl("https://example.invalid/listing");
		listing.setStatus(ListingStatus.ACTIVE);
		listing.setFirstSeenAt(now);
		listing.setLastSeenAt(now);
		return listingRepository.saveAndFlush(listing);
	}
}
