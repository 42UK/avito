package ru.avitodeals.scoring.application;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import ru.avitodeals.listing.application.ListingPricePoint;
import ru.avitodeals.listing.application.ListingQueries;
import ru.avitodeals.listing.application.ListingView;
import ru.avitodeals.pricing.application.MarketPriceQueries;
import ru.avitodeals.pricing.application.MarketPriceView;
import ru.avitodeals.product.application.ProductQueries;
import ru.avitodeals.scoring.domain.DealScore;
import ru.avitodeals.scoring.infrastructure.persistence.DealScoreRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DealQueryServiceTests {

	private final DealScoreRepository scores = mock(DealScoreRepository.class);
	private final ListingQueries listings = mock(ListingQueries.class);
	private final ProductQueries products = mock(ProductQueries.class);
	private final MarketPriceQueries prices = mock(MarketPriceQueries.class);
	private final DealQueryService service = new DealQueryService(scores, listings, products, prices);

	@Test
	void listsOnlyTheLatestSavedDealForActiveListings() {
		UUID activeId = UUID.randomUUID();
		UUID removedId = UUID.randomUUID();
		Instant recent = Instant.parse("2026-10-08T10:00:00Z");
		DealScore activeDeal = score(activeId, true, recent);
		DealScore supersededDeal = score(activeId, true, recent.minusSeconds(1));
		DealScore removedDeal = score(removedId, true, recent);
		DealScore review = score(UUID.randomUUID(), false, recent);
		when(scores.findAllByOrderByComputedAtDescIdDesc())
				.thenReturn(List.of(activeDeal, review, removedDeal, supersededDeal));
		when(listings.findByIds(org.mockito.ArgumentMatchers.anyCollection()))
				.thenReturn(List.of(listing(activeId, "ACTIVE"), listing(removedId, "REMOVED")));

		Page<DealCard> result = service.list(0, 10);

		assertEquals(1, result.getTotalElements());
		assertEquals(activeDeal.getId(), result.getContent().getFirst().id());
		assertTrue(result.getContent().getFirst().flags().contains("DEAL"));
	}

	@Test
	void dealDetailsExposePersistedExplanationOutliersCurrentComparablesAndPriceHistory() {
		UUID listingId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		UUID marketPriceId = UUID.randomUUID();
		DealScore deal = score(listingId, true, Instant.parse("2026-10-08T10:00:00Z"));
		deal.setMarketPriceId(marketPriceId);
		ListingView candidate = listing(listingId, "ACTIVE");
		ListingView comparable = listing(UUID.randomUUID(), "ACTIVE");
		MarketPriceView snapshot = new MarketPriceView(marketPriceId, productId, "GOOD",
				BigDecimal.valueOf(100), "RUB", BigDecimal.valueOf(100),
				BigDecimal.valueOf(90), BigDecimal.valueOf(110), 5, new BigDecimal("0.9"),
				"HIGH", List.of(comparable.id()), "MEDIAN_MAD", Instant.EPOCH);
		List<ListingPricePoint> history = List.of(new ListingPricePoint(
				BigDecimal.valueOf(80), "RUB", Instant.EPOCH));
		when(scores.findById(deal.getId())).thenReturn(Optional.of(deal));
		when(scores.findFirstByListingIdOrderByComputedAtDescIdDesc(listingId))
				.thenReturn(Optional.of(deal));
		when(listings.find(listingId)).thenReturn(Optional.of(candidate));
		when(prices.find(marketPriceId)).thenReturn(Optional.of(snapshot));
		when(products.matchedListingIds(productId)).thenReturn(List.of(listingId, comparable.id()));
		when(listings.findByIds(org.mockito.ArgumentMatchers.anyCollection()))
				.thenReturn(List.of(candidate, comparable));
		when(listings.priceHistory(listingId)).thenReturn(history);

		DealDetails details = service.find(deal.getId()).orElseThrow();

		assertEquals(deal.getExplanation(), details.explanation());
		assertEquals(snapshot.outlierIds(), details.outlierIds());
		assertEquals(comparable.id(), details.comparables().getFirst().listing().id());
		assertTrue(details.comparables().getFirst().outlier());
		assertEquals(history, details.priceHistory());
		assertEquals("CURRENT_MATCHED_LISTINGS", details.comparablesBasis());
	}

	@Test
	void validatesLatestDealUsingOnlyThatListingsScoreHistory() {
		UUID listingId = UUID.randomUUID();
		Instant computedAt = Instant.parse("2026-10-08T10:00:00Z");
		DealScore requested = score(listingId, true, computedAt);
		requested.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
		DealScore newer = score(listingId, false, computedAt);
		newer.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
		when(scores.findById(requested.getId())).thenReturn(Optional.of(requested));
		when(scores.findFirstByListingIdOrderByComputedAtDescIdDesc(listingId))
				.thenReturn(Optional.of(newer));

		assertTrue(service.find(requested.getId()).isEmpty());
		verify(scores).findFirstByListingIdOrderByComputedAtDescIdDesc(listingId);
		verify(scores, never()).findAllByOrderByComputedAtDescIdDesc();
	}

	private static DealScore score(UUID listingId, boolean deal, Instant computedAt) {
		DealScore score = DealScore.create(listingId, UUID.randomUUID(), BigDecimal.TEN,
				BigDecimal.valueOf(50), BigDecimal.ZERO, BigDecimal.ONE,
				deal ? List.of("DEAL") : List.of("REVIEW"),
				List.of(), computedAt);
		score.setId(UUID.randomUUID());
		return score;
	}

	private static ListingView listing(UUID id, String status) {
		return new ListingView(id, "mock", id.toString(), "item", null,
				BigDecimal.TEN, "RUB", "GOOD", null, "https://example.invalid",
				status, null, Instant.EPOCH, Instant.EPOCH);
	}
}
