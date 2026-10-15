package ru.avitodeals.product.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicProductNormalizerTests {

	private final DeterministicProductNormalizer normalizer = new DeterministicProductNormalizer();

	@Test
	void russianAndEnglishIphoneListingsFromGoldenDatasetShareOneCanonicalKey() {
		List<String> titles = List.of(
				"iPhone 13 128GB",
				"Apple iPhone 13 128 ГБ",
				"Айфон 13 128gb",
				"iPhone 13, память 128 ГБ");

		for (String title : titles) {
			ProductNormalization result = normalizer.normalize(title, "Полностью исправен");
			assertEquals("apple_iphone_13_128gb", result.canonicalKey(), title);
			assertEquals("apple", result.brand());
			assertEquals("iPhone 13", result.model());
			assertEquals("smartphone", result.category());
			assertEquals("128", result.attributes().values().get("storage_gb"));
		}
	}

	@Test
	void iphoneModelVariantsAndStorageRemainDistinct() {
		assertEquals("apple_iphone_15_128gb", key("iPhone 15 128GB"));
		assertEquals("apple_iphone_15_pro_128gb", key("iPhone 15 Pro 128 GB"));
		assertEquals("apple_iphone_15_pro_max_256gb", key("Apple iPhone 15 Pro Max 256 ГБ"));
		assertEquals("apple_iphone_15_256gb", key("iPhone 15 256GB"));
		assertFalse(key("iPhone 15 Pro 128GB").equals(key("iPhone 15 128GB")));
		assertFalse(key("iPhone 15 128GB").equals(key("iPhone 15 256GB")));
	}

	@Test
	void canonicalizesGoldenDatasetProductAliasesAndEditions() {
		assertEquals("sony_playstation_5_disc", key("PlayStation 5 с дисководом"));
		assertEquals("sony_playstation_5_disc", key("PS5 Disc Edition"));
		assertEquals("samsung_galaxy_s22_128gb", key("Samsung Galaxy S22 128 ГБ"));
		assertEquals("samsung_galaxy_s22_128gb", key("Самсунг Галакси S22 128 ГБ"));
		assertEquals("nintendo_switch_oled", key("Nintendo Switch OLED"));
		assertEquals("nintendo_switch_oled", key("Нинтендо Свитч OLED"));
		assertEquals("nintendo_switch_oled", key("Switch OLED"));
		assertEquals("sony_wh1000xm5", key("Sony WH-1000XM5"));
		assertEquals("sony_wh1000xm5", key("WH1000XM5"));
		assertEquals("xiaomi_redmi_note_12_pro_128gb", key("Redmi Note 12 Pro 128 ГБ"));
		assertEquals("xiaomi_redmi_note_12_pro_128gb", key("Сяоми Редми Ноут 12 Про 128 ГБ"));
		assertEquals("sony_playstation_5_disc", key("Плейстейшн 5 с дисководом"));
		assertEquals("sony_wh1000xm5", key("Сони Наушники WH-1000XM5"));
	}

	@Test
	void missingVariantAndMemoryAreNotFilledInFromAssumptions() {
		ProductNormalization result = normalizer.normalize(
				"Redmi Note 12", "Память и модификация не указаны; продавец не уточнил");

		assertNull(result.canonicalKey());
		assertTrue(result.unresolvedAttributes().contains("storage_gb"));
		assertTrue(result.unresolvedAttributes().contains("model_variant"));
		assertFalse("xiaomi_redmi_note_12_pro_128gb".equals(result.canonicalKey()));
	}

	@Test
	void conflictingStorageValuesRemainUnknownInsteadOfSelectingOne() {
		ProductNormalization result = normalizer.normalize(
				"iPhone 13 128GB", "Указано 256 ГБ, точный объём не подтверждён");

		assertNull(result.canonicalKey());
		assertNull(result.attributes().values().get("storage_gb"));
		assertTrue(result.unresolvedAttributes().contains("storage_gb"));
	}

	@Test
	void conflictingModelVariantsAcrossTitleAndDescriptionRemainUnresolved() {
		ProductNormalization result = normalizer.normalize(
				"iPhone 15 128GB", "Продавец уточнил: iPhone 15 Pro 128GB");

		assertNull(result.canonicalKey());
		assertTrue(result.unresolvedAttributes().contains("model_variant"));
	}

	@Test
	void accessoryIsNotNormalizedAsThePhoneItMentions() {
		ProductNormalization result = normalizer.normalize(
				"Чехол для iPhone 13", "Новый силиконовый чехол, телефон не продаётся");

		assertEquals(ProductType.ACCESSORY, result.type());
		assertNull(result.canonicalKey());
		assertTrue(result.attributes().accessory());
		assertEquals("accessory", result.attributes().values().get("item_type"));
	}

	@Test
	void unknownProductDoesNotReceiveAnInventedIdentity() {
		ProductNormalization result = normalizer.normalize("Неизвестная модель 128GB", null);

		assertEquals(ProductType.UNKNOWN, result.type());
		assertNull(result.canonicalKey());
		assertNull(result.brand());
		assertNull(result.model());
	}

	@Test
	void compactAndPunctuatedEnglishModelNamesAreNormalizedDeterministically() {
		assertEquals("sony_wh1000xm5", key("WH 1000 XM5"));
		assertEquals("apple_iphone_15_pro_max_256gb", key("  IPHONE—15  PRO  MAX / 256 GB "));
	}

	private String key(String title) {
		return normalizer.normalize(title, null).canonicalKey();
	}
}
