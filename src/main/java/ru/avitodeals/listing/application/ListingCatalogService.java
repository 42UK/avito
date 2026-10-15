package ru.avitodeals.listing.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.listing.domain.Listing;
import ru.avitodeals.listing.domain.ListingCondition;
import ru.avitodeals.listing.domain.ListingPriceHistory;
import ru.avitodeals.listing.domain.ListingStatus;
import ru.avitodeals.listing.domain.Seller;
import ru.avitodeals.listing.infrastructure.persistence.ListingPriceHistoryRepository;
import ru.avitodeals.listing.infrastructure.persistence.ListingRepository;
import ru.avitodeals.listing.infrastructure.persistence.SellerRepository;
import ru.avitodeals.shared.application.TransactionScopedKeyLock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ListingCatalogService implements ListingCatalog {

	private final ListingRepository listingRepository;
	private final ListingPriceHistoryRepository priceHistoryRepository;
	private final SellerRepository sellerRepository;
	private final TransactionScopedKeyLock keyLock;

	public ListingCatalogService(
			ListingRepository listingRepository,
			ListingPriceHistoryRepository priceHistoryRepository,
			SellerRepository sellerRepository,
			TransactionScopedKeyLock keyLock) {
		this.listingRepository = listingRepository;
		this.priceHistoryRepository = priceHistoryRepository;
		this.sellerRepository = sellerRepository;
		this.keyLock = keyLock;
	}

	@Override
	@Transactional
	public ListingChange upsert(ListingData data, Instant observedAt) {
		validate(data, observedAt);
		keyLock.acquire("listing-source:" + data.sourceId());
		String currency = data.currency().trim().toUpperCase(Locale.ROOT);
		Listing listing = listingRepository.findBySourceAndExternalId(data.sourceId(), data.externalId())
				.orElse(null);

		if (listing == null) {
			listing = new Listing(data.sourceId(), data.externalId());
			listing.setFirstSeenAt(observedAt);
			listing.setPrice(data.price());
			listing.setCurrency(currency);
			updateListing(listing, data, currency, observedAt);
			listing.setStatus(ListingStatus.ACTIVE);
			listing.setSellerId(resolveSeller(data, listing.getSellerId()));
			listing = listingRepository.save(listing);
			savePriceHistory(listing, data.price(), currency, observedAt);
			return new ListingChange(toSnapshot(listing), true, false, false, null, null);
		}

		if (observedAt.isBefore(listing.getLastSeenAt())) {
			return new ListingChange(toSnapshot(listing), false, false, false, null, null);
		}

		boolean reactivated = listing.getStatus() == ListingStatus.REMOVED;
		BigDecimal previousPrice = listing.getPrice();
		String previousCurrency = listing.getCurrency();
		boolean priceChanged = previousPrice.compareTo(data.price()) != 0
				|| !previousCurrency.equals(currency);

		updateListing(listing, data, currency, observedAt);
		listing.setStatus(ListingStatus.ACTIVE);
		listing.setSellerId(resolveSeller(data, listing.getSellerId()));
		listing = listingRepository.save(listing);

		if (priceChanged) {
			savePriceHistory(listing, data.price(), currency, observedAt);
		}
		return new ListingChange(toSnapshot(listing), false, reactivated, priceChanged,
				priceChanged ? previousPrice : null, priceChanged ? previousCurrency : null);
	}

	@Override
	@Transactional
	public List<ListingSnapshot> markMissing(String sourceId, Set<String> presentExternalIds, Instant observedAt) {
		if (sourceId == null || sourceId.isBlank() || presentExternalIds == null || observedAt == null) {
			throw new IllegalArgumentException("Source, present listing IDs and observation time are required");
		}
		keyLock.acquire("listing-source:" + sourceId);
		return listingRepository.findBySourceAndStatus(sourceId, ListingStatus.ACTIVE).stream()
				.filter(listing -> !presentExternalIds.contains(listing.getExternalId()))
				.map(listing -> {
					listing.setStatus(ListingStatus.REMOVED);
					return toSnapshot(listingRepository.save(listing));
				})
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public java.util.Optional<ListingNotificationDetails> findNotificationDetails(UUID listingId) {
		if (listingId == null) {
			throw new IllegalArgumentException("Listing ID is required");
		}
		return listingRepository.findById(listingId)
				.map(listing -> new ListingNotificationDetails(listing.getId(), listing.getTitle(), listing.getUrl()));
	}

	private UUID resolveSeller(ListingData data, UUID existingSellerId) {
		if (data.sellerExternalId() == null || data.sellerExternalId().isBlank()) {
			return existingSellerId;
		}
		Seller seller = sellerRepository.findBySourceAndExternalId(data.sourceId(), data.sellerExternalId())
				.orElseGet(() -> {
					return new Seller(data.sourceId(), data.sellerExternalId());
				});
		if (data.sellerName() != null && !data.sellerName().isBlank()) {
			seller.setName(data.sellerName());
		}
		return sellerRepository.save(seller).getId();
	}

	private void updateListing(Listing listing, ListingData data, String currency, Instant observedAt) {
		listing.setTitle(data.title().trim());
		listing.setDescription(data.description());
		listing.setPrice(data.price());
		listing.setCurrency(currency);
		listing.setCondition(parseCondition(data.condition()));
		listing.setLocation(data.location());
		listing.setUrl(data.url().trim());
		listing.setLastSeenAt(observedAt);
	}

	private ListingCondition parseCondition(String condition) {
		if (condition == null || condition.isBlank()) {
			return ListingCondition.UNKNOWN;
		}
		try {
			return ListingCondition.valueOf(condition.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return ListingCondition.UNKNOWN;
		}
	}

	private void savePriceHistory(Listing listing, BigDecimal price, String currency, Instant observedAt) {
		ListingPriceHistory history = new ListingPriceHistory(listing.getId(), price, currency, observedAt);
		priceHistoryRepository.save(history);
	}

	private ListingSnapshot toSnapshot(Listing listing) {
		return new ListingSnapshot(listing.getId(), listing.getSource(), listing.getExternalId(),
				listing.getPrice(), listing.getCurrency(), listing.getStatus().name(),
				listing.getFirstSeenAt(), listing.getLastSeenAt());
	}

	private void validate(ListingData data, Instant observedAt) {
		if (data == null || observedAt == null
				|| blankOrTooLong(data.sourceId(), 100)
				|| blankOrTooLong(data.externalId(), 255)
				|| data.title() == null || data.title().isBlank()
				|| data.url() == null || data.url().isBlank()
				|| data.price() == null || data.price().signum() < 0
				|| data.currency() == null || !data.currency().trim().matches("[A-Za-z]{3}")) {
			throw new IllegalArgumentException("Listing has invalid or missing required values");
		}
	}

	private boolean blankOrTooLong(String value, int maxLength) {
		return value == null || value.isBlank() || value.length() > maxLength;
	}
}
