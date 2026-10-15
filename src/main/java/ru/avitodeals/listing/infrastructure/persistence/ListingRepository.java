package ru.avitodeals.listing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.avitodeals.listing.domain.Listing;
import ru.avitodeals.listing.domain.ListingStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

	Optional<Listing> findBySourceAndExternalId(String source, String externalId);

	List<Listing> findBySourceAndStatus(String source, ListingStatus status);

	Page<Listing> findAllByOrderByLastSeenAtDescIdDesc(Pageable pageable);

	List<Listing> findByIdIn(Iterable<UUID> ids);

	long countByStatus(ListingStatus status);
}
