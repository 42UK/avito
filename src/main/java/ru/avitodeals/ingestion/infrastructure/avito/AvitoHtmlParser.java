package ru.avitodeals.ingestion.infrastructure.avito;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AvitoHtmlParser {

	private static final Logger LOGGER = LoggerFactory.getLogger(AvitoHtmlParser.class);
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final Pattern RELATIVE_DATE =
			Pattern.compile("(?iu)^(\\d+)\\s+(минут(?:у|ы)?|час(?:а|ов)?|день|дня|дней|дн(?:я|ей))\\s+назад$");
	private static final Pattern CALENDAR_DATE =
			Pattern.compile("(?iu)^(вчера|сегодня|позавчера|\\d{1,2}\\s+[а-яё]+)(?:\\s+в\\s+(\\d{1,2}:\\d{2}))?$");
	private static final Pattern TIME = Pattern.compile("^(\\d{1,2}):(\\d{2})$");
	private static final Map<String, Integer> MONTHS = Map.ofEntries(
			Map.entry("января", 1), Map.entry("февраля", 2), Map.entry("марта", 3),
			Map.entry("апреля", 4), Map.entry("мая", 5), Map.entry("июня", 6),
			Map.entry("июля", 7), Map.entry("августа", 8), Map.entry("сентября", 9),
			Map.entry("октября", 10), Map.entry("ноября", 11), Map.entry("декабря", 12));

	private final Clock clock;

	public AvitoHtmlParser(Clock clock) {
		this.clock = clock;
	}

	public List<ParsedFeedItem> parseFeed(String html) {
		return parseFeedPage(html).items();
	}

	public ParsedFeed parseFeedPage(String html) {
		Document document = Jsoup.parse(html == null ? "" : html);
		List<ParsedFeedItem> items = new ArrayList<>();
		for (Element card : document.select("[data-marker=\"item\"]")) {
			try {
				items.add(parseFeedItem(card));
			} catch (RuntimeException exception) {
				LOGGER.warn("Unable to parse Avito feed item {}", card.attr("data-item-id"), exception);
			}
		}
		return new ParsedFeed(items, parseCount(document), parsePageNumbers(document));
	}

	public ParsedItemPage parseItemPage(String html) {
		Document document = Jsoup.parse(html == null ? "" : html);
		JsonLdData jsonLd = parseJsonLd(document);
		Map<String, String> attributes = parseAttributes(document);
		String condition = firstNonBlank(
				conditionAttribute(attributes), conditionFromJson(jsonLd.product()));
		Element sellerLabel = document.selectFirst("[data-marker=\"seller-info/label\"]");
		SellerType sellerType = parseSellerType(text(sellerLabel));
		Element dateElement = document.selectFirst("[data-marker=\"item-view/item-date\"]");
		return new ParsedItemPage(
				text(jsonLd.product().path("sku")),
				text(jsonLd.product().path("name")),
				text(jsonLd.product().path("description")),
				parsePrice(text(jsonLd.product().path("offers").path("price"))),
				text(jsonLd.product().path("offers").path("priceCurrency")),
				cleanUrl(text(jsonLd.product().path("offers").path("url"))),
				condition,
				jsonLd.categoryPath(),
				attributes,
				sellerType,
				parseDate(text(dateElement)).orElse(null));
	}

	public Optional<ParsedDate> parseDate(String rawDate) {
		String value = normalize(rawDate);
		if (value.isEmpty()) {
			return Optional.empty();
		}
		try {
			return Optional.of(new ParsedDate(Instant.parse(value), DateAccuracy.EXACT));
		} catch (DateTimeParseException ignored) {
			try {
				return Optional.of(new ParsedDate(
						OffsetDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).toInstant(),
						DateAccuracy.EXACT));
			} catch (DateTimeParseException ignoredOffset) {
				try {
					return Optional.of(new ParsedDate(
							LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME)
									.atZone(clock.getZone()).toInstant(),
							DateAccuracy.EXACT));
				} catch (DateTimeParseException ignoredLocal) {
					return parseRussianDate(value);
				}
			}
		}
	}

	private ParsedFeedItem parseFeedItem(Element card) {
		Element titleElement = card.selectFirst("[data-marker=\"item-title\"]");
		Element urlElement = card.selectFirst("a[itemprop=\"url\"]");
		Element priceElement = card.selectFirst("meta[itemprop=\"price\"]");
		Element currencyElement = card.selectFirst("meta[itemprop=\"priceCurrency\"]");
		Element descriptionElement = card.selectFirst("meta[itemprop=\"description\"]");
		Element locationElement = card.selectFirst("[data-marker=\"item-location\"]");
		Element dateElement = card.selectFirst("[data-marker=\"item-date\"]");
		String url = cleanUrl(attribute(urlElement, "href"));
		return new ParsedFeedItem(
				nonBlank(card.attr("data-item-id")),
				firstNonBlank(text(titleElement), attribute(titleElement, "title")),
				parsePrice(attribute(priceElement, "content")),
				upper(attribute(currencyElement, "content")),
				url,
				regionSlug(url),
				attribute(descriptionElement, "content"),
				parseDate(text(dateElement)).orElse(null));
	}

	private JsonLdData parseJsonLd(Document document) {
		JsonNode product = null;
		List<String> categoryPath = List.of();
		for (Element script : document.select("script[type=\"application/ld+json\"]")) {
			try {
				JsonNode root = JSON.readTree(script.data());
				if (root == null) {
					continue;
				}
				List<JsonNode> nodes = new ArrayList<>();
				collectJsonNodes(root, nodes);
				for (JsonNode node : nodes) {
					if (hasType(node, "Product") && product == null) {
						product = node;
					} else if (hasType(node, "BreadcrumbList") && categoryPath.isEmpty()) {
						categoryPath = parseBreadcrumbs(node);
					}
				}
			} catch (Exception exception) {
				LOGGER.warn("Unable to parse an Avito JSON-LD block", exception);
			}
		}
		return new JsonLdData(product == null ? JSON.createObjectNode() : product, categoryPath);
	}

	private static void collectJsonNodes(JsonNode node, List<JsonNode> result) {
		if (node.isObject()) {
			result.add(node);
			node.elements().forEachRemaining(child -> collectJsonNodes(child, result));
		} else if (node.isArray()) {
			node.elements().forEachRemaining(child -> collectJsonNodes(child, result));
		}
	}

	private static boolean hasType(JsonNode node, String type) {
		JsonNode typeNode = node.path("@type");
		if (typeNode.isArray()) {
			for (JsonNode value : typeNode) {
				if (type.equals(value.asText())) {
					return true;
				}
			}
			return false;
		}
		return type.equals(typeNode.asText());
	}

	private static List<String> parseBreadcrumbs(JsonNode breadcrumbList) {
		List<String> path = new ArrayList<>();
		JsonNode elements = breadcrumbList.path("itemListElement");
		for (JsonNode element : elements) {
			String name = normalize(text(element.path("name")));
			if (!name.isEmpty() && !name.equalsIgnoreCase("Главная")) {
				path.add(name);
			}
		}
		return List.copyOf(path);
	}

	private static Map<String, String> parseAttributes(Document document) {
		Map<String, String> attributes = new LinkedHashMap<>();
		for (Element item : document.select("[data-marker=\"item-view/item-params\"] li")) {
			Element keyElement = item.selectFirst("span");
			if (keyElement == null) {
				continue;
			}
			String key = normalize(text(keyElement)).replaceFirst(":\\s*$", "");
			Element valueElement = item.clone();
			Element clonedKey = valueElement.selectFirst("span");
			if (clonedKey != null) {
				clonedKey.remove();
			}
			String value = normalize(valueElement.text());
			if (!key.isEmpty() && !value.isEmpty()) {
				attributes.put(key, value);
			}
		}
		return Map.copyOf(attributes);
	}

	private Optional<ParsedDate> parseRussianDate(String value) {
		String normalized = value.replaceFirst("^[·•]\\s*", "").trim().toLowerCase(Locale.forLanguageTag("ru"));
		Matcher relative = RELATIVE_DATE.matcher(normalized);
		if (relative.matches()) {
			long amount = Long.parseLong(relative.group(1));
			String unit = relative.group(2);
			Instant now = clock.instant();
			if (unit.startsWith("минут")) {
				return approximate(now.minus(amount, ChronoUnit.MINUTES));
			}
			if (unit.startsWith("час")) {
				return approximate(now.minus(amount, ChronoUnit.HOURS));
			}
			long days = amount;
			return approximate(LocalDate.now(clock).minusDays(days).atStartOfDay(clock.getZone()).toInstant());
		}

		Matcher calendar = CALENDAR_DATE.matcher(normalized);
		if (!calendar.matches()) {
			return Optional.empty();
		}
		LocalDate date = calendarDate(calendar.group(1));
		if (date == null) {
			return Optional.empty();
		}
		String timeText = calendar.group(2);
		if (timeText == null) {
			return approximate(date.atStartOfDay(clock.getZone()).toInstant());
		}
		Matcher time = TIME.matcher(timeText);
		if (!time.matches()) {
			return Optional.empty();
		}
		LocalDateTime dateTime = date.atTime(Integer.parseInt(time.group(1)), Integer.parseInt(time.group(2)));
		return approximate(dateTime.atZone(clock.getZone()).toInstant());
	}

	private LocalDate calendarDate(String dayText) {
		LocalDate today = LocalDate.now(clock);
		if (dayText.equals("вчера")) {
			return today.minusDays(1);
		}
		if (dayText.equals("сегодня")) {
			return today;
		}
		if (dayText.equals("позавчера")) {
			return today.minusDays(2);
		}
		String[] parts = dayText.split("\\s+");
		if (parts.length != 2) {
			return null;
		}
		Integer month = MONTHS.get(parts[1]);
		if (month == null) {
			return null;
		}
		try {
			LocalDate candidate = LocalDate.of(Year.now(clock).getValue(), month, Integer.parseInt(parts[0]));
			if (candidate.isAfter(today)) {
				candidate = candidate.minusYears(1);
			}
			return candidate;
		} catch (NumberFormatException | java.time.DateTimeException exception) {
			return null;
		}
	}

	private static String conditionAttribute(Map<String, String> attributes) {
		return attributes.entrySet().stream()
				.filter(entry -> entry.getKey().equalsIgnoreCase("Состояние"))
				.map(Map.Entry::getValue)
				.findFirst().orElse(null);
	}

	private static String conditionFromJson(JsonNode product) {
		String itemCondition = text(product.path("offers").path("itemCondition"));
		if (itemCondition == null) {
			return null;
		}
		if (itemCondition.endsWith("NewCondition")) {
			return "Новое";
		}
		if (itemCondition.endsWith("UsedCondition")) {
			return "Б/у";
		}
		return null;
	}

	private static SellerType parseSellerType(String label) {
		if (label == null || label.isBlank()) {
			return SellerType.UNKNOWN;
		}
		String normalized = label.toLowerCase(Locale.forLanguageTag("ru"));
		if (normalized.contains("частн")) {
			return SellerType.PRIVATE;
		}
		if (normalized.contains("магаз") || normalized.contains("компан") || normalized.contains("бизнес")) {
			return SellerType.BUSINESS;
		}
		return SellerType.UNKNOWN;
	}

	private static Integer parseCount(Document document) {
		Element count = document.selectFirst("[data-marker=\"page-title/count\"]");
		String countText = text(count);
		String digits = countText == null ? "" : countText.replaceAll("[^0-9]", "");
		if (digits.isEmpty()) {
			return null;
		}
		try {
			return Integer.valueOf(digits);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private static List<Integer> parsePageNumbers(Document document) {
		List<Integer> pages = new ArrayList<>();
		for (Element link : document.select("[data-marker=\"hidden-pagination/list-element/link\"]")) {
			String href = attribute(link, "href");
			if (href == null) {
				continue;
			}
			try {
				String query = URI.create(href).getRawQuery();
				if (query == null) {
					continue;
				}
				for (String parameter : query.split("&")) {
					if (parameter.startsWith("p=")) {
						pages.add(Integer.parseInt(parameter.substring(2)));
						break;
					}
				}
			} catch (IllegalArgumentException exception) {
				LOGGER.debug("Ignoring malformed Avito pagination link");
			}
		}
		return List.copyOf(pages);
	}

	private static BigDecimal parsePrice(String value) {
		String normalized = normalize(value).replaceAll("[\\s\\u00a0\\u202f]", "");
		if (normalized.isEmpty()) {
			return null;
		}
		try {
			return new BigDecimal(normalized);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private static String cleanUrl(String value) {
		String normalized = normalize(value);
		if (normalized.isEmpty()) {
			return null;
		}
		try {
			URI uri = URI.create(normalized);
			if (!uri.isAbsolute()) {
				uri = URI.create("https://www.avito.ru").resolve(uri);
			}
			return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null).toString();
		} catch (Exception exception) {
			int queryIndex = normalized.indexOf('?');
			String path = queryIndex >= 0 ? normalized.substring(0, queryIndex) : normalized;
			int fragmentIndex = path.indexOf('#');
			return fragmentIndex >= 0 ? path.substring(0, fragmentIndex) : path;
		}
	}

	private static String regionSlug(String url) {
		if (url == null) {
			return null;
		}
		try {
			String path = URI.create(url).getPath();
			if (path == null || path.isBlank() || path.equals("/")) {
				return null;
			}
			return path.substring(1).split("/", 2)[0];
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private static String attribute(Element element, String name) {
		return element == null ? null : nonBlank(element.attr(name));
	}

	private static String text(Element element) {
		return element == null ? null : nonBlank(element.text());
	}

	private static String text(JsonNode node) {
		return node == null || node.isMissingNode() || node.isNull() ? null : nonBlank(node.asText());
	}

	private static String nonBlank(String value) {
		String normalized = normalize(value);
		return normalized.isEmpty() ? null : normalized;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.replace('\u00a0', ' ').replace('\u202f', ' ').trim();
	}

	private static String upper(String value) {
		String normalized = nonBlank(value);
		return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
	}

	private static String firstNonBlank(String first, String second) {
		return first == null || first.isBlank() ? second : first;
	}

	private static Optional<ParsedDate> approximate(Instant instant) {
		return Optional.of(new ParsedDate(instant, DateAccuracy.APPROXIMATE));
	}

	private record JsonLdData(JsonNode product, List<String> categoryPath) {
	}
}
