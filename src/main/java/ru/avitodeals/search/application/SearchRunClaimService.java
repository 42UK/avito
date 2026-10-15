package ru.avitodeals.search.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.search.domain.Search;
import ru.avitodeals.search.infrastructure.persistence.SearchRepository;
import ru.avitodeals.shared.application.TransactionScopedKeyLock;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class SearchRunClaimService {

	private final SearchRepository searchRepository;
	private final TransactionScopedKeyLock keyLock;
	private final Clock clock;

	public SearchRunClaimService(
			SearchRepository searchRepository,
			TransactionScopedKeyLock keyLock,
			Clock clock) {
		this.searchRepository = searchRepository;
		this.keyLock = keyLock;
		this.clock = clock;
	}

	@Transactional
	public Optional<SearchRunSnapshot> claimIfDue(UUID searchId) {
		keyLock.acquire("search-run:" + searchId);
		Search search = searchRepository.findByIdForUpdate(searchId).orElse(null);
		if (search == null || !search.isActive()) {
			return Optional.empty();
		}

		Instant now = clock.instant();
		Instant lastRunAt = search.getLastRunAt();
		if (lastRunAt != null && now.isBefore(lastRunAt.plusSeconds(search.getIntervalMinutes() * 60L))) {
			return Optional.empty();
		}

		search.setLastRunAt(now);
		searchRepository.save(search);
		return Optional.of(new SearchRunSnapshot(
				search.getId(), search.getName(), search.getQuery(), search.getFilters(), now));
	}

	@Transactional
	public void releaseRateLimitedClaim(UUID searchId, Instant claimedAt) {
		keyLock.acquire("search-run:" + searchId);
		Search search = searchRepository.findByIdForUpdate(searchId).orElse(null);
		if (search != null && claimedAt.equals(search.getLastRunAt())) {
			search.setLastRunAt(null);
			searchRepository.save(search);
		}
	}
}
