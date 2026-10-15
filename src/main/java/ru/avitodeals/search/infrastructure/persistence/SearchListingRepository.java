package ru.avitodeals.search.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.avitodeals.search.domain.SearchListing;
import ru.avitodeals.search.domain.SearchListingId;

import java.util.List;
import java.util.UUID;

public interface SearchListingRepository extends JpaRepository<SearchListing, SearchListingId> {

	List<SearchListing> findByIdSearchId(UUID searchId);

	List<SearchListing> findByIdListingId(UUID listingId);
}
