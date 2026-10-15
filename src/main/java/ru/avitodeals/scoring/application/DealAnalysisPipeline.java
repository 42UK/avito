package ru.avitodeals.scoring.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.listing.application.ListingQueries;
import ru.avitodeals.listing.application.ListingView;
import ru.avitodeals.pricing.application.ComparableListing;
import ru.avitodeals.pricing.application.ComparableListings;
import ru.avitodeals.pricing.application.MarketPriceService;
import ru.avitodeals.pricing.application.PersistedMarketPrice;
import ru.avitodeals.product.application.AttributeExtractor;
import ru.avitodeals.product.application.ExtractedAttributes;
import ru.avitodeals.product.application.ExtractedCondition;
import ru.avitodeals.product.application.ProductMatchResult;
import ru.avitodeals.product.application.ProductMatcher;
import ru.avitodeals.product.application.ProductQueries;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DealAnalysisPipeline {

	private final ListingQueries listingQueries;
	private final ProductMatcher productMatcher;
	private final ProductQueries productQueries;
	private final AttributeExtractor attributeExtractor;
	private final MarketPriceService marketPriceService;
	private final DealScoringService dealScoringService;

	public DealAnalysisPipeline(
			ListingQueries listingQueries,
			ProductMatcher productMatcher,
			ProductQueries productQueries,
			AttributeExtractor attributeExtractor,
			MarketPriceService marketPriceService,
			DealScoringService dealScoringService) {
		this.listingQueries = listingQueries;
		this.productMatcher = productMatcher;
		this.productQueries = productQueries;
		this.attributeExtractor = attributeExtractor;
		this.marketPriceService = marketPriceService;
		this.dealScoringService = dealScoringService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void analyze(UUID listingId) {
		ListingView candidate = listingQueries.find(listingId).orElse(null);
		if (candidate == null || !candidate.status().equals("ACTIVE")) {
			return;
		}

		ProductMatchResult productMatch = productMatcher.match(
				candidate.id(), candidate.title(), candidate.description());
		if (!productMatch.statusCode().equals("MATCHED")
				|| productMatch.productId() == null) {
			return;
		}

		Map<UUID, ProductMatchResult> productMatches = productQueries.matchedListings(productMatch.productId()).stream()
				.collect(Collectors.toMap(ProductMatchResult::listingId, match -> match));
		productMatches.put(candidate.id(), productMatch);
		ExtractedAttributes candidateAttributes = extractAttributes(candidate);
		List<AnalyzableListing> sameMarketListings = listingQueries.findByIds(productMatches.keySet()).stream()
				.filter(listing -> listing.status().equals("ACTIVE"))
				.filter(listing -> listing.source().equals(candidate.source()))
				.filter(listing -> listing.dataOrigin() == candidate.dataOrigin())
				.filter(listing -> listing.currency().equals(candidate.currency()))
				.map(listing -> new AnalyzableListing(listing, productMatches.get(listing.id()),
						extractAttributes(listing)))
				.filter(listing -> listing.attributes().condition() == candidateAttributes.condition())
				.toList();

		for (AnalyzableListing analyzedListing : sameMarketListings) {
			score(analyzedListing, sameMarketListings, productMatch.productId());
		}
	}

	private void score(
			AnalyzableListing candidate,
			List<AnalyzableListing> sameMarketListings,
			UUID productId) {
		List<ComparableListing> comparables = sameMarketListings.stream()
				.map(listing -> listing.listing())
				.map(listing -> ComparableListing.withConditionCode(
						listing.id(), productId, candidate.attributes().condition().name(),
						listing.currency(), listing.price(), true))
				.toList();
		String conditionCode = candidate.attributes().condition().name();
		ListingView listing = candidate.listing();
		ComparableListings request = ComparableListings.withConditionCode(
				listing.id(), productId, conditionCode, listing.currency(), comparables);
		PersistedMarketPrice marketPrice = marketPriceService.calculateAndSaveSnapshot(request);
		dealScoringService.scoreAndSave(new DealScoringInput(listing.id(), listing.price(),
				listing.currency(), conditionCode, marketPrice, candidate.productMatch(), candidate.attributes()));
	}

	private ExtractedAttributes extractAttributes(ListingView listing) {
		ExtractedCondition declaredCondition = ExtractedCondition.valueOf(listing.condition());
		return attributeExtractor.extract(listing.title(), listing.description(), declaredCondition);
	}

	private record AnalyzableListing(
			ListingView listing,
			ProductMatchResult productMatch,
			ExtractedAttributes attributes) {
	}
}
