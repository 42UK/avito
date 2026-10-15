package ru.avitodeals.scoring.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.listing.application.ListingQueries;
import ru.avitodeals.listing.application.ListingView;
import ru.avitodeals.pricing.application.MarketPriceQueries;
import ru.avitodeals.pricing.application.MarketPriceView;
import ru.avitodeals.product.application.ProductQueries;
import ru.avitodeals.scoring.domain.DealScore;
import ru.avitodeals.scoring.infrastructure.persistence.DealScoreRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DealQueryService {

	private static final String DEAL_FLAG = "DEAL";
	private static final String ACTIVE_STATUS = "ACTIVE";

	private final DealScoreRepository dealScoreRepository;
	private final ListingQueries listingQueries;
	private final ProductQueries productQueries;
	private final MarketPriceQueries marketPriceQueries;

	public DealQueryService(
			DealScoreRepository dealScoreRepository,
			ListingQueries listingQueries,
			ProductQueries productQueries,
			MarketPriceQueries marketPriceQueries) {
		this.dealScoreRepository = dealScoreRepository;
		this.listingQueries = listingQueries;
		this.productQueries = productQueries;
		this.marketPriceQueries = marketPriceQueries;
	}

	@Transactional(readOnly = true)
	public Page<DealCard> list(int page, int size) {
		validatePage(page, size);
		List<DealScore> latestDeals = latestScores().stream()
				.filter(DealScore::isDealAllowed)
				.toList();
		Map<UUID, ListingView> activeListings = listingQueries.findByIds(
						latestDeals.stream().map(DealScore::getListingId).distinct().toList())
				.stream().filter(listing -> ACTIVE_STATUS.equals(listing.status()))
				.collect(Collectors.toMap(ListingView::id, listing -> listing));

		List<DealCard> cards = latestDeals.stream()
				.filter(score -> activeListings.containsKey(score.getListingId()))
				.map(score -> toCard(score, activeListings.get(score.getListingId())))
				.toList();
		int from = (int) Math.min((long) page * size, cards.size());
		int to = Math.min(from + size, cards.size());
		return new PageImpl<>(cards.subList(from, to), PageRequest.of(page, size), cards.size());
	}

	@Transactional(readOnly = true)
	public Optional<DealDetails> find(UUID scoreId) {
		DealScore score = dealScoreRepository.findById(scoreId).orElse(null);
		if (score == null || !score.isDealAllowed()) {
			return Optional.empty();
		}
		DealScore latestForListing = dealScoreRepository
				.findFirstByListingIdOrderByComputedAtDescIdDesc(score.getListingId())
				.orElse(null);
		if (latestForListing == null || !latestForListing.getId().equals(score.getId())) {
			return Optional.empty();
		}
		ListingView listing = listingQueries.find(score.getListingId()).orElse(null);
		if (listing == null || !ACTIVE_STATUS.equals(listing.status())) {
			return Optional.empty();
		}
		MarketPriceView marketPrice = marketPriceQueries.find(score.getMarketPriceId()).orElse(null);
		if (marketPrice == null) {
			return Optional.empty();
		}

		Set<UUID> outlierIds = new HashSet<>(marketPrice.outlierIds());
		List<DealComparable> comparables = listingQueries.findByIds(
						productQueries.matchedListingIds(marketPrice.productId())).stream()
				.filter(candidate -> !candidate.id().equals(listing.id()))
				.filter(candidate -> ACTIVE_STATUS.equals(candidate.status()))
				.filter(candidate -> candidate.condition().equals(marketPrice.condition())
						&& candidate.currency().equalsIgnoreCase(marketPrice.currency()))
				.sorted(Comparator.comparing(ListingView::price))
				.map(candidate -> new DealComparable(candidate, outlierIds.contains(candidate.id())))
				.toList();

		return Optional.of(new DealDetails(score.getId(), listing, score.getDiscountPercent(),
				score.getDealScore(), score.getRiskScore(), score.getConfidence(), score.getFlags(),
				score.getExplanation(), score.getComputedAt(), marketPrice, comparables,
				marketPrice.outlierIds(), listingQueries.priceHistory(listing.id()),
				"CURRENT_MATCHED_LISTINGS"));
	}

	@Transactional(readOnly = true)
	public long count() {
		return list(0, 100).getTotalElements();
	}

	private List<DealScore> latestScores() {
		Map<UUID, DealScore> latestByListing = new HashMap<>();
		dealScoreRepository.findAllByOrderByComputedAtDescIdDesc().forEach(score ->
				latestByListing.putIfAbsent(score.getListingId(), score));
		return new ArrayList<>(latestByListing.values());
	}

	private static DealCard toCard(DealScore score, ListingView listing) {
		return new DealCard(score.getId(), listing, score.getDiscountPercent(), score.getDealScore(),
				score.getRiskScore(), score.getConfidence(), score.getFlags(), score.getExplanation(),
				score.getComputedAt());
	}

	private static void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > 100) {
			throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
		}
	}
}
