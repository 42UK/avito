package ru.avitodeals.ingestion.infrastructure.avito;

import org.junit.jupiter.api.Test;
import ru.avitodeals.ingestion.application.RawListing;
import ru.avitodeals.ingestion.application.SearchCriteria;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvitoListingSourceTests {

	private static final String FEED_HTML = fixture();
	private final AvitoIngestionProperties properties = properties();

	@Test
	void fetchesPublicFeedPagesMapsRowsAndDoesNotIncludeSellerPersonalData() {
		List<String> requestedUris = new ArrayList<>();
		AvitoListingSource source = new AvitoListingSource(uri -> {
			requestedUris.add(uri.toString());
			return FEED_HTML;
		}, new AvitoHtmlParser(Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC)),
				properties);

		List<RawListing> listings = source.fetch(new SearchCriteria("iPhone", Map.of(
				"location", "Оленегорск", "category", "Телефоны",
				"priceMin", 10000, "priceMax", 60000)));

		assertEquals(properties.getMaxPages(), requestedUris.size());
		assertTrue(requestedUris.getFirst().startsWith("https://www.avito.ru/rossiya?"));
		assertTrue(requestedUris.getFirst().contains("q=iPhone+"));
		assertTrue(requestedUris.getFirst().contains("pmin=10000"));
		assertTrue(requestedUris.getFirst().contains("pmax=60000"));
		assertTrue(requestedUris.getFirst().contains("%D0%9E"));
		RawListing listing = listings.stream().filter(item -> item.externalId().equals("8376354125"))
				.findFirst().orElseThrow();
		assertEquals("iPhone 17, 256 ГБ, SIM + eSIM", listing.title());
		assertEquals(new BigDecimal("45000"), listing.price());
		assertEquals("RUB", listing.currency());
		assertEquals("olenegorsk", listing.location());
		assertEquals("https://www.avito.ru/olenegorsk/telefony/iphone_17_256_gb_sim_esim_8376354125",
				listing.url());
		assertEquals("UNKNOWN", listing.condition());
		assertNull(listing.description());
		assertNull(listing.sellerExternalId());
		assertNull(listing.sellerName());
	}

	@Test
	void excludesFeedCardsFromDifferentRegionsWhenLocationIsSelected() {
		AvitoListingSource source = new AvitoListingSource(uri -> FEED_HTML,
				new AvitoHtmlParser(Clock.systemUTC()), properties);

		List<RawListing> listings = source.fetch(new SearchCriteria("phone", Map.of("location", "Москва")));

		assertTrue(listings.stream().noneMatch(item -> item.location().equals("olenegorsk")));
		assertTrue(listings.stream().noneMatch(item -> item.location().equals("киржач")));
	}

	@Test
	void observesConfiguredPageLimitAndDeduplicatesListings() {
		AvitoIngestionProperties limited = properties();
		limited.setMaxPages(1);
		List<String> requestedUris = new ArrayList<>();
		AvitoListingSource source = new AvitoListingSource(uri -> {
			requestedUris.add(uri.toString());
			return FEED_HTML;
		}, new AvitoHtmlParser(Clock.systemUTC()), limited);

		List<RawListing> listings = source.fetch(new SearchCriteria("phone", Map.of()));

		assertEquals(1, requestedUris.size());
		assertEquals(listings.size(), listings.stream().map(RawListing::externalId).distinct().count());
	}

	@Test
	void skipsIncompleteCardsRatherThanFailingTheWholeFeed() {
		String html = """
				<div data-marker="item" data-item-id="without-price">
				  <a data-marker="item-title" itemprop="url" title="No price"
				     href="https://www.avito.ru/rossiya/item">No price</a>
				</div>
				""";
		AvitoListingSource source = new AvitoListingSource(uri -> html,
				new AvitoHtmlParser(Clock.systemUTC()), properties);

		assertTrue(source.fetch(new SearchCriteria("query", Map.of())).isEmpty());
	}

	private static AvitoIngestionProperties properties() {
		AvitoIngestionProperties properties = new AvitoIngestionProperties();
		properties.setRegionSlug("rossiya");
		properties.setMaxPages(2);
		properties.setMinimumRequestInterval(java.time.Duration.ZERO);
		return properties;
	}

	private static String fixture() {
		try (var files = Files.list(Path.of("src/test/resources/avito"))) {
			Path path = files.filter(file -> file.getFileName().toString().startsWith("Купить"))
					.findFirst().orElseThrow();
			return Files.readString(path);
		} catch (IOException exception) {
			throw new IllegalStateException("Avito feed fixture is unavailable", exception);
		}
	}
}
