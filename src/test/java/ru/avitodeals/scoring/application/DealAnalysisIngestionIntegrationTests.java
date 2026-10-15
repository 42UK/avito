package ru.avitodeals.scoring.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.avitodeals.ingestion.application.IngestionPersistence;
import ru.avitodeals.ingestion.application.IngestionRequest;
import ru.avitodeals.ingestion.application.RawListing;
import ru.avitodeals.ingestion.application.SearchCriteria;
import ru.avitodeals.listing.infrastructure.persistence.ListingRepository;
import ru.avitodeals.pricing.infrastructure.persistence.MarketPriceRepository;
import ru.avitodeals.product.infrastructure.persistence.ListingProductMatchRepository;
import ru.avitodeals.scoring.infrastructure.persistence.DealScoreRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:deal-analysis-ingestion-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"avito-deals.ingestion.source=mock",
		"avito-deals.search.scheduler.fixed-delay-ms=3600000"
})
class DealAnalysisIngestionIntegrationTests {

	@Autowired
	private IngestionPersistence ingestionPersistence;

	@Autowired
	private ListingProductMatchRepository productMatches;

	@Autowired
	private DealScoreRepository dealScores;

	@Autowired
	private MarketPriceRepository marketPrices;

	@Autowired
	private ListingRepository listings;

	@Test
	void ingestionMatchesListingsPricesOnlyWithinSameSourceAndRescoresAffectedPeers() {
		Instant observedAt = Instant.parse("2026-10-15T12:00:00Z");
		assertEquals(1, ingestAvito(listing("avito-1", "10000.00",
				"https://www.avito.ru/moscow/avito-1"), observedAt));
		assertEquals(1, ingestAvito(listing("avito-2", "20000.00",
				"https://www.avito.ru/moscow/avito-2"), observedAt));
		var demoReport = ingestionPersistence.persist("demo", IngestionRequest.forSearch(
				SearchCriteria.all(), null), List.of(
				listing("demo-1", "500.00", "https://example.test/demo-1")), observedAt);
		assertEquals(1, demoReport.created());

		UUID firstListingId = listings.findBySourceAndExternalId("avito", "avito-1")
				.orElseThrow().getId();
		UUID secondListingId = listings.findBySourceAndExternalId("avito", "avito-2")
				.orElseThrow().getId();
		var avitoMatches = productMatches.findAll().stream()
				.filter(match -> match.getListingId().equals(firstListingId)
						|| match.getListingId().equals(secondListingId))
				.toList();
		assertEquals(2, avitoMatches.size());
		assertEquals(avitoMatches.get(0).getProductId(), avitoMatches.get(1).getProductId());

		assertAvitoAnalysisUsesOnlyPeer(firstListingId, new BigDecimal("20000.00"), 2);
		assertAvitoAnalysisUsesOnlyPeer(secondListingId, new BigDecimal("10000.00"), 1);
	}

	private int ingestAvito(RawListing listing, Instant observedAt) {
		return ingestionPersistence.persist("avito", IngestionRequest.forSearch(
				SearchCriteria.all(), null), List.of(listing), observedAt).created();
	}

	private void assertAvitoAnalysisUsesOnlyPeer(
			UUID listingId, BigDecimal expectedMarketPrice, int expectedScoreCount) {
		var scores = dealScores.findAllByOrderByComputedAtDescIdDesc().stream()
				.filter(score -> score.getListingId().equals(listingId))
				.toList();
		assertTrue(scores.size() >= expectedScoreCount,
				"Each later peer should create a new persisted analysis for existing listings");
		var peerBasedScore = scores.stream()
				.filter(score -> marketPrices.findById(score.getMarketPriceId())
						.map(marketPrice -> marketPrice.getSampleSize() == 1
								&& marketPrice.getMarketPrice().compareTo(expectedMarketPrice) == 0)
						.orElse(false))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Expected market valuation based on one same-source peer"));
		var peerBasedMarket = marketPrices.findById(peerBasedScore.getMarketPriceId()).orElseThrow();
		assertEquals(1, peerBasedMarket.getSampleSize());
		assertEquals(0, peerBasedMarket.getMarketPrice().compareTo(expectedMarketPrice));
	}

	private static RawListing listing(String externalId, String price, String url) {
		return new RawListing(externalId, "iPhone 16 Pro 128 GB", "Хорошее состояние",
				new BigDecimal(price), "RUB", "GOOD", "Moscow", url, null, null);
	}
}
