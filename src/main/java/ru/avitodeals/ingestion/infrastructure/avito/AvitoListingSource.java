package ru.avitodeals.ingestion.infrastructure.avito;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.avitodeals.ingestion.application.ListingSource;
import ru.avitodeals.ingestion.application.RawListing;
import ru.avitodeals.ingestion.application.SearchCriteria;
import ru.avitodeals.listing.application.DataOrigin;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "avito-deals.ingestion.source", havingValue = "avito")
public class AvitoListingSource implements ListingSource {

	private static final Logger LOGGER = LoggerFactory.getLogger(AvitoListingSource.class);
	private static final Pattern REGION_SLUG = Pattern.compile("[a-z0-9_-]+");
	private static final String SOURCE_ID = "avito";

	private final AvitoPageFetcher pageFetcher;
	private final AvitoHtmlParser parser;
	private final AvitoIngestionProperties properties;

	public AvitoListingSource(
			AvitoPageFetcher pageFetcher,
			AvitoHtmlParser parser,
			AvitoIngestionProperties properties) {
		if (properties.getMaxPages() < 1 || properties.getMaxPages() > 5
				|| properties.getRegionSlug() == null
				|| !REGION_SLUG.matcher(properties.getRegionSlug()).matches()) {
			throw new IllegalArgumentException("Avito region slug and max pages must be valid (1..5)");
		}
		this.pageFetcher = pageFetcher;
		this.parser = parser;
		this.properties = properties;
	}

	@Override
	public String sourceId() {
		return SOURCE_ID;
	}

	@Override
	public DataOrigin dataOrigin() {
		return DataOrigin.REAL;
	}

	@Override
	public List<RawListing> fetch(SearchCriteria criteria) {
		Map<String, RawListing> listings = new LinkedHashMap<>();
		List<Integer> pagesToFetch = new ArrayList<>();
		pagesToFetch.add(1);
		int requestedPages = 0;
		for (int index = 0; index < pagesToFetch.size() && requestedPages < properties.getMaxPages(); index++) {
			int pageNumber = pagesToFetch.get(index);
			URI pageUri = searchUri(criteria, pageNumber);
			ParsedFeed feed = parser.parseFeedPage(pageFetcher.fetch(pageUri));
			requestedPages++;
			for (ParsedFeedItem item : feed.items()) {
				if (!matchesRequestedLocation(item, criteria)) {
					LOGGER.debug("Skipping Avito listing {} because it does not match the requested location",
							item.externalId());
					continue;
				}
				if (!isIngestable(item)) {
					LOGGER.warn("Skipping Avito listing {} because required feed fields are missing",
							item.externalId());
					continue;
				}
				RawListing listing = toRawListing(item);
				listings.putIfAbsent(listing.externalId(), listing);
			}
			for (Integer nextPage : feed.pageNumbers()) {
				if (nextPage != null && nextPage > pageNumber && !pagesToFetch.contains(nextPage)
						&& pagesToFetch.size() < properties.getMaxPages()) {
					pagesToFetch.add(nextPage);
				}
			}
		}
		return List.copyOf(listings.values());
	}

	private URI searchUri(SearchCriteria criteria, int pageNumber) {
		List<String> terms = new ArrayList<>();
		addTerm(terms, criteria.query());
		addTerm(terms, filterText(criteria.filters().get("location")));
		addTerm(terms, filterText(criteria.filters().get("category")));
		List<String> parameters = new ArrayList<>();
		if (!terms.isEmpty()) {
			parameters.add("q=" + encode(String.join(" ", terms)));
		}
		addPrice(parameters, "pmin", criteria.filters().get("priceMin"));
		addPrice(parameters, "pmax", criteria.filters().get("priceMax"));
		if (pageNumber > 1) {
			parameters.add("p=" + pageNumber);
		}
		String query = parameters.isEmpty() ? "" : "?" + String.join("&", parameters);
		return URI.create("https://www.avito.ru/" + properties.getRegionSlug() + query);
	}

	private static void addPrice(List<String> parameters, String key, Object value) {
		if (value == null) {
			return;
		}
		BigDecimal price;
		try {
			price = value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Avito price filters must be numeric", exception);
		}
		if (price.signum() < 0) {
			throw new IllegalArgumentException("Avito price filters must not be negative");
		}
		parameters.add(key + "=" + encode(price.stripTrailingZeros().toPlainString()));
	}

	private static void addTerm(List<String> terms, String value) {
		if (value != null && !value.isBlank()) {
			terms.add(value.trim());
		}
	}

	private static String filterText(Object value) {
		return value == null ? null : value.toString();
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static RawListing toRawListing(ParsedFeedItem item) {
		return new RawListing(item.externalId(), item.title(), null, item.price(),
				item.currency(), "UNKNOWN", item.regionSlug(), item.url(), null, null);
	}

	private static boolean isIngestable(ParsedFeedItem item) {
		return item.externalId() != null && !item.externalId().isBlank()
				&& item.title() != null && !item.title().isBlank()
				&& item.price() != null
				&& item.currency() != null && item.currency().matches("[A-Z]{3}")
				&& item.url() != null && isAvitoUrl(item.url());
	}

	private static boolean matchesRequestedLocation(ParsedFeedItem item, SearchCriteria criteria) {
		String requestedLocation = filterText(criteria.filters().get("location"));
		if (requestedLocation == null || requestedLocation.isBlank()) {
			return true;
		}
		String requestedSlug = toRegionSlug(requestedLocation);
		return item.regionSlug() != null && item.regionSlug().equalsIgnoreCase(requestedSlug);
	}

	private static String toRegionSlug(String location) {
		StringBuilder slug = new StringBuilder();
		for (char character : location.trim().toLowerCase(Locale.ROOT).toCharArray()) {
			String transliterated = switch (character) {
				case 'а' -> "a";
				case 'б' -> "b";
				case 'в' -> "v";
				case 'г' -> "g";
				case 'д' -> "d";
				case 'е', 'ё', 'э' -> "e";
				case 'ж' -> "zh";
				case 'з' -> "z";
				case 'и', 'й' -> "i";
				case 'к' -> "k";
				case 'л' -> "l";
				case 'м' -> "m";
				case 'н' -> "n";
				case 'о' -> "o";
				case 'п' -> "p";
				case 'р' -> "r";
				case 'с' -> "s";
				case 'т' -> "t";
				case 'у' -> "u";
				case 'ф' -> "f";
				case 'х' -> "h";
				case 'ц' -> "ts";
				case 'ч' -> "ch";
				case 'ш' -> "sh";
				case 'щ' -> "sch";
				case 'ъ', 'ь' -> "";
				case 'ы' -> "y";
				case 'ю' -> "yu";
				case 'я' -> "ya";
				default -> Character.isLetterOrDigit(character) && character < 128
						? Character.toString(character) : "-";
			};
			if (transliterated.equals("-")) {
				if (!slug.isEmpty() && slug.charAt(slug.length() - 1) != '-') {
					slug.append('-');
				}
			} else {
				slug.append(transliterated);
			}
		}
		while (!slug.isEmpty() && slug.charAt(slug.length() - 1) == '-') {
			slug.deleteCharAt(slug.length() - 1);
		}
		return slug.toString();
	}

	private static boolean isAvitoUrl(String value) {
		try {
			URI uri = URI.create(value);
			return "https".equalsIgnoreCase(uri.getScheme())
					&& "www.avito.ru".equalsIgnoreCase(uri.getHost())
					&& uri.getUserInfo() == null;
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}
}
