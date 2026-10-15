package ru.avitodeals.product.application;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public final class DeterministicProductNormalizer implements ProductNormalizer {

	private static final Pattern IPHONE = pattern(
			"\\biphone\\s*(\\d{1,2})\\s*(pro\\s*max|pro|max|plus|mini|e)?\\b");
	private static final Pattern PLAYSTATION = pattern("\\b(?:playstation\\s*5|ps\\s*5|ps5)\\b");
	private static final Pattern GALAXY = pattern(
			"\\b(?:samsung\\s*(?:galaxy\\s*)?|galaxy\\s*)s\\s*(\\d{1,2})\\s*(ultra|plus|fe)?\\b");
	private static final Pattern REDMI_NOTE = pattern(
			"\\b(?:(?:xiaomi\\s+)?redmi\\s+note|xiaomi\\s+note)\\s*(\\d{1,2})"
					+ "\\s*(pro\\s*plus|pro|plus|s)?\\b");
	private static final Pattern SWITCH = pattern(
			"\\b(?:nintendo\\s+)?switch\\s*(oled|lite)?\\b");
	private static final Pattern PLAYSTATION_DISC = pattern(
			"\\b(?:disc|disk|blu\\s*ray|дисковод\\w*|с\\s+диском|диск)\\b");
	private static final Pattern PLAYSTATION_DIGITAL = pattern(
			"\\b(?:digital|цифров\\w*|без\\s+дисковод\\w*)\\b");
	private static final Pattern PRODUCT_WORD = pattern(
			"\\b(?:iphone|playstation|ps\\s*5|ps5|galaxy|samsung|redmi|xiaomi|"
					+ "nintendo|switch)\\b");
	private static final Pattern UNSPECIFIED_VARIANT = pattern(
			"\\b(?:модификац\\w*|variant|model)\\s+(?:не\\s+(?:указан\\w*|уточнен\\w*|известен\\w*)"
					+ "|unknown|not\\s+specified)\\b");

	private final AttributeExtractor attributeExtractor;

	public DeterministicProductNormalizer() {
		this(new DeterministicAttributeExtractor());
	}

	public DeterministicProductNormalizer(AttributeExtractor attributeExtractor) {
		this.attributeExtractor = attributeExtractor;
	}

	@Override
	public ProductNormalization normalize(String title, String description) {
		ExtractedAttributes attributes = attributeExtractor.extract(title, description);
		String text = DeterministicAttributeExtractor.normalizeText(
				(title == null ? "" : title) + " " + (description == null ? "" : description));
		Set<String> unresolved = new LinkedHashSet<>(attributes.unresolvedAttributes());
		if (attributes.accessory()) {
			return new ProductNormalization(null, null, null, "accessory", ProductType.ACCESSORY,
					attributes, unresolved);
		}

		String normalizedTitle = DeterministicAttributeExtractor.normalizeText(title == null ? "" : title);
		ProductIdentity titleIdentity = findIdentity(normalizedTitle);
		ProductIdentity descriptionIdentity = findIdentity(
				DeterministicAttributeExtractor.normalizeText(description == null ? "" : description));
		if (titleIdentity != null && descriptionIdentity != null
				&& !titleIdentity.key().equals(descriptionIdentity.key())) {
			unresolved.add("model_variant");
			return new ProductNormalization(null, null, null, null, ProductType.PRODUCT,
					attributes, unresolved);
		}
		ProductIdentity identity = titleIdentity != null ? titleIdentity : descriptionIdentity;
		if (identity == null) {
			identity = findIdentity(text);
		}
		if (identity == null) {
			return new ProductNormalization(null, null, null, null,
					PRODUCT_WORD.matcher(text).find() ? ProductType.PRODUCT : ProductType.UNKNOWN,
					attributes, unresolved);
		}

		String storage = attributes.values().get("storage_gb");
		if (identity.supportsStorage() && storage == null) {
			unresolved.add("storage_gb");
		}
		boolean variantUnspecified = UNSPECIFIED_VARIANT.matcher(text).find();
		if (variantUnspecified) {
			unresolved.add("model_variant");
		}
		if ((identity.supportsStorage() && storage == null) || variantUnspecified) {
			return new ProductNormalization(null, identity.brand(), identity.model(), identity.category(),
					ProductType.PRODUCT, attributes, unresolved);
		}

		String canonicalKey = identity.key();
		if (storage != null && identity.supportsStorage()) {
			canonicalKey += "_" + storage + "gb";
		}
		return new ProductNormalization(canonicalKey, identity.brand(), identity.model(),
				identity.category(), ProductType.PRODUCT, attributes, unresolved);
	}

	private static ProductIdentity findIdentity(String text) {
		Matcher matcher = IPHONE.matcher(text);
		if (matcher.find()) {
			String number = matcher.group(1);
			String variant = matcher.group(2);
			String variantSlug = variant == null ? "" : variant.replaceAll("\\s+", "_").toLowerCase(Locale.ROOT);
			String model = "iPhone " + number + (variant == null ? "" : " " + variant.replaceAll("\\s+", " "));
			return new ProductIdentity("apple", model, "smartphone",
					"apple_iphone_" + number + (variantSlug.isEmpty() ? "" : "_" + variantSlug), true);
		}

		matcher = PLAYSTATION.matcher(text);
		if (matcher.find()) {
			boolean disc = PLAYSTATION_DISC.matcher(text).find();
			boolean digital = PLAYSTATION_DIGITAL.matcher(text).find();
			String edition = disc == digital ? "" : (disc ? "_disc" : "_digital");
			String model = "PlayStation 5" + (edition.isEmpty() ? "" : disc ? " Disc" : " Digital");
			return new ProductIdentity("sony", model, "game_console", "sony_playstation_5" + edition, false);
		}

		matcher = GALAXY.matcher(text);
		if (matcher.find()) {
			String number = matcher.group(1);
			String variant = matcher.group(2);
			String suffix = variant == null ? "" : "_" + variant.toLowerCase(Locale.ROOT);
			String model = "Galaxy S" + number + (variant == null ? "" : " " + variant.toUpperCase(Locale.ROOT));
			return new ProductIdentity("samsung", model, "smartphone",
					"samsung_galaxy_s" + number + suffix, true);
		}

		matcher = REDMI_NOTE.matcher(text);
		if (matcher.find()) {
			String number = matcher.group(1);
			String variant = matcher.group(2);
			String variantSlug = variant == null ? "" : variant.replaceAll("\\s+", "_").toLowerCase(Locale.ROOT);
			String model = "Redmi Note " + number + (variant == null ? "" : " " + variant.replaceAll("\\s+", " "));
			return new ProductIdentity("xiaomi", model, "smartphone",
					"xiaomi_redmi_note_" + number + (variantSlug.isEmpty() ? "" : "_" + variantSlug), true);
		}

		matcher = SWITCH.matcher(text);
		if (matcher.find() && (pattern("\\bnintendo\\b").matcher(text).find() || matcher.group(1) != null)) {
			String variant = matcher.group(1);
			String suffix = variant == null ? "" : "_" + variant.toLowerCase(Locale.ROOT);
			String model = "Nintendo Switch" + (variant == null ? "" : " " + variant.toUpperCase(Locale.ROOT));
			return new ProductIdentity("nintendo", model, "game_console", "nintendo_switch" + suffix, false);
		}

		String compact = text.replaceAll("[^\\p{L}\\p{N}]", "");
		Matcher headphoneMatcher = pattern("(?:sony)?wh1000xm(\\d+)").matcher(compact);
		if (headphoneMatcher.find()) {
			String version = headphoneMatcher.group(1);
			return new ProductIdentity("sony", "WH-1000XM" + version, "headphones",
					"sony_wh1000xm" + version, false);
		}
		return null;
	}

	private record ProductIdentity(String brand, String model, String category, String key, boolean supportsStorage) {
	}

	private static Pattern pattern(String regex) {
		return Pattern.compile(regex, Pattern.UNICODE_CHARACTER_CLASS);
	}
}
