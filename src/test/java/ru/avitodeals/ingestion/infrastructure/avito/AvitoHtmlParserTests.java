package ru.avitodeals.ingestion.infrastructure.avito;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvitoHtmlParserTests {

	private static final Instant NOW = Instant.parse("2026-10-14T12:00:00Z");

	private final AvitoHtmlParser parser = new AvitoHtmlParser(Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void parsesEveryFeedListingWithoutQueryParametersOrPersonalData() throws IOException {
		String html = fixtureStartingWith("Купить");
		List<ParsedFeedItem> items = parser.parseFeed(html);

		assertFalse(items.isEmpty());
		for (ParsedFeedItem item : items) {
			assertNotNull(item.externalId());
			assertNotNull(item.title());
			assertNotNull(item.price());
			assertNotNull(item.currency());
			assertNotNull(item.url());
			assertFalse(item.url().contains("?"));
			assertFalse(item.url().contains("#"));
			assertNotNull(item.regionSlug());
		}
		ParsedFeedItem example = items.stream()
				.filter(item -> item.externalId().equals("8376354125"))
				.findFirst().orElseThrow();
		assertEquals("iPhone 17, 256 ГБ, SIM + eSIM", example.title());
		assertEquals(new BigDecimal("45000"), example.price());
		assertEquals("RUB", example.currency());
		assertEquals("https://www.avito.ru/olenegorsk/telefony/iphone_17_256_gb_sim_esim_8376354125",
				example.url());
		assertEquals("olenegorsk", example.regionSlug());
		assertEquals(NOW.minusSeconds(2 * 60 * 60), example.publishedAtApprox().instant());
		assertEquals(DateAccuracy.APPROXIMATE, example.publishedAtApprox().accuracy());

		ParsedFeed feed = parser.parseFeedPage(html);
		assertEquals(1148, feed.totalCount());
		assertTrue(feed.pageNumbers().contains(2));
		assertEquals(items, feed.items());
	}

	@Test
	void parsesCpuItemFromJsonLdAndParameters() throws IOException {
		ParsedItemPage page = parser.parseItemPage(fixtureStartingWith("Процессор"));

		assertEquals("8098848667", page.externalId());
		assertEquals("Процессор intel core i5 сокет 1200", page.title());
		assertEquals(new BigDecimal("9000"), page.price());
		assertEquals("RUB", page.currency());
		assertEquals("Б/у", page.condition());
		assertEquals("Процессоры", page.categoryPath().getLast());
		assertEquals("Core i5-10400F", page.attributes().get("Модель"));
		assertEquals("LGA1200", page.attributes().get("Сокет"));
		assertEquals(SellerType.PRIVATE, page.sellerType());
		assertEquals(DateAccuracy.APPROXIMATE, page.publishedAt().accuracy());
	}

	@Test
	void parsesIphoneConditionAttributesAndNeverReturnsSellerIdentity() throws IOException {
		ParsedItemPage page = parser.parseItemPage(fixtureStartingWith("iPhone"));

		assertEquals("8450600956", page.externalId());
		assertEquals("Хорошее", page.condition());
		assertEquals("92 %", page.attributes().get("Состояние аккумулятора"));
		assertEquals("Коробка", page.attributes().get("Комплект"));
		assertEquals(SellerType.PRIVATE, page.sellerType());
		assertFalse(Arrays.stream(ParsedItemPage.class.getRecordComponents())
				.map(RecordComponent::getName)
				.anyMatch(name -> name.toLowerCase().contains("sellername")
						|| name.toLowerCase().contains("rating")
						|| name.toLowerCase().contains("phone")));
		assertTrue(page.categoryPath().contains("Электроника"));
	}

	@Test
	void acceptsEmptyAndCardlessFeeds() {
		assertTrue(parser.parseFeed("").isEmpty());
		assertTrue(parser.parseFeed("<html><body>нет карточек</body></html>").isEmpty());
	}

	@Test
	void keepsFeedCardWhenPriceIsMissing() {
		String html = """
				<div data-marker="item" data-item-id="item-1">
				  <a data-marker="item-title" title="Тест" itemprop="url"
				     href="https://www.avito.ru/kirzhach/test/item-1?context=ignored">Тест</a>
				</div>
				""";

		List<ParsedFeedItem> items = parser.parseFeed(html);

		assertEquals(1, items.size());
		assertEquals("item-1", items.getFirst().externalId());
		assertEquals("Тест", items.getFirst().title());
		assertNull(items.getFirst().price());
		assertNull(items.getFirst().currency());
	}

	@Test
	void malformedJsonLdDoesNotPreventReturningPartialItemPage() {
		String html = """
				<script type="application/ld+json">{ broken json }</script>
				<div data-marker="item-view/item-params">
				  <li><span>Состояние</span>Хорошее</li>
				</div>
				""";

		ParsedItemPage page = parser.parseItemPage(html);

		assertNull(page.externalId());
		assertNull(page.title());
		assertEquals("Хорошее", page.condition());
	}

	@Test
	void parsesRelativeAndCalendarDatesWithInjectedClock() {
		assertParsedDate("2 часа назад", NOW.minusSeconds(2 * 60 * 60));
		assertParsedDate("Вчера", Instant.parse("2026-10-13T00:00:00Z"));
		assertParsedDate("3 дня назад", Instant.parse("2026-10-11T00:00:00Z"));
		assertParsedDate("· вчера в 18:38", Instant.parse("2026-10-13T18:38:00Z"));
		assertParsedDate("· 2 октября в 19:10", Instant.parse("2026-10-02T19:10:00Z"));
	}

	@Test
	void marksFullTimestampAsExactAndRelativeDatesAsApproximate() {
		assertEquals(DateAccuracy.EXACT,
				parser.parseDate("2026-10-10T12:30:00Z").orElseThrow().accuracy());
		assertEquals(DateAccuracy.APPROXIMATE,
				parser.parseDate("Вчера").orElseThrow().accuracy());
		assertTrue(parser.parseDate("неизвестная дата").isEmpty());
	}

	private void assertParsedDate(String text, Instant expected) {
		ParsedDate parsed = parser.parseDate(text).orElseThrow();
		assertEquals(expected, parsed.instant(), text);
		assertEquals(DateAccuracy.APPROXIMATE, parsed.accuracy(), text);
	}

	private static String fixtureStartingWith(String prefix) throws IOException {
		try (var paths = Files.list(Path.of("src/test/resources/avito"))) {
			Path fixture = paths.filter(path -> path.getFileName().toString().startsWith(prefix))
					.findFirst().orElseThrow();
			return Files.readString(fixture);
		}
	}
}
