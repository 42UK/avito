package ru.avitodeals.scoring.application;

import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import ru.avitodeals.listing.application.ListingCatalog;
import ru.avitodeals.listing.application.ListingNotificationDetails;
import ru.avitodeals.listing.domain.ListingCondition;
import ru.avitodeals.pricing.application.MarketPriceResult;
import ru.avitodeals.pricing.application.PersistedMarketPrice;
import ru.avitodeals.pricing.domain.ConfidenceLevel;
import ru.avitodeals.pricing.domain.ConditionGroup;
import ru.avitodeals.product.application.DeterministicAttributeExtractor;
import ru.avitodeals.product.application.ExtractedAttributes;
import ru.avitodeals.product.application.ExtractedCondition;
import ru.avitodeals.product.application.ProductMatchResult;
import ru.avitodeals.product.domain.MatchStatus;
import ru.avitodeals.scoring.domain.DealScore;
import ru.avitodeals.scoring.infrastructure.persistence.DealScoreRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DealScoringServiceTests {

	private static final Instant COMPUTED_AT = Instant.parse("2026-10-08T10:00:00Z");
	private static final UUID LISTING_ID = id(1);
	private static final UUID PRODUCT_ID = id(2);
	private static final UUID SNAPSHOT_ID = id(3);
	private static final DealScoringSettings SETTINGS = new DealScoringSettings(
			bd("20"), bd("40"), bd("0.90"), bd("60"), bd("25"));

	private final DealScoreRepository repository = mock(DealScoreRepository.class);
	private final ListingCatalog listingCatalog = mock(ListingCatalog.class);
	private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
	private final DealScoringService service =
			new DealScoringService(SETTINGS, repository, listingCatalog, eventPublisher);

	@Test
	void normalTrueDealUsesMarketMedianAndPassesOnlyWithHighConfidence() {
		DealScore score = service.score(input(39_000, 52_000, ConfidenceLevel.HIGH, "1", 0.98),
				COMPUTED_AT);

		assertEquals(bd("25.00"), score.getDiscountPercent());
		assertEquals(bd("50.00"), score.getDealScore());
		assertEquals(bd("0.00"), score.getRiskScore());
		assertEquals(bd("0.9800"), score.getConfidence());
		assertTrue(score.isDealAllowed());
		assertTrue(score.getFlags().contains("DEAL"));
		assertEquals(SNAPSHOT_ID, score.getMarketPriceId());
		assertEquals(COMPUTED_AT, score.getComputedAt());
		assertStructuredExplanations(score);
	}

	@Test
	void normalPriceDoesNotBecomeADealWithoutDiscount() {
		DealScore score = service.score(input(52_000, 52_000, ConfidenceLevel.HIGH, "1", 0.99),
				COMPUTED_AT);

		assertEquals(bd("0.00"), score.getDiscountPercent());
		assertEquals(bd("0.00"), score.getDealScore());
		assertFalse(score.isDealAllowed());
		assertTrue(score.getFlags().contains("NO_DEAL"));
		assertFalse(score.getFlags().contains("BLOCKED"));
	}

	@Test
	void robustPriceOutlierCaseStillScoresAgainstThePersistedMedian() {
		DealScore score = service.score(input(35_000, 51_500, ConfidenceLevel.HIGH, "1", 0.99),
				COMPUTED_AT);

		assertEquals(bd("32.04"), score.getDiscountPercent());
		assertEquals(bd("64.08"), score.getDealScore());
		assertTrue(score.isDealAllowed());
		assertEquals(SNAPSHOT_ID, score.getMarketPriceId());
	}

	@Test
	void defectiveDescriptionAndPartsFlagBlockAndCapDealScore() {
		ExtractedAttributes descriptionRisk = new DeterministicAttributeExtractor()
				.extract("iPhone 12 64 ГБ", "Не включается, продаю целиком на запчасти",
						ExtractedCondition.UNKNOWN);
		DealScore score = service.score(input(10_000, 30_000, ConfidenceLevel.HIGH, "1", 0.99,
				ListingCondition.UNKNOWN, ConditionGroup.UNKNOWN, descriptionRisk));

		assertFalse(score.isDealAllowed());
		assertTrue(score.getFlags().containsAll(List.of("DEFECTIVE", "FOR_PARTS", "BLOCKED")));
		assertTrue(score.getDealScore().compareTo(SETTINGS.maximumRejectedDealScore()) <= 0);
		assertEquals(bd("100.00"), score.getRiskScore());
		assertStructuredExplanations(score);
	}

	@Test
	void declaredDefectiveStateNeverReceivesHighDealScore() {
		ExtractedAttributes noTextRisk = attributes(ExtractedCondition.DEFECTIVE, false, true, Set.of());
		DealScore score = service.score(input(10_000, 30_000, ConfidenceLevel.HIGH, "1", 0.99,
				ListingCondition.DEFECTIVE, ConditionGroup.DEFECTIVE, noTextRisk));

		assertFalse(score.isDealAllowed());
		assertTrue(score.getFlags().contains("DEFECTIVE"));
		assertTrue(score.getDealScore().compareTo(bd("25")) <= 0);
	}

	@Test
	void lowConfidenceBlocksEvenAnOtherwiseLargeDiscount() {
		DealScore score = service.score(input(10_000, 30_000, ConfidenceLevel.LOW, "0.6", 0.99),
				COMPUTED_AT);

		assertFalse(score.isDealAllowed());
		assertTrue(score.getFlags().contains("LOW_CONFIDENCE"));
		assertTrue(score.getFlags().contains("BLOCKED"));
		assertTrue(score.getDealScore().compareTo(bd("25")) <= 0);
	}

	@Test
	void zeroNumericPriceConfidenceCannotPassAnInconsistentHighLabel() {
		DealScore score = service.score(input(10_000, 30_000, ConfidenceLevel.HIGH, "0", 0.99),
				COMPUTED_AT);

		assertFalse(score.isDealAllowed());
		assertTrue(score.getFlags().contains("PRICE_CONFIDENCE_UNAVAILABLE"));
		assertTrue(score.getFlags().contains("BLOCKED"));
	}

	@Test
	void mediumConfidenceIsReviewAndCannotReachDealScoreThreshold() {
		DealScore score = service.score(input(20_000, 30_000, ConfidenceLevel.MEDIUM, "0.8", 0.99),
				COMPUTED_AT);

		assertFalse(score.isDealAllowed());
		assertTrue(score.getFlags().contains("MEDIUM_CONFIDENCE"));
		assertTrue(score.getFlags().contains("REVIEW"));
		assertTrue(score.getDealScore().compareTo(SETTINGS.minimumDealScore()) < 0);
		assertEquals(bd("35.00"), score.getRiskScore());
	}

	@Test
	void wrongCanonicalProductAndMissingMatchAreBlocked() {
		ProductMatchResult wrongProduct = new ProductMatchResult(
				LISTING_ID, id(99), bd("0.99"), MatchStatus.MATCHED, "test-v1");
		DealScore wrong = service.score(input(10_000, 30_000, ConfidenceLevel.HIGH, "1",
				wrongProduct, attributes()));

		assertFalse(wrong.isDealAllowed());
		assertTrue(wrong.getFlags().contains("PRODUCT_MISMATCH"));
		assertTrue(wrong.getFlags().contains("BLOCKED"));

		ProductMatchResult noCanonicalProduct = new ProductMatchResult(
				LISTING_ID, null, bd("0.99"), MatchStatus.MATCHED, "test-v1");
		DealScore missing = service.score(input(10_000, 30_000, ConfidenceLevel.HIGH, "1",
				noCanonicalProduct, attributes()));
		assertFalse(missing.isDealAllowed());
		assertTrue(missing.getFlags().contains("PRODUCT_MISMATCH"));
	}

	@Test
	void unmatchedProductIsBlockedWhileAmbiguousMatchRequiresReview() {
		ProductMatchResult unmatched = new ProductMatchResult(
				LISTING_ID, null, bd("0"), MatchStatus.UNMATCHED, "test-v1");

		DealScore score = service.score(input(9_000, 30_000, ConfidenceLevel.HIGH, "1",
				unmatched, attributes()));

		assertFalse(score.isDealAllowed());
		assertTrue(score.getFlags().contains("BLOCKED"));
		assertFalse(score.getFlags().contains("REVIEW"));
	}

	@Test
	void ambiguousMatchRequiresReviewInsteadOfGuessing() {
		ProductMatchResult ambiguous = new ProductMatchResult(
				LISTING_ID, null, bd("0.50"), MatchStatus.AMBIGUOUS, "test-v1");
		DealScore score = service.score(input(9_000, 30_000, ConfidenceLevel.LOW, "0",
				ambiguous, attributes()));

		assertFalse(score.isDealAllowed());
		assertTrue(score.getFlags().contains("REVIEW"));
		assertFalse(score.getFlags().contains("DEAL"));
	}

	@Test
	void lowMatchConfidenceRequiresReviewEvenWhenStatusIsMatched() {
		DealScore score = service.score(input(20_000, 30_000, ConfidenceLevel.HIGH, "1", 0.85));

		assertFalse(score.isDealAllowed());
		assertTrue(score.getFlags().contains("LOW_MATCH_CONFIDENCE"));
		assertTrue(score.getFlags().contains("REVIEW"));
	}

	@Test
	void accessoryAndFunctionalDefectFlagsAlwaysBlock() {
		ExtractedAttributes accessory = attributes(ExtractedCondition.NEW, true, false, Set.of());
		DealScore accessoryScore = service.score(input(500, 30_000, ConfidenceLevel.HIGH, "1", 0.99,
				ListingCondition.NEW, ConditionGroup.NEW, accessory));
		assertFalse(accessoryScore.isDealAllowed());
		assertTrue(accessoryScore.getFlags().contains("ACCESSORY"));

		ExtractedAttributes functionalDefect = attributes(
				ExtractedCondition.GOOD, false, false, Set.of("WATER_DAMAGE"));
		DealScore defectScore = service.score(input(500, 30_000, ConfidenceLevel.HIGH, "1", 0.99,
				ListingCondition.GOOD, ConditionGroup.GOOD, functionalDefect));
		assertFalse(defectScore.isDealAllowed());
		assertTrue(defectScore.getFlags().contains("DEFECTIVE"));
		assertTrue(defectScore.getDealScore().compareTo(bd("25")) <= 0);
	}

	@Test
	void currencyAndConditionMustExactlyMatchTheMarketSnapshot() {
		DealScore wrongCurrency = service.score(input(10_000, 30_000, ConfidenceLevel.HIGH, "1", 0.99,
				ListingCondition.GOOD, ConditionGroup.GOOD, attributes(), "USD"));
		assertFalse(wrongCurrency.isDealAllowed());
		assertTrue(wrongCurrency.getFlags().contains("CURRENCY_MISMATCH"));

		DealScore wrongCondition = service.score(input(10_000, 30_000, ConfidenceLevel.HIGH, "1", 0.99,
				ListingCondition.FAIR, ConditionGroup.GOOD, attributes(), "RUB"));
		assertFalse(wrongCondition.isDealAllowed());
		assertTrue(wrongCondition.getFlags().contains("CONDITION_MISMATCH"));
	}

	@Test
	void excessiveDiscountIsRiskyAndSentToReview() {
		DealScore score = service.score(input(10_000, 100_000, ConfidenceLevel.HIGH, "1", 0.99));

		assertFalse(score.isDealAllowed());
		assertTrue(score.getFlags().contains("SUSPICIOUSLY_LOW_PRICE"));
		assertTrue(score.getFlags().contains("REVIEW"));
		assertTrue(score.getDealScore().compareTo(bd("25")) <= 0);
		assertEquals(bd("75.00"), score.getRiskScore());
	}

	@Test
	void nonPositiveMarketReferenceBlocksWithoutInventingDiscount() {
		DealScore score = service.score(input(100, 0, ConfidenceLevel.LOW, "0", 0.99));

		assertFalse(score.isDealAllowed());
		assertEquals(bd("0.00"), score.getDiscountPercent());
		assertTrue(score.getFlags().containsAll(List.of("MARKET_PRICE_UNAVAILABLE", "BLOCKED")));
		assertTrue(explanationByCode(score, "MARKET_PRICE_UNAVAILABLE").containsKey("parameters"));
	}

	@Test
	void negativeDiscountFitsDatabasePrecisionForValidListingPrices() {
		DealScore score = service.score(input(1_000_000, 1, ConfidenceLevel.HIGH, "1", 0.99),
				COMPUTED_AT);

		assertEquals(bd("-99999900.00"), score.getDiscountPercent());
		assertFalse(score.isDealAllowed());
	}

	@Test
	void persistedScoreIsSavedWithTheMarketSnapshotId() {
		DealScoringInput input = input(39_000, 52_000, ConfidenceLevel.HIGH, "1", 0.98);
		when(repository.save(any(DealScore.class))).thenAnswer(invocation -> {
			DealScore score = invocation.getArgument(0);
			score.setId(id(4));
			return score;
		});
		when(listingCatalog.findNotificationDetails(LISTING_ID))
				.thenReturn(Optional.of(new ListingNotificationDetails(LISTING_ID, "Pixel 8", "https://example.test/1")));

		DealScore persisted = service.scoreAndSave(input, COMPUTED_AT);

		verify(repository).save(persisted);
		verify(eventPublisher).publishEvent(any(DealScored.class));
		assertEquals(SNAPSHOT_ID, persisted.getMarketPriceId());
		assertTrue(persisted.isDealAllowed());
	}

	@Test
	void inputRejectsMatchResultsBelongingToAnotherListingAndInvalidSettings() {
		ProductMatchResult otherListing = new ProductMatchResult(
				id(100), PRODUCT_ID, bd("0.99"), MatchStatus.MATCHED, "test-v1");
		assertThrows(IllegalArgumentException.class, () -> input(1, 52_000, ConfidenceLevel.HIGH,
				"1", otherListing, attributes()));
		assertThrows(IllegalArgumentException.class,
				() -> new DealScoringSettings(bd("20"), bd("25"), bd("0.9"), bd("60"), bd("25")));
	}

	private static void assertStructuredExplanations(DealScore score) {
		assertTrue(score.getExplanation().size() >= 7);
		for (Map<String, Object> reason : score.getExplanation()) {
			assertTrue(reason.get("code") instanceof String);
			assertTrue(reason.get("parameters") instanceof Map);
			assertTrue(reason.get("text") instanceof String);
			assertFalse(((String) reason.get("text")).isBlank());
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> parameters = (Map<String, Object>)
				explanationByCode(score, "MARKET_PRICE").get("parameters");
		assertEquals(SNAPSHOT_ID.toString(), parameters.get("marketPriceId"));
	}

	private static Map<String, Object> explanationByCode(DealScore score, String code) {
		return score.getExplanation().stream()
				.filter(reason -> code.equals(reason.get("code")))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing explanation code " + code));
	}

	private static DealScoringInput input(
			long candidatePrice, long marketPrice, ConfidenceLevel confidence, String confidenceScore, double matchConfidence) {
		return create(candidatePrice, marketPrice, confidence, confidenceScore,
				new ProductMatchResult(LISTING_ID, PRODUCT_ID, BigDecimal.valueOf(matchConfidence),
						MatchStatus.MATCHED, "test-v1"), attributes(), ListingCondition.GOOD,
				ConditionGroup.GOOD, "RUB");
	}

	private static DealScoringInput input(
			long candidatePrice, long marketPrice, ConfidenceLevel confidence, String confidenceScore,
			ProductMatchResult productMatch, ExtractedAttributes attributes) {
		return create(candidatePrice, marketPrice, confidence, confidenceScore, productMatch,
				attributes, ListingCondition.GOOD, ConditionGroup.GOOD, "RUB");
	}

	private static DealScoringInput input(
			long candidatePrice, long marketPrice, ConfidenceLevel confidence, String confidenceScore,
			ListingCondition candidateCondition, ConditionGroup marketCondition, ExtractedAttributes attributes) {
		return create(candidatePrice, marketPrice, confidence, confidenceScore,
				new ProductMatchResult(LISTING_ID, PRODUCT_ID, bd("0.99"), MatchStatus.MATCHED, "test-v1"),
				attributes, candidateCondition, marketCondition, "RUB");
	}

	private static DealScoringInput input(
			long candidatePrice, long marketPrice, ConfidenceLevel confidence, String confidenceScore,
			ListingCondition candidateCondition, ConditionGroup marketCondition, ExtractedAttributes attributes,
			String currency) {
		return create(candidatePrice, marketPrice, confidence, confidenceScore,
				new ProductMatchResult(LISTING_ID, PRODUCT_ID, bd("0.99"), MatchStatus.MATCHED, "test-v1"),
				attributes, candidateCondition, marketCondition, currency);
	}

	private static DealScoringInput input(
			long candidatePrice, long marketPrice, ConfidenceLevel confidence, String confidenceScore,
			double matchConfidence, ListingCondition candidateCondition, ConditionGroup marketCondition,
			ExtractedAttributes attributes) {
		return create(candidatePrice, marketPrice, confidence, confidenceScore,
				new ProductMatchResult(LISTING_ID, PRODUCT_ID, BigDecimal.valueOf(matchConfidence),
						MatchStatus.MATCHED, "test-v1"), attributes, candidateCondition, marketCondition, "RUB");
	}

	private static DealScoringInput input(
			long candidatePrice, long marketPrice, ConfidenceLevel confidence, String confidenceScore,
			double matchConfidence, ListingCondition candidateCondition, ConditionGroup marketCondition,
			ExtractedAttributes attributes, String currency) {
		return create(candidatePrice, marketPrice, confidence, confidenceScore,
				new ProductMatchResult(LISTING_ID, PRODUCT_ID, BigDecimal.valueOf(matchConfidence),
						MatchStatus.MATCHED, "test-v1"), attributes, candidateCondition, marketCondition, currency);
	}

	private static DealScoringInput create(
			long candidatePrice, long marketPrice, ConfidenceLevel confidence, String confidenceScore,
			ProductMatchResult productMatch, ExtractedAttributes attributes,
			ListingCondition candidateCondition, ConditionGroup marketCondition, String currency) {
		PersistedMarketPrice persistedMarketPrice = new PersistedMarketPrice(
				SNAPSHOT_ID, marketCondition, marketResult(marketPrice, confidence, confidenceScore));
		return new DealScoringInput(LISTING_ID, BigDecimal.valueOf(candidatePrice), currency,
				candidateCondition.name(), persistedMarketPrice, productMatch, attributes);
	}

	private static DealScoringInput input(
			long candidatePrice, long marketPrice, ConfidenceLevel confidence, String confidenceScore) {
		return input(candidatePrice, marketPrice, confidence, confidenceScore, 0.99);
	}

	private static MarketPriceResult marketResult(
			long marketPrice, ConfidenceLevel level, String confidenceScore) {
		BigDecimal value = BigDecimal.valueOf(marketPrice);
		return new MarketPriceResult(PRODUCT_ID, value, "RUB", value, value, value, 5,
				bd(confidenceScore), level, List.of(), 0, "test-median", COMPUTED_AT);
	}

	private static ExtractedAttributes attributes() {
		return attributes(ExtractedCondition.GOOD, false, false, Set.of());
	}

	private static ExtractedAttributes attributes(
			ExtractedCondition condition, boolean accessory, boolean defective, Set<String> defects) {
		return new ExtractedAttributes(Map.of("condition", condition.name()), condition,
				accessory, false, defective, defects, Set.of());
	}

	private static BigDecimal bd(String value) {
		return new BigDecimal(value);
	}

	private static UUID id(int value) {
		return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", value));
	}
}
