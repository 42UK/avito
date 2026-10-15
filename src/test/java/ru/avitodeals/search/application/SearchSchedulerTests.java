package ru.avitodeals.search.application;

import org.junit.jupiter.api.Test;
import ru.avitodeals.ingestion.application.IngestionPipeline;
import ru.avitodeals.ingestion.application.IngestionRequest;
import ru.avitodeals.ingestion.application.SearchCriteria;
import ru.avitodeals.ingestion.infrastructure.avito.AvitoFetchException;
import ru.avitodeals.search.infrastructure.persistence.SearchRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchSchedulerTests {

	@Test
	void convertsQueryAndFiltersAndPassesSearchIdToPartialPipelineRequest() {
		SearchRepository repository = mock(SearchRepository.class);
		SearchRunClaimService claimService = mock(SearchRunClaimService.class);
		IngestionPipeline pipeline = mock(IngestionPipeline.class);
		UUID searchId = UUID.randomUUID();
		when(repository.findByActiveTrue()).thenReturn(java.util.List.of(searchRow(searchId)));
		when(claimService.claimIfDue(searchId)).thenReturn(Optional.of(new SearchRunSnapshot(
				searchId, "Pixel search", "  Pixel 8 ", Map.of("priceMin", 1000, "location", "Москва"),
				Instant.parse("2026-10-14T15:00:00Z"))));
		when(pipeline.ingest(any(IngestionRequest.class))).thenReturn(new ru.avitodeals.ingestion.application.IngestionReport(
				1, 0, 1, 0, 0));
		SearchScheduler scheduler = new SearchScheduler(repository, claimService, pipeline);

		scheduler.runDueSearches();

		verify(pipeline).ingest(new IngestionRequest(
				new SearchCriteria("Pixel 8", Map.of("priceMin", 1000, "location", "Москва")),
				searchId, false));
	}

	@Test
	void aFailedSearchIsLoggedAndDoesNotPreventLaterSearches() {
		SearchRepository repository = mock(SearchRepository.class);
		SearchRunClaimService claimService = mock(SearchRunClaimService.class);
		IngestionPipeline pipeline = mock(IngestionPipeline.class);
		UUID failedSearchId = UUID.randomUUID();
		UUID healthySearchId = UUID.randomUUID();
		when(repository.findByActiveTrue()).thenReturn(java.util.List.of(
				searchRow(failedSearchId), searchRow(healthySearchId)));
		when(claimService.claimIfDue(failedSearchId)).thenReturn(Optional.of(
				new SearchRunSnapshot(failedSearchId, "broken", "x", Map.of(),
						Instant.parse("2026-10-14T15:00:00Z"))));
		when(claimService.claimIfDue(healthySearchId)).thenReturn(Optional.of(
				new SearchRunSnapshot(healthySearchId, "healthy", "y", Map.of(),
						Instant.parse("2026-10-14T15:00:00Z"))));
		when(pipeline.ingest(new IngestionRequest(new SearchCriteria("y", Map.of()), healthySearchId, false)))
				.thenReturn(new ru.avitodeals.ingestion.application.IngestionReport(0, 0, 0, 0, 0));
		doThrow(new IllegalStateException("source unavailable"))
				.when(pipeline).ingest(new IngestionRequest(new SearchCriteria("x", Map.of()), failedSearchId, false));
		SearchScheduler scheduler = new SearchScheduler(repository, claimService, pipeline);

		scheduler.runDueSearches();

		verify(pipeline).ingest(new IngestionRequest(new SearchCriteria("y", Map.of()), healthySearchId, false));
	}

	@Test
	void rateLimitedSearchClaimIsReleasedSoTheSearchCanRetryAfterCooldown() {
		SearchRepository repository = mock(SearchRepository.class);
		SearchRunClaimService claimService = mock(SearchRunClaimService.class);
		IngestionPipeline pipeline = mock(IngestionPipeline.class);
		UUID searchId = UUID.randomUUID();
		Instant claimedAt = Instant.parse("2026-10-14T15:00:00Z");
		when(repository.findByActiveTrue()).thenReturn(java.util.List.of(searchRow(searchId)));
		when(claimService.claimIfDue(searchId)).thenReturn(Optional.of(
				new SearchRunSnapshot(searchId, "rate-limited", "x", Map.of(), claimedAt)));
		doThrow(new AvitoFetchException(
				"HTTP 429", 429, java.time.Duration.ofMinutes(5), false))
				.when(pipeline).ingest(new IngestionRequest(new SearchCriteria("x", Map.of()), searchId, false));
		SearchScheduler scheduler = new SearchScheduler(repository, claimService, pipeline);

		scheduler.runDueSearches();

		verify(claimService).releaseRateLimitedClaim(searchId, claimedAt);
	}

	@Test
	void notDueSearchIsNotSentToThePipeline() {
		SearchRepository repository = mock(SearchRepository.class);
		SearchRunClaimService claimService = mock(SearchRunClaimService.class);
		IngestionPipeline pipeline = mock(IngestionPipeline.class);
		UUID searchId = UUID.randomUUID();
		when(repository.findByActiveTrue()).thenReturn(java.util.List.of(searchRow(searchId)));
		when(claimService.claimIfDue(searchId)).thenReturn(Optional.empty());

		new SearchScheduler(repository, claimService, pipeline).runDueSearches();

		verify(pipeline, never()).ingest(any(IngestionRequest.class));
	}

	private ru.avitodeals.search.domain.Search searchRow(UUID id) {
		return new SearchRow(id);
	}

	private static final class SearchRow extends ru.avitodeals.search.domain.Search {

		private SearchRow(UUID id) {
			setId(id);
			setName("Test");
			setQuery("");
			setIntervalMinutes(60);
		}
	}
}
