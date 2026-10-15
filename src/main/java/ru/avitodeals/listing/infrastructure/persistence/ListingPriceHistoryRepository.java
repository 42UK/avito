package ru.avitodeals.listing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.avitodeals.listing.domain.ListingPriceHistory;

import java.util.List;
import java.util.UUID;

public interface ListingPriceHistoryRepository extends JpaRepository<ListingPriceHistory, UUID> {

	List<ListingPriceHistory> findByListingIdOrderByObservedAtAsc(UUID listingId);
}
