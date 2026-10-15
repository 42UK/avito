package ru.avitodeals.search.application;

import org.junit.jupiter.api.Test;
import ru.avitodeals.search.domain.Search;
import ru.avitodeals.search.infrastructure.persistence.SearchRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchManagementServiceTests {

	private final SearchRepository repository = mock(SearchRepository.class);
	private final SearchManagementService service = new SearchManagementService(repository);

	@Test
	void rejectsInvalidRangesAndUnknownFilterKeysBeforePersistence() {
		assertThrows(IllegalArgumentException.class, () -> service.create(new SearchCommand(
				"search", "phone", Map.of("priceMin", 20, "priceMax", 10),
				60, BigDecimal.TEN, true)));
		assertThrows(IllegalArgumentException.class, () -> service.create(new SearchCommand(
				"search", "phone", Map.of("unexpected", "value"),
				60, BigDecimal.TEN, true)));
		assertThrows(IllegalArgumentException.class, () -> service.create(new SearchCommand(
				"search", "phone", Map.of(), 0, BigDecimal.TEN, true)));
		verify(repository, never()).save(any());
	}

	@Test
	void acceptsValidSearchAndNormalizesTextAndFilters() {
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		SearchView result = service.create(new SearchCommand(
				"  Pixel  ", "  Google Pixel  ", Map.of("location", "Москва", "priceMin", 10),
				60, new BigDecimal("40"), true));

		org.junit.jupiter.api.Assertions.assertEquals("Pixel", result.name());
		org.junit.jupiter.api.Assertions.assertEquals("Google Pixel", result.query());
		org.junit.jupiter.api.Assertions.assertEquals("Москва", result.filters().get("location"));
		verify(repository).save(any());
	}

	@Test
	void updatingSearchMakesItEligibleForAnImmediateRun() {
		UUID id = UUID.randomUUID();
		Search search = new Search("Pixel", "Pixel 8", Map.of(), BigDecimal.TEN, true, 60);
		search.setId(id);
		search.setLastRunAt(Instant.parse("2026-10-14T15:00:00Z"));
		when(repository.findById(id)).thenReturn(java.util.Optional.of(search));
		when(repository.save(search)).thenReturn(search);

		service.update(id, new SearchCommand("Pixel", "Pixel 8", Map.of(), 60, BigDecimal.TEN, true));

		org.junit.jupiter.api.Assertions.assertNull(search.getLastRunAt());
	}
}
