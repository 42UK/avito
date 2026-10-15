package ru.avitodeals.search.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.avitodeals.ingestion.application.ListingObserved;
import ru.avitodeals.search.domain.SearchListing;
import ru.avitodeals.search.domain.SearchListingId;
import ru.avitodeals.search.infrastructure.persistence.SearchListingRepository;
import ru.avitodeals.shared.application.TransactionScopedKeyLock;

@Component
public class SearchIngestionEventListener {

	private final SearchListingRepository searchListingRepository;
	private final TransactionScopedKeyLock keyLock;

	public SearchIngestionEventListener(
			SearchListingRepository searchListingRepository,
			TransactionScopedKeyLock keyLock) {
		this.searchListingRepository = searchListingRepository;
		this.keyLock = keyLock;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onListingObserved(ListingObserved event) {
		if (event.searchId() == null) {
			return;
		}
		keyLock.acquire("search-listing:" + event.searchId() + ":" + event.listingId());
		SearchListingId id = new SearchListingId(event.searchId(), event.listingId());
		SearchListing relation = searchListingRepository.findById(id).orElseGet(() -> {
			return new SearchListing(id, event.observedAt(), event.observedAt());
		});
		if (event.observedAt().isBefore(relation.getFirstSeenAt())) {
			relation.setFirstSeenAt(event.observedAt());
		}
		if (event.observedAt().isAfter(relation.getLastSeenAt())) {
			relation.setLastSeenAt(event.observedAt());
		}
		searchListingRepository.save(relation);
	}
}
