package ru.avitodeals.product.application;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public final class DeterministicAttributeExtractor implements AttributeExtractor {

	private static final Pattern MEMORY_GB = pattern(
			"(?<!\\d)(\\d{2,4})\\s*(?:gb|гб|гигабайт(?:а|ов)?)(?!\\p{L})");
	private static final Pattern NAMED_MEMORY_GB = pattern(
			"(?:память|memory|storage)\\s*(?:на\\s*)?(\\d{2,4})\\b");
	private static final Pattern ACCESSORY = pattern(
			"\\b(?:чехол|case|cover|защитн(?:ое|ая|ый) стекло|screen protector|"
					+ "зарядн(?:ое|ый) устройство|charger|кабель|cable|"
					+ "чехол-книжка|аксессуар(?:ы)?)\\b");
	private static final Pattern ACCESSORY_AT_START = pattern(
			"^\\s*(?:чехол|case|cover|защитн(?:ое|ая|ый) стекло|screen protector|"
					+ "зарядн(?:ое|ый) устройство|charger|кабель|cable|"
					+ "аксессуар(?:ы)?)\\b");
	private static final Pattern ACCESSORY_FOR_PRODUCT = pattern(
			"\\b(?:чехол|case|cover|защитн\\w* стекло|screen protector|charger|кабель|cable)"
					+ "\\s+(?:для|на|for)\\b");
	private static final Pattern ACCESSORY_INCLUDED = pattern(
			"\\b(?:with|included|в\\s+комплекте|вместе\\s+с|"
					+ "с\\s+(?:чехлом|зарядк\\w*|кабелем))\\b");
	private static final Pattern FOR_PARTS = pattern(
			"\\b(?:на\\s+запчасти|на\\s+разбор|под\\s+разбор|for\\s+parts|parts\\s+only)\\b");
	private static final Pattern DEFECTIVE = pattern(
			"\\b(?:не\\s+(?:работает|рабочий|рабочая|включается|заряжается|исправен|исправна)"
					+ "|неисправн\\w*|нерабоч\\w*|(?<!не )слом\\w*|под\\s+ремонт|"
					+ "defective|(?<!not )broken|not\\s+(?:fully\\s+)?working|"
					+ "does\\s+not\\s+turn\\s+on)\\b");
	private static final Pattern WATER_DAMAGE = pattern(
			"\\b(?:залит\\w*|после\\s+вод\\w*|попала\\s+вод\\w*|water\\s+damage|"
					+ "water\\s+damaged|liquid\\s+damage)\\b");
	private static final Pattern SCREEN_DAMAGE = pattern(
			"\\b(?:разбит\\w*\\s+(?:экран|дисплей)|треснут\\w*\\s+(?:экран|дисплей)|"
					+ "экран\\s+разбит\\w*|дисплей\\s+разбит\\w*|cracked\\s+screen|"
					+ "broken\\s+screen)\\b");
	private static final Pattern BATTERY_ISSUE = pattern(
			"\\b(?:не\\s+держит\\s+заряд|быстро\\s+разряжа\\w*|аккумулятор\\s+изношен\\w*|"
					+ "battery\\s+(?:issue|problem|health\\s+is\\s+poor))\\b");
	private static final Pattern COSMETIC_WEAR = pattern(
			"\\b(?:потерт\\w*|царапин\\w*|скол\\w*|следы\\s+использования|"
					+ "scratches|scuffs|wear\\s+and\\s+tear|fair\\s+condition)\\b");
	private static final Pattern NEW_CONDITION = pattern(
			"\\b(?:нов(?:ый|ая|ое|ые)|запечатан\\w*|new|sealed|brand\\s+new)\\b");
	private static final Pattern GOOD_CONDITION = pattern(
			"\\b(?:исправ\\w*|отличн\\w*\\s+состояни\\w*|хорош\\w*\\s+состояни\\w*|"
					+ "fully\\s+working|good\\s+condition|excellent\\s+condition)\\b");
	private static final Set<Integer> STORAGE_SIZES = Set.of(16, 32, 64, 128, 256, 512, 1024);

	@Override
	public ExtractedAttributes extract(String title, String description) {
		return extract(title, description, ExtractedCondition.UNKNOWN);
	}

	@Override
	public ExtractedAttributes extract(String title, String description, ExtractedCondition declaredCondition) {
		String normalizedTitle = normalizeText(title == null ? "" : title);
		String text = normalizeText(join(title, description));
		Map<String, String> values = new java.util.LinkedHashMap<>();
		Set<String> unresolved = new LinkedHashSet<>();
		Set<String> defects = new LinkedHashSet<>();

		Set<Integer> storageSizes = findStorageSizes(text);
		if (storageSizes.size() == 1) {
			values.put("storage_gb", storageSizes.iterator().next().toString());
		} else if (storageSizes.size() > 1) {
			unresolved.add("storage_gb");
		}

		boolean accessoryInTitle = ACCESSORY.matcher(normalizedTitle).find();
		boolean accessory = ACCESSORY_AT_START.matcher(normalizedTitle).find()
				|| ACCESSORY_FOR_PRODUCT.matcher(normalizedTitle).find()
				|| (accessoryInTitle && !ACCESSORY_INCLUDED.matcher(normalizedTitle).find())
				|| (normalizedTitle.isBlank() && ACCESSORY.matcher(text).find());
		boolean forParts = FOR_PARTS.matcher(text).find();
		boolean waterDamage = WATER_DAMAGE.matcher(text).find();
		boolean screenDamage = SCREEN_DAMAGE.matcher(text).find();
		boolean batteryIssue = BATTERY_ISSUE.matcher(text).find();
		boolean generalDefect = DEFECTIVE.matcher(text).find();
		boolean defective = forParts || waterDamage || screenDamage || batteryIssue || generalDefect
				|| declaredCondition == ExtractedCondition.DEFECTIVE;
		if (waterDamage) {
			defects.add("WATER_DAMAGE");
		}
		if (screenDamage) {
			defects.add("SCREEN_DAMAGE");
		}
		if (batteryIssue) {
			defects.add("BATTERY_ISSUE");
		}
		if (generalDefect) {
			defects.add("FUNCTIONAL_DEFECT");
		}
		if (forParts) {
			values.put("for_parts", "true");
		}
		if (accessory) {
			values.put("item_type", "accessory");
		}

		ExtractedCondition condition = resolveCondition(
				text, declaredCondition == null ? ExtractedCondition.UNKNOWN : declaredCondition, defective);
		values.put("condition", condition.name());
		if (defective) {
			values.put("defective", "true");
		}

		return new ExtractedAttributes(values, condition, accessory, forParts, defective, defects, unresolved);
	}

	private static ExtractedCondition resolveCondition(
			String text, ExtractedCondition declaredCondition, boolean defective) {
		if (defective || declaredCondition == ExtractedCondition.DEFECTIVE) {
			return ExtractedCondition.DEFECTIVE;
		}
		if (declaredCondition != ExtractedCondition.UNKNOWN) {
			return declaredCondition;
		}
		if (NEW_CONDITION.matcher(text).find()) {
			return ExtractedCondition.NEW;
		}
		if (COSMETIC_WEAR.matcher(text).find()) {
			return ExtractedCondition.FAIR;
		}
		if (GOOD_CONDITION.matcher(text).find()) {
			return ExtractedCondition.GOOD;
		}
		return ExtractedCondition.UNKNOWN;
	}

	private static Set<Integer> findStorageSizes(String text) {
		Set<Integer> values = new LinkedHashSet<>();
		addStorageMatches(MEMORY_GB.matcher(text), values);
		addStorageMatches(NAMED_MEMORY_GB.matcher(text), values);
		return values;
	}

	private static void addStorageMatches(Matcher matcher, Set<Integer> values) {
		while (matcher.find()) {
			int size = Integer.parseInt(matcher.group(1));
			if (STORAGE_SIZES.contains(size)) {
				values.add(size);
			}
		}
	}

	private static String join(String title, String description) {
		return (title == null ? "" : title) + " " + (description == null ? "" : description);
	}

	static String normalizeText(String text) {
		String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
				.toLowerCase(Locale.ROOT)
				.replace('ё', 'е')
				.replaceAll("[^\\p{L}\\p{N}]+", " ")
				.trim();
		return normalized
				.replaceAll("(?U)\\bайфон\\b", "iphone")
				.replaceAll("(?U)\\bплейстейшн\\b", "playstation")
				.replaceAll("(?U)\\bпс\\s*5\\b", "ps5")
				.replaceAll("(?U)\\bсамсунг\\b", "samsung")
				.replaceAll("(?U)\\bгалакси\\b", "galaxy")
				.replaceAll("(?U)\\bредми\\b", "redmi")
				.replaceAll("(?U)\\bсяоми\\b", "xiaomi")
				.replaceAll("(?U)\\bноут\\b", "note")
				.replaceAll("(?U)\\bпро\\b", "pro")
				.replaceAll("(?U)\\bмакс\\b", "max")
				.replaceAll("(?U)\\bплюс\\b", "plus")
				.replaceAll("(?U)\\bмини\\b", "mini")
				.replaceAll("(?U)\\bлайт\\b", "lite")
				.replaceAll("(?U)\\bультра\\b", "ultra")
				.replaceAll("(?U)\\bнинтендо\\b", "nintendo")
				.replaceAll("(?U)\\bсвитч\\b", "switch")
				.replaceAll("(?U)\\bсони\\b", "sony");
	}

	private static Pattern pattern(String regex) {
		return Pattern.compile(regex, Pattern.UNICODE_CHARACTER_CLASS);
	}
}
