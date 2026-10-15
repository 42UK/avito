package ru.avitodeals.product.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicAttributeExtractorTests {

	private final DeterministicAttributeExtractor extractor = new DeterministicAttributeExtractor();

	@Test
	void detectsPartsAndNonWorkingPhoneFromRussianGoldenDatasetCase() {
		ExtractedAttributes result = extractor.extract(
				"iPhone 12 64 ГБ на запчасти", "Не включается, продаю целиком на запчасти");

		assertEquals(ExtractedCondition.DEFECTIVE, result.condition());
		assertTrue(result.defective());
		assertTrue(result.forParts());
		assertTrue(result.defects().contains("FUNCTIONAL_DEFECT"));
		assertEquals("true", result.values().get("for_parts"));
		assertEquals("64", result.values().get("storage_gb"));
	}

	@Test
	void extractsDefectsFromDescriptionEvenWhenConditionWasNotDeclared() {
		ExtractedAttributes result = extractor.extract(
				"Samsung Galaxy S22 128 ГБ", "После залития, не включается. Только под восстановление");

		assertEquals(ExtractedCondition.DEFECTIVE, result.condition());
		assertTrue(result.defective());
		assertTrue(result.defects().contains("WATER_DAMAGE"));
		assertTrue(result.defects().contains("FUNCTIONAL_DEFECT"));
		assertFalse(result.forParts());
	}

	@Test
	void detectsSpecificScreenAndBatteryDefectsInEnglish() {
		ExtractedAttributes result = extractor.extract(
				"iPhone 15 Pro", "Cracked screen; battery issue, not fully working");

		assertEquals(ExtractedCondition.DEFECTIVE, result.condition());
		assertTrue(result.defects().contains("SCREEN_DAMAGE"));
		assertTrue(result.defects().contains("BATTERY_ISSUE"));
	}

	@Test
	void cosmeticWearIsFairButDoesNotImplyFunctionalFailure() {
		ExtractedAttributes result = extractor.extract("iPhone 13 128GB", "Есть царапины, работает");

		assertEquals(ExtractedCondition.FAIR, result.condition());
		assertFalse(result.defective());
		assertFalse(result.forParts());
	}

	@Test
	void explicitGoodNewAndDeclaredConditionsArePreservedUnlessTextShowsDefect() {
		assertEquals(ExtractedCondition.GOOD,
				extractor.extract("iPhone", "Fully working, good condition").condition());
		assertEquals(ExtractedCondition.NEW,
				extractor.extract("iPhone", "Brand new, sealed").condition());
		assertEquals(ExtractedCondition.GOOD,
				extractor.extract("iPhone", "No details", ExtractedCondition.GOOD).condition());
		ExtractedAttributes contradiction = extractor.extract(
				"iPhone", "Brand new, but does not turn on", ExtractedCondition.NEW);
		assertEquals(ExtractedCondition.DEFECTIVE, contradiction.condition());
		assertTrue(contradiction.defective());
	}

	@Test
	void nonDefectNegationDoesNotCreateAFalseScreenDamageFlag() {
		ExtractedAttributes result = extractor.extract("iPhone", "Экран не разбит, без дефектов");
		ExtractedAttributes englishResult = extractor.extract("iPhone", "Not broken, no defects");

		assertFalse(result.defective());
		assertFalse(result.defects().contains("SCREEN_DAMAGE"));
		assertEquals(ExtractedCondition.UNKNOWN, result.condition());
		assertFalse(englishResult.defective());
	}

	@Test
	void accessoryClassificationAndAmbiguousMemoryAreExplicit() {
		ExtractedAttributes accessory = extractor.extract("Чехол для iPhone 13", "Телефон не продаётся");
		assertTrue(accessory.accessory());
		assertEquals("accessory", accessory.values().get("item_type"));

		ExtractedAttributes phoneWithIncludedCase = extractor.extract(
				"iPhone 13 128GB", "Телефон в отличном состоянии, идёт в комплекте с чехлом");
		assertFalse(phoneWithIncludedCase.accessory());
		assertFalse(extractor.extract("iPhone 13 case included", null).accessory());

		ExtractedAttributes conflicting = extractor.extract(
				"iPhone 13 128GB", "Память 256 ГБ");
		assertFalse(conflicting.values().containsKey("storage_gb"));
		assertTrue(conflicting.unresolvedAttributes().contains("storage_gb"));
	}

	@Test
	void partsOnlyAlsoImpliesDefectiveCondition() {
		ExtractedAttributes result = extractor.extract("Camera for parts only", null);

		assertTrue(result.forParts());
		assertTrue(result.defective());
		assertEquals(ExtractedCondition.DEFECTIVE, result.condition());
	}

	@Test
	void negatedFullyWorkingPhraseIsNotClassifiedAsGood() {
		ExtractedAttributes result = extractor.extract("Phone", "Not fully working");

		assertTrue(result.defective());
		assertEquals(ExtractedCondition.DEFECTIVE, result.condition());
		assertTrue(result.defects().contains("FUNCTIONAL_DEFECT"));
	}
}
