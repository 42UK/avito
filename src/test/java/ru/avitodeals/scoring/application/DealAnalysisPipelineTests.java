package ru.avitodeals.scoring.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.avitodeals.listing.application.ListingQueries;
import ru.avitodeals.listing.application.ListingView;
import ru.avitodeals.listing.application.DataOrigin;
import ru.avitodeals.pricing.application.ComparableListings;
import ru.avitodeals.pricing.application.MarketPriceResult;
import ru.avitodeals.pricing.application.MarketPriceService;
import ru.avitodeals.pricing.application.PersistedMarketPrice;
import ru.avitodeals.pricing.domain.ConditionGroup;
import ru.avitodeals.pricing.domain.ConfidenceLevel;
import ru.avitodeals.product.application.AttributeExtractor;
import ru.avitodeals.product.application.DeterministicAttributeExtractor;
import ru.avitodeals.product.application.ProductMatchResult;
import ru.avitodeals.product.application.ProductMatcher;
import ru.avitodeals.product.application.ProductQueries;
import ru.avitodeals.product.domain.MatchStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DealAnalysisPipelineTests {

	private final ListingQueries listingQueries = mock(ListingQueries.class);
	private final ProductMatcher productMatcher = mock(ProductMatcher.class);
	private final ProductQueries productQueries = mock(ProductQueries.class);
	private final AttributeExtractor attributeExtractor = new DeterministicAttributeExtractor();
	private final MarketPriceService marketPriceService = mock(MarketPriceService.class);
	private final DealScoringService dealScoringService = mock(DealScoringService.class);
	private final DealAnalysisPipeline pipeline = new DealAnalysisPipeline(listingQueries, productMatcher,
			productQueries, attributeExtractor, marketPriceService, dealScoringService);

	private final UUID productId = UUID.randomUUID();
	private final UUID candidateId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		when(productMatcher.match(candidateId, "iPhone 16 Pro 128 GB", "Хорошее состояние"))
				.thenReturn(matched(candidateId));
		when(productQueries.matchedListings(productId)).thenReturn(List.of(matched(candidateId)));
	}

	@Test
	void calculatesMarketPriceFromActiveSameSourceCurrencyAndConditionMatchesBeforeScoring() {
		ListingView candidate = listing(candidateId, "avito", "GOOD", "RUB", "10000.00", "ACTIVE");
		UUID validComparableId = UUID.randomUUID();
		UUID differentSourceId = UUID.randomUUID();
		UUID differentOriginId = UUID.randomUUID();
		UUID differentConditionId = UUID.randomUUID();
		UUID removedListingId = UUID.randomUUID();
		when(listingQueries.find(candidateId)).thenReturn(Optional.of(candidate));
		when(productQueries.matchedListings(productId)).thenReturn(List.of(
				matched(candidateId), matched(validComparableId), matched(differentSourceId),
				matched(differentOriginId),
				matched(differentConditionId), matched(removedListingId)));
		when(listingQueries.findByIds(any())).thenReturn(List.of(
				candidate,
				listing(validComparableId, "avito", "GOOD", "RUB", "20000.00", "ACTIVE"),
				listing(differentSourceId, "demo", "GOOD", "RUB", "1.00", "ACTIVE"),
				listing(differentOriginId, "avito", DataOrigin.REAL, "GOOD", "RUB", "1.00", "ACTIVE"),
				listing(differentConditionId, "avito", "NEW", "RUB", "1.00", "ACTIVE"),
				listing(removedListingId, "avito", "GOOD", "RUB", "1.00", "REMOVED")));
		PersistedMarketPrice marketPrice = marketPrice();
		when(marketPriceService.calculateAndSaveSnapshot(any())).thenReturn(marketPrice);

		pipeline.analyze(candidateId);

		var requestCaptor = org.mockito.ArgumentCaptor.forClass(ComparableListings.class);
		verify(marketPriceService, org.mockito.Mockito.times(2))
				.calculateAndSaveSnapshot(requestCaptor.capture());
		for (ComparableListings request : requestCaptor.getAllValues()) {
			assertEquals("GOOD", request.condition().name());
			assertEquals(2, request.listings().size());
			assertEquals(List.of(candidateId, validComparableId),
					request.listings().stream().map(comparable -> comparable.id()).toList());
		}
		var scoreCaptor = org.mockito.ArgumentCaptor.forClass(DealScoringInput.class);
		verify(dealScoringService, org.mockito.Mockito.times(2)).scoreAndSave(scoreCaptor.capture());
		assertEquals(List.of(candidateId, validComparableId), scoreCaptor.getAllValues().stream()
				.map(DealScoringInput::listingId).toList());
	}

	@Test
	void doesNotCreateMarketPriceOrScoreForUnmatchedProduct() {
		when(listingQueries.find(candidateId)).thenReturn(Optional.of(
				listing(candidateId, "avito", "GOOD", "RUB", "10000.00", "ACTIVE")));
		when(productMatcher.match(candidateId, "iPhone 16 Pro 128 GB", "Хорошее состояние"))
				.thenReturn(new ProductMatchResult(candidateId, null, BigDecimal.ZERO,
						MatchStatus.UNMATCHED, "test-v1"));

		pipeline.analyze(candidateId);

		verify(marketPriceService, never()).calculateAndSaveSnapshot(any());
		verify(dealScoringService, never()).scoreAndSave(any());
	}

	@Test
	void ignoresInactiveListingsBeforeProductMatching() {
		when(listingQueries.find(candidateId)).thenReturn(Optional.of(
				listing(candidateId, "avito", "GOOD", "RUB", "10000.00", "REMOVED")));

		pipeline.analyze(candidateId);

		verify(productMatcher, never()).match(any(), any(), any());
		verify(marketPriceService, never()).calculateAndSaveSnapshot(any());
	}

	private static ListingView listing(
			UUID id, String source, String condition, String currency, String price, String status) {
		return listing(id, source, DataOrigin.DEMO, condition, currency, price, status);
	}

	private static ListingView listing(
			UUID id,
			String source,
			DataOrigin dataOrigin,
			String condition,
			String currency,
			String price,
			String status) {
		return new ListingView(id, source, id.toString(), "iPhone 16 Pro 128 GB", "Хорошее состояние",
				new BigDecimal(price), currency, condition, "Moscow", "https://example.invalid/" + id,
				status, null, Instant.EPOCH, Instant.EPOCH, dataOrigin);
	}

	private ProductMatchResult matched(UUID listingId) {
		return new ProductMatchResult(listingId, productId,
				new BigDecimal("0.9500"), MatchStatus.MATCHED, "test-v1");
	}

	private PersistedMarketPrice marketPrice() {
		Instant computedAt = Instant.parse("2026-10-15T00:00:00Z");
		return new PersistedMarketPrice(UUID.randomUUID(), ConditionGroup.GOOD,
				new MarketPriceResult(productId, new BigDecimal("20000.00"), "RUB",
						new BigDecimal("20000.00"), new BigDecimal("20000.00"),
						new BigDecimal("20000.00"), 1, new BigDecimal("0.2000"),
						ConfidenceLevel.LOW, List.of(), 0, "test", computedAt));
	}
}
