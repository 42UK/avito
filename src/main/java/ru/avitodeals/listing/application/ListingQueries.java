package ru.avitodeals.listing.application;

import org.springframework.data.domain.Page;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingQueries {

	Page<ListingView> list(int page, int size);

	Optional<ListingView> find(UUID id);

	List<ListingView> findByIds(Collection<UUID> ids);

	List<ListingPricePoint> priceHistory(UUID listingId);

	long count();

	long countActive();
}
