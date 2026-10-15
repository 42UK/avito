package ru.avitodeals.pricing.application;

import org.junit.jupiter.api.Test;
import ru.avitodeals.pricing.domain.ConfidenceLevel;
import ru.avitodeals.pricing.domain.ConditionGroup;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketPriceCalculatorTests {

	private static final Instant COMPUTED_AT = Instant.parse("2026-10-08T10:00:00Z");
	private static final UUID CANDIDATE_ID = id(900);
	private static final UUID PRODUCT_ID = id(901);

	private final MarketPriceCalculator calculator = new MarketPriceCalculator();

	@Test
	void robustPriceOutlierMatchesGoldenMedianAndInterpolatedQuartiles() {
		List<ComparableListing> listings = new ArrayList<>();
		int[] prices = {49_000, 50_000, 51_000, 52_000, 53_000, 54_000, 180_000};
		for (int index = 0; index < prices.length; index++) {
			listings.add(listing(index + 1, PRODUCT_ID, ConditionGroup.GOOD, "RUB", prices[index], true));
		}
		MarketPriceResult result = calculator.calculate(request(ConditionGroup.GOOD, "RUB", listings), COMPUTED_AT);

		assertMoney("51500.00", result.marketPrice());
		assertMoney("51500.00", result.median());
		assertMoney("50250.00", result.p25());
		assertMoney("52750.00", result.p75());
		assertEquals(6, result.sampleSize());
		assertEquals(List.of(id(7)), result.outliers());
		assertEquals(1, result.outliersCount());
		assertEquals(ConfidenceLevel.HIGH, result.confidenceLevel());
		assertMoney("1.0000", result.confidenceScore());
		assertFalse(result.outliers().contains(CANDIDATE_ID));
	}

	@Test
	void candidateAndListingsWithWrongProductConditionCurrencyOrStatusAreExcluded() {
		List<ComparableListing> listings = new ArrayList<>();
		listings.add(listing(1, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 10_000, true));
		listings.add(listing(2, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 20_000, true));
		listings.add(listing(3, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 30_000, true));
		listings.add(listing(4, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 40_000, true));
		listings.add(listing(5, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 50_000, true));
		listings.add(listing(6, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 999_999, true, CANDIDATE_ID));
		listings.add(listing(7, id(902), ConditionGroup.GOOD, "RUB", 1, true));
		listings.add(listing(8, PRODUCT_ID, ConditionGroup.FAIR, "RUB", 1, true));
		listings.add(listing(9, PRODUCT_ID, ConditionGroup.GOOD, "USD", 1, true));
		listings.add(listing(10, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 1, false));

		MarketPriceResult result = calculator.calculate(request(ConditionGroup.GOOD, "RUB", listings), COMPUTED_AT);

		assertEquals(5, result.sampleSize());
		assertMoney("30000.00", result.marketPrice());
		assertEquals(ConfidenceLevel.HIGH, result.confidenceLevel());
		assertTrue(result.outliers().isEmpty());
	}

	@Test
	void fewerThanFiveUsableNonOutliersAlwaysHasLowConfidence() {
		List<ComparableListing> listings = List.of(
				listing(1, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 27_000, true),
				listing(2, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 28_000, true),
				listing(3, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 29_000, true));

		MarketPriceResult result = calculator.calculate(request(ConditionGroup.GOOD, "RUB", listings), COMPUTED_AT);

		assertEquals(3, result.sampleSize());
		assertEquals(ConfidenceLevel.LOW, result.confidenceLevel());
		assertMoney("0.6000", result.confidenceScore());
		assertMoney("28000.00", result.marketPrice());
	}

	@Test
	void fourUsableNonOutliersHaveMediumConfidence() {
		List<ComparableListing> listings = List.of(
				listing(1, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 10_000, true),
				listing(2, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 11_000, true),
				listing(3, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 12_000, true),
				listing(4, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 13_000, true));

		MarketPriceResult result = calculator.calculate(request(ConditionGroup.GOOD, "RUB", listings), COMPUTED_AT);

		assertEquals(4, result.sampleSize());
		assertEquals(ConfidenceLevel.MEDIUM, result.confidenceLevel());
		assertMoney("0.8000", result.confidenceScore());
	}

	@Test
	void confidenceThresholdsCanBeConfigured() {
		MarketPriceCalculator configured = new MarketPriceCalculator(2, 4);
		List<ComparableListing> listings = List.of(
				listing(1, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 10_000, true),
				listing(2, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 11_000, true));

		MarketPriceResult result = configured.calculate(request(ConditionGroup.GOOD, "RUB", listings), COMPUTED_AT);

		assertEquals(ConfidenceLevel.MEDIUM, result.confidenceLevel());
	}

	@Test
	void fiveNormalComparablesMatchGoldenMedianAndQuartiles() {
		List<ComparableListing> listings = List.of(
				listing(1, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 50_000, true),
				listing(2, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 51_000, true),
				listing(3, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 52_000, true),
				listing(4, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 53_000, true),
				listing(5, PRODUCT_ID, ConditionGroup.GOOD, "RUB", 54_000, true));

		MarketPriceResult result = calculator.calculate(request(ConditionGroup.GOOD, "RUB", listings), COMPUTED_AT);

		assertMoney("52000.00", result.marketPrice());
		assertMoney("51000.00", result.p25());
		assertMoney("53000.00", result.p75());
		assertEquals(ConfidenceLevel.HIGH, result.confidenceLevel());
	}

	@Test
	void noDataProducesZeroValuedLowConfidenceResult() {
		MarketPriceResult result = calculator.calculate(
				request(ConditionGroup.GOOD, "RUB", List.of()), COMPUTED_AT);

		assertMoney("0.00", result.marketPrice());
		assertMoney("0.00", result.median());
		assertMoney("0.00", result.p25());
		assertMoney("0.00", result.p75());
		assertMoney("0.0000", result.confidenceScore());
		assertEquals(0, result.sampleSize());
		assertEquals(ConfidenceLevel.LOW, result.confidenceLevel());
		assertTrue(result.outliers().isEmpty());
	}

	@Test
	void tiesAndCurrencyRoundingAreStable() {
		List<ComparableListing> listings = List.of(
				listing(2, PRODUCT_ID, ConditionGroup.GOOD, "RUB", "10.00", true),
				listing(1, PRODUCT_ID, ConditionGroup.GOOD, "RUB", "10.00", true),
				listing(3, PRODUCT_ID, ConditionGroup.GOOD, "RUB", "11.01", true),
				listing(4, PRODUCT_ID, ConditionGroup.GOOD, "RUB", "12.00", true));

		MarketPriceResult first = calculator.calculate(request(ConditionGroup.GOOD, "RUB", listings), COMPUTED_AT);
		MarketPriceResult reversed = calculator.calculate(request(
				ConditionGroup.GOOD, "RUB", List.of(listings.get(3), listings.get(2), listings.get(1), listings.get(0))),
				COMPUTED_AT);

		assertMoney("10.51", first.marketPrice());
		assertMoney("10.00", first.p25());
		assertMoney("11.26", first.p75());
		assertEquals(first, reversed);
	}

	private static ComparableListings request(
			ConditionGroup condition, String currency, List<ComparableListing> listings) {
		return new ComparableListings(CANDIDATE_ID, PRODUCT_ID, condition, currency, listings);
	}

	private static ComparableListing listing(
			int id, UUID productId, ConditionGroup condition, String currency, long amount, boolean active) {
		return listing(id, productId, condition, currency, Long.toString(amount), active);
	}

	private static ComparableListing listing(
			int id, UUID productId, ConditionGroup condition, String currency, String amount, boolean active) {
		return listing(id, productId, condition, currency, amount, active, id(id));
	}

	private static ComparableListing listing(
			int ignored, UUID productId, ConditionGroup condition, String currency,
			long amount, boolean active, UUID listingId) {
		return listing(ignored, productId, condition, currency, Long.toString(amount), active, listingId);
	}

	private static ComparableListing listing(
			int ignored, UUID productId, ConditionGroup condition, String currency,
			String amount, boolean active, UUID listingId) {
		return new ComparableListing(listingId, productId, condition, currency, new BigDecimal(amount), active);
	}

	private static UUID id(int value) {
		return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", value));
	}

	private static void assertMoney(String expected, BigDecimal actual) {
		assertEquals(0, new BigDecimal(expected).compareTo(actual));
	}
}
