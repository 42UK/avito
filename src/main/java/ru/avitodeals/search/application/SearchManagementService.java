package ru.avitodeals.search.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.search.domain.Search;
import ru.avitodeals.search.infrastructure.persistence.SearchRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SearchManagementService {

	private final SearchRepository repository;

	public SearchManagementService(SearchRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public Page<SearchView> list(int page, int size) {
		validatePage(page, size);
		return repository.findAll(PageRequest.of(page, size))
				.map(SearchManagementService::toView);
	}

	@Transactional(readOnly = true)
	public SearchView get(UUID id) {
		return repository.findById(id).map(SearchManagementService::toView)
				.orElseThrow(() -> new SearchNotFoundException(id));
	}

	@Transactional
	public SearchView create(SearchCommand command) {
		validate(command);
		Search search = new Search(command.name().trim(), command.query().trim(),
				command.filters(), command.minDealScore(), command.active(), command.intervalMinutes());
		return toView(repository.save(search));
	}

	@Transactional
	public SearchView update(UUID id, SearchCommand command) {
		validate(command);
		Search search = repository.findById(id).orElseThrow(() -> new SearchNotFoundException(id));
		apply(search, command);
		search.setLastRunAt(null);
		return toView(repository.save(search));
	}

	@Transactional
	public void delete(UUID id) {
		if (!repository.existsById(id)) {
			throw new SearchNotFoundException(id);
		}
		repository.deleteById(id);
	}

	@Transactional(readOnly = true)
	public long count() {
		return repository.count();
	}

	@Transactional(readOnly = true)
	public long countActive() {
		return repository.countByActiveTrue();
	}

	private static void apply(Search search, SearchCommand command) {
		search.setName(command.name().trim());
		search.setQuery(command.query().trim());
		search.setFilters(new HashMap<>(command.filters() == null ? Map.of() : command.filters()));
		search.setIntervalMinutes(command.intervalMinutes());
		search.setMinDealScore(command.minDealScore());
		search.setActive(command.active());
	}

	private static SearchView toView(Search search) {
		return new SearchView(search.getId(), search.getName(), search.getQuery(), search.getFilters(),
				search.getIntervalMinutes(), search.getMinDealScore(), search.isActive(), search.getLastRunAt());
	}

	private static void validate(SearchCommand command) {
		if (command == null || blank(command.name()) || command.name().length() > 500
				|| blank(command.query()) || command.query().length() > 5000
				|| command.intervalMinutes() < 1 || command.intervalMinutes() > 10080
				|| command.minDealScore() == null || command.minDealScore().compareTo(BigDecimal.ZERO) < 0
				|| command.minDealScore().compareTo(BigDecimal.valueOf(100)) > 0
				|| command.minDealScore().stripTrailingZeros().scale() > 2
				|| !validFilters(command.filters())) {
			throw new IllegalArgumentException("Search fields or filters are invalid");
		}
	}

	private static boolean validFilters(Map<String, Object> filters) {
		if (filters == null) {
			return true;
		}
		if (filters.values().stream().anyMatch(value -> value == null)) {
			return false;
		}
		if (!filters.keySet().stream().allMatch(key ->
				key.equals("priceMin") || key.equals("priceMax") || key.equals("location") || key.equals("category"))) {
			return false;
		}
		BigDecimal min = decimal(filters.get("priceMin"));
		BigDecimal max = decimal(filters.get("priceMax"));
		if (filters.containsKey("priceMin") && min == null || filters.containsKey("priceMax") && max == null) {
			return false;
		}
		if (min != null && min.signum() < 0 || max != null && max.signum() < 0
				|| min != null && max != null && min.compareTo(max) > 0) {
			return false;
		}
		return textFilter(filters.get("location")) && textFilter(filters.get("category"));
	}

	private static BigDecimal decimal(Object value) {
		if (value instanceof BigDecimal decimal) {
			return decimal;
		}
		if (value instanceof Number number) {
			return new BigDecimal(number.toString());
		}
		return null;
	}

	private static boolean textFilter(Object value) {
		return value == null || value instanceof String;
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private static void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
		}
	}
}
