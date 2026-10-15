package ru.avitodeals.listing.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.listing.domain.Listing;
import ru.avitodeals.listing.domain.ListingStatus;
import ru.avitodeals.listing.infrastructure.persistence.ListingPriceHistoryRepository;
import ru.avitodeals.listing.infrastructure.persistence.ListingRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ListingQueryService implements ListingQueries {

	private final ListingRepository listingRepository;
	private final ListingPriceHistoryRepository historyRepository;

	public ListingQueryService(
			ListingRepository listingRepository,
			ListingPriceHistoryRepository historyRepository) {
		this.listingRepository = listingRepository;
		this.historyRepository = historyRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public Page<ListingView> list(int page, int size) {
		validatePage(page, size);
		return listingRepository.findAllByOrderByLastSeenAtDescIdDesc(PageRequest.of(page, size))
				.map(ListingQueryService::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ListingView> find(UUID id) {
		return listingRepository.findById(id).map(ListingQueryService::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ListingView> findByIds(Collection<UUID> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return listingRepository.findByIdIn(ids).stream().map(ListingQueryService::toView).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ListingPricePoint> priceHistory(UUID listingId) {
		return historyRepository.findByListingIdOrderByObservedAtAsc(listingId).stream()
				.map(point -> new ListingPricePoint(point.getPrice(), point.getCurrency(), point.getObservedAt()))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public long count() {
		return listingRepository.count();
	}

	@Override
	@Transactional(readOnly = true)
	public long countActive() {
		return listingRepository.countByStatus(ListingStatus.ACTIVE);
	}

	private static ListingView toView(Listing listing) {
		return new ListingView(listing.getId(), listing.getSource(), listing.getExternalId(),
				listing.getTitle(), listing.getDescription(), listing.getPrice(), listing.getCurrency(),
				listing.getCondition().name(), listing.getLocation(), listing.getUrl(), listing.getStatus().name(),
				listing.getSellerId(), listing.getFirstSeenAt(), listing.getLastSeenAt(),
				listing.getDataOrigin());
	}

	private static void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
		}
	}
}
