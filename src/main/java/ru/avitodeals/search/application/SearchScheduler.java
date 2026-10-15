package ru.avitodeals.search.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.avitodeals.ingestion.application.IngestionPipeline;
import ru.avitodeals.ingestion.application.IngestionReport;
import ru.avitodeals.ingestion.application.IngestionRequest;
import ru.avitodeals.ingestion.application.ListingSourceException;
import ru.avitodeals.ingestion.application.SearchCriteria;
import ru.avitodeals.search.infrastructure.persistence.SearchRepository;

import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

@Component
public class SearchScheduler {

	private static final Logger LOGGER = LoggerFactory.getLogger(SearchScheduler.class);
	private static final ReentrantLock[] RUN_LOCKS = IntStream.range(0, 64)
			.mapToObj(ignored -> new ReentrantLock(true))
			.toArray(ReentrantLock[]::new);

	private final SearchRepository searchRepository;
	private final SearchRunClaimService claimService;
	private final IngestionPipeline ingestionPipeline;

	public SearchScheduler(
			SearchRepository searchRepository,
			SearchRunClaimService claimService,
			IngestionPipeline ingestionPipeline) {
		this.searchRepository = searchRepository;
		this.claimService = claimService;
		this.ingestionPipeline = ingestionPipeline;
	}

	@Scheduled(fixedDelayString = "${avito-deals.search.scheduler.fixed-delay-ms:60000}")
	public void runDueSearches() {
		var activeSearchIds = searchRepository.findByActiveTrue().stream()
				.map(search -> search.getId())
				.toList();
		LOGGER.info("Search scheduler checked {} active search(es)", activeSearchIds.size());
		for (UUID searchId : activeSearchIds) {
			runSearch(searchId);
		}
	}

	private void runSearch(UUID searchId) {
		ReentrantLock runLock = RUN_LOCKS[Math.floorMod(searchId.hashCode(), RUN_LOCKS.length)];
		runLock.lock();
		try {
			var claim = claimService.claimIfDue(searchId);
			if (claim.isEmpty()) {
				LOGGER.info("Scheduled search {} was not started because its configured interval has not elapsed",
						searchId);
				return;
			}
			var search = claim.orElseThrow();
			SearchCriteria criteria = new SearchCriteria(search.query(), search.filters());
			try {
				LOGGER.info("Starting scheduled ingestion for search {}", search.id());
				IngestionReport report = ingestionPipeline.ingest(
						IngestionRequest.forSearch(criteria, search.id()));
				LOGGER.info("Scheduled ingestion for search {} completed: processed={}, created={}, "
								+ "duplicates={}, priceChanges={}, removed={}",
						search.id(), report.processed(), report.created(), report.duplicates(),
						report.priceChanges(), report.removed());
			} catch (ListingSourceException exception) {
				if (exception.getStatusCode() == 429) {
					claimService.releaseRateLimitedClaim(search.id(), search.claimedAt());
				}
				throw exception;
			}
		} catch (ListingSourceException exception) {
			if (exception.getStatusCode() == 429) {
				if (exception.isDeferredByRateLimit()) {
					LOGGER.debug("Avito search {} was skipped during the global rate-limit cooldown", searchId);
				} else {
					LOGGER.warn("Avito rate-limited scheduled search {}; requests are paused for {}",
							searchId, exception.getRetryAfter());
				}
			} else {
				LOGGER.error("Scheduled search {} failed; it can retry after its configured interval",
						searchId, exception);
			}
		} catch (RuntimeException exception) {
			LOGGER.error("Scheduled search {} failed; it can retry after its configured interval", searchId, exception);
		} finally {
			runLock.unlock();
		}
	}

	public IngestionReport runNow(UUID searchId) {
		ReentrantLock runLock = RUN_LOCKS[Math.floorMod(searchId.hashCode(), RUN_LOCKS.length)];
		runLock.lock();
		try {
			SearchRunSnapshot search = claimService.claimNow(searchId);
			try {
				LOGGER.info("Starting manually requested ingestion for search {}", search.id());
				IngestionReport report = ingestionPipeline.ingest(
						IngestionRequest.forSearch(new SearchCriteria(search.query(), search.filters()), search.id()));
				LOGGER.info("Manual ingestion for search {} completed: processed={}, created={}, "
								+ "duplicates={}, priceChanges={}, removed={}",
						search.id(), report.processed(), report.created(), report.duplicates(),
						report.priceChanges(), report.removed());
				return report;
			} catch (ListingSourceException exception) {
				if (exception.getStatusCode() == 429) {
					claimService.releaseRateLimitedClaim(search.id(), search.claimedAt());
				}
				throw exception;
			}
		} finally {
			runLock.unlock();
		}
	}
}
