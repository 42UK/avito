package ru.avitodeals.pricing.application;

import org.junit.jupiter.api.Test;
import ru.avitodeals.pricing.domain.ConfidenceLevel;
import ru.avitodeals.pricing.domain.ConditionGroup;
import ru.avitodeals.pricing.domain.MarketPrice;
import ru.avitodeals.pricing.infrastructure.persistence.MarketPriceRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketPriceServiceTests {

	@Test
	void calculatesAndPersistsSnapshotUsingExistingRepository() {
		MarketPriceRepository repository = mock(MarketPriceRepository.class);
		MarketPriceCalculator calculator = new MarketPriceCalculator();
		Instant computedAt = Instant.parse("2026-10-08T10:00:00Z");
		when(repository.save(any(MarketPrice.class))).thenAnswer(invocation -> invocation.getArgument(0));
		MarketPriceService service = new MarketPriceService(calculator, repository);
		UUID candidateId = UUID.fromString("00000000-0000-0000-0000-000000000100");
		UUID productId = UUID.fromString("00000000-0000-0000-0000-000000000101");
		List<ComparableListing> listings = List.of(
				comparable(1, productId, 10_000),
				comparable(2, productId, 20_000),
				comparable(3, productId, 30_000),
				comparable(4, productId, 40_000),
				comparable(5, productId, 50_000));

		MarketPriceResult result = service.calculateAndSave(
				new ComparableListings(candidateId, productId, ConditionGroup.GOOD, "RUB", listings), computedAt);

		ArgumentCaptor<MarketPrice> snapshotCaptor = ArgumentCaptor.forClass(MarketPrice.class);
		verify(repository).save(snapshotCaptor.capture());
		assertEquals(productId, result.productId());
		assertEquals(ConfidenceLevel.HIGH, result.confidenceLevel());
		assertEquals(computedAt, result.computedAt());
		assertEquals(productId, snapshotCaptor.getValue().getProductId());
		assertEquals(ConditionGroup.GOOD, snapshotCaptor.getValue().getConditionGroup());
		assertEquals(result.marketPrice(), snapshotCaptor.getValue().getMarketPrice());
		assertEquals(result.outliers().size(), snapshotCaptor.getValue().getOutliersCount());
	}

	@Test
	void exposesGeneratedSnapshotIdAndConditionWithoutRepositoryBoundaryLeakage() {
		MarketPriceRepository repository = mock(MarketPriceRepository.class);
		UUID snapshotId = UUID.fromString("00000000-0000-0000-0000-000000000150");
		when(repository.save(any(MarketPrice.class))).thenAnswer(invocation -> {
			MarketPrice snapshot = invocation.getArgument(0);
			snapshot.setId(snapshotId);
			return snapshot;
		});
		MarketPriceService service = new MarketPriceService(new MarketPriceCalculator(), repository);
		UUID candidateId = UUID.fromString("00000000-0000-0000-0000-000000000100");
		UUID productId = UUID.fromString("00000000-0000-0000-0000-000000000101");
		List<ComparableListing> listings = List.of(
				comparable(1, productId, 10_000),
				comparable(2, productId, 20_000),
				comparable(3, productId, 30_000),
				comparable(4, productId, 40_000),
				comparable(5, productId, 50_000));

		PersistedMarketPrice persisted = service.calculateAndSaveSnapshot(
				new ComparableListings(candidateId, productId, ConditionGroup.GOOD, "RUB", listings),
				Instant.parse("2026-10-08T10:00:00Z"));

		assertEquals(snapshotId, persisted.marketPriceId());
		assertEquals(ConditionGroup.GOOD, persisted.condition());
		assertEquals(productId, persisted.result().productId());
	}

	private static ComparableListing comparable(int id, UUID productId, long amount) {
		UUID listingId = UUID.fromString(String.format("00000000-0000-0000-0000-%012d", id));
		return new ComparableListing(listingId, productId, ConditionGroup.GOOD, "RUB",
				BigDecimal.valueOf(amount), true);
	}
}
