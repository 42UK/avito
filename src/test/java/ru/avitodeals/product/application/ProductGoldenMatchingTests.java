package ru.avitodeals.product.application;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import ru.avitodeals.product.domain.MatchStatus;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductGoldenMatchingTests {

	private final ProductNormalizer normalizer = new DeterministicProductNormalizer();
	private final ProductClassifier classifier = new DeterministicProductClassifier();

	@ParameterizedTest(name = "{0}")
	@MethodSource("goldenCases")
	void classifiesAllTenQaGoldenCandidatesConservatively(
			String caseId, String title, String description, MatchStatus expectedStatus, String expectedProductKey) {
		ProductNormalization normalized = normalizer.normalize(title, description);
		ProductClassification classification = classifier.classify(normalized);

		assertEquals(expectedStatus, classification.status(), caseId);
		assertEquals(expectedProductKey, normalized.canonicalKey(), caseId);
		if (expectedStatus != MatchStatus.MATCHED) {
			assertNull(normalized.canonicalKey(), caseId);
		}
		if (expectedStatus == MatchStatus.AMBIGUOUS) {
			assertTrue(normalized.unresolvedAttributes().size() > 0, caseId);
		}
	}

	static Stream<org.junit.jupiter.params.provider.Arguments> goldenCases() {
		return Stream.of(
				org.junit.jupiter.params.provider.Arguments.of(
						"normal-true-deal", "Apple iPhone 13 128 ГБ, отличное состояние",
						"Полностью исправен, комплект, без скрытых дефектов",
						MatchStatus.MATCHED, "apple_iphone_13_128gb"),
				org.junit.jupiter.params.provider.Arguments.of(
						"normal-no-discount", "PlayStation 5 с дисководом",
						"Исправна, состояние хорошее", MatchStatus.MATCHED, "sony_playstation_5_disc"),
				org.junit.jupiter.params.provider.Arguments.of(
						"defective-parts-must-not-score", "iPhone 12 64 ГБ на запчасти",
						"Не включается, продаю целиком на запчасти",
						MatchStatus.MATCHED, "apple_iphone_12_64gb"),
				org.junit.jupiter.params.provider.Arguments.of(
						"defect-in-description", "Samsung Galaxy S22 128 ГБ",
						"После залития, не включается. Только под восстановление",
						MatchStatus.MATCHED, "samsung_galaxy_s22_128gb"),
				org.junit.jupiter.params.provider.Arguments.of(
						"wrong-product-model", "Samsung Galaxy S23 128 ГБ",
						"Телефон исправен", MatchStatus.MATCHED, "samsung_galaxy_s23_128gb"),
				org.junit.jupiter.params.provider.Arguments.of(
						"different-memory-variant", "iPhone 13 256 ГБ",
						"Исправный, хорошее состояние", MatchStatus.MATCHED, "apple_iphone_13_256gb"),
				org.junit.jupiter.params.provider.Arguments.of(
						"accessory-not-phone", "Чехол для iPhone 13",
						"Новый силиконовый чехол, телефон не продаётся", MatchStatus.UNMATCHED, null),
				org.junit.jupiter.params.provider.Arguments.of(
						"robust-price-outlier", "Sony WH-1000XM5",
						"Исправные оригинальные наушники", MatchStatus.MATCHED, "sony_wh1000xm5"),
				org.junit.jupiter.params.provider.Arguments.of(
						"insufficient-sample", "Nintendo Switch OLED",
						"Исправна, хорошее состояние", MatchStatus.MATCHED, "nintendo_switch_oled"),
				org.junit.jupiter.params.provider.Arguments.of(
						"ambiguous-variant", "Redmi Note 12, отличное состояние",
						"Память и модификация не указаны; продавец не уточнил",
						MatchStatus.AMBIGUOUS, null));
	}

	@Test
	void englishAndRussianAliasesProduceTheSameExactCanonicalIdentity() {
		ProductNormalization english = normalizer.normalize("iPhone 13 128GB", null);
		ProductNormalization russian = normalizer.normalize("Айфон 13 128 ГБ", null);

		assertEquals(english.canonicalKey(), russian.canonicalKey());
		assertEquals("apple_iphone_13_128gb", english.canonicalKey());
		assertEquals(MatchStatus.MATCHED, classifier.classify(english).status());
		assertEquals(MatchStatus.MATCHED, classifier.classify(russian).status());
	}

	@Test
	void switchOledComparableAliasMatchesTheGoldenTargetProduct() {
		ProductNormalization comparable = normalizer.normalize("Switch OLED", null);

		assertEquals("nintendo_switch_oled", comparable.canonicalKey());
		assertEquals(MatchStatus.MATCHED, classifier.classify(comparable).status());
	}

	@Test
	void unresolvedAttributesPreventExactClassificationEvenIfAKeyWasProvided() {
		ProductNormalization inconsistent = new ProductNormalization(
				"apple_iphone_13_128gb", "apple", "iPhone 13", "smartphone", ProductType.PRODUCT,
				new DeterministicAttributeExtractor().extract("iPhone 13 128GB", null),
				java.util.Set.of("storage_gb"));

		assertEquals(MatchStatus.AMBIGUOUS, classifier.classify(inconsistent).status());
	}

	@Test
	void unrecognizedProductDoesNotProduceAnInventedMatch() {
		ProductNormalization normalization = normalizer.normalize("Неизвестная модель 128GB", null);

		assertEquals(ProductType.UNKNOWN, normalization.type());
		assertNull(normalization.canonicalKey());
		assertEquals(MatchStatus.UNMATCHED, classifier.classify(normalization).status());
	}
}
