package ru.avitodeals.ingestion.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import ru.avitodeals.ingestion.application.ListingSource;
import ru.avitodeals.ingestion.application.RawListing;
import ru.avitodeals.ingestion.application.SearchCriteria;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "avito-deals.ingestion.source", havingValue = "mock", matchIfMissing = true)
public class MockListingSource implements ListingSource {

	private static final List<RawListing> LISTINGS = List.of(
			new RawListing("mock-1001", "Смартфон Pixel 8", "Хорошее состояние, комплект",
					new BigDecimal("42000.00"), "RUB", "GOOD", "Москва",
					"https://example.invalid/listings/mock-1001", "seller-1", "Mock Seller"),
			new RawListing("mock-1002", "Наушники Sony WH-1000XM5", "Почти новые",
					new BigDecimal("18000.00"), "RUB", "NEW", "Санкт-Петербург",
					"https://example.invalid/listings/mock-1002", "seller-2", "Sample Seller"));

	@Override
	public String sourceId() {
		return "mock";
	}

	@Override
	public List<RawListing> fetch(SearchCriteria criteria) {
		String query = criteria.query().toLowerCase(Locale.ROOT);
		return LISTINGS.stream()
				.filter(listing -> query.isBlank()
						|| listing.title().toLowerCase(Locale.ROOT).contains(query)
						|| listing.description().toLowerCase(Locale.ROOT).contains(query))
				.filter(listing -> matchesFilters(listing, criteria.filters()))
				.toList();
	}

	private boolean matchesFilters(RawListing listing, Map<String, Object> filters) {
		Object minimum = filters.get("priceMin");
		if (minimum != null && listing.price().compareTo(toPrice(minimum)) < 0) {
			return false;
		}
		Object maximum = filters.get("priceMax");
		if (maximum != null && listing.price().compareTo(toPrice(maximum)) > 0) {
			return false;
		}
		Object location = filters.get("location");
		return location == null || listing.location().toLowerCase(Locale.ROOT)
				.contains(location.toString().toLowerCase(Locale.ROOT));
	}

	private BigDecimal toPrice(Object value) {
		if (value instanceof BigDecimal price) {
			return price;
		}
		if (value instanceof Number number) {
			return new BigDecimal(number.toString());
		}
		try {
			return new BigDecimal(value.toString());
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException("Mock listing price filters must be numeric", exception);
		}
	}
}
