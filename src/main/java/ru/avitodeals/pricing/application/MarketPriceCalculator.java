package ru.avitodeals.pricing.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.avitodeals.pricing.domain.ConfidenceLevel;
import ru.avitodeals.pricing.domain.ConditionGroup;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class MarketPriceCalculator {

	private static final int DEFAULT_MEDIUM_CONFIDENCE_SAMPLE = 4;
	private static final int DEFAULT_HIGH_CONFIDENCE_SAMPLE = 5;
	private static final BigDecimal QUARTER = new BigDecimal("0.25");
	private static final BigDecimal THREE_QUARTERS = new BigDecimal("0.75");
	private static final BigDecimal IQR_FENCE_MULTIPLIER = new BigDecimal("1.5");
	private static final BigDecimal MAD_Z_SCORE_CUTOFF = new BigDecimal("3.5");
	private static final BigDecimal MAD_NORMALIZATION = new BigDecimal("0.6745");
	private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
	private static final String METHOD = "MEDIAN_MAD_IQR_LINEAR_INTERPOLATION";
	private static final Comparator<ComparableListing> PRICE_ORDER =
			Comparator.comparing(ComparableListing::amount).thenComparing(ComparableListing::id);

	private final int minimumMediumConfidenceSample;
	private final int minimumHighConfidenceSample;

	public MarketPriceCalculator() {
		this(DEFAULT_MEDIUM_CONFIDENCE_SAMPLE, DEFAULT_HIGH_CONFIDENCE_SAMPLE);
	}

	@Autowired
	public MarketPriceCalculator(
			@Value("${avito-deals.pricing.confidence.medium-min-sample-size:4}") int minimumMediumConfidenceSample,
			@Value("${avito-deals.pricing.confidence.high-min-sample-size:5}") int minimumHighConfidenceSample) {
		if (minimumMediumConfidenceSample < 1 || minimumHighConfidenceSample <= minimumMediumConfidenceSample) {
			throw new IllegalArgumentException("Confidence sample thresholds must satisfy 1 <= medium < high");
		}
		this.minimumMediumConfidenceSample = minimumMediumConfidenceSample;
		this.minimumHighConfidenceSample = minimumHighConfidenceSample;
	}

	public MarketPriceResult calculate(ComparableListings request, Instant computedAt) {
		List<ComparableListing> eligible = eligibleListings(request);
		Set<UUID> outlierIds = findOutliers(eligible);
		List<ComparableListing> included = eligible.stream()
				.filter(listing -> !outlierIds.contains(listing.id()))
				.sorted(PRICE_ORDER)
				.toList();
		List<UUID> outliers = outlierIds.stream().sorted().toList();

		BigDecimal median = percentile(included, new BigDecimal("0.5"));
		BigDecimal p25 = percentile(included, QUARTER);
		BigDecimal p75 = percentile(included, THREE_QUARTERS);
		int sampleSize = included.size();
		BigDecimal confidenceScore = BigDecimal.valueOf(sampleSize)
				.divide(BigDecimal.valueOf(minimumHighConfidenceSample), 4, RoundingMode.HALF_UP)
				.min(BigDecimal.ONE);
		ConfidenceLevel confidenceLevel = sampleSize >= minimumHighConfidenceSample
				? ConfidenceLevel.HIGH
				: sampleSize >= minimumMediumConfidenceSample ? ConfidenceLevel.MEDIUM : ConfidenceLevel.LOW;

		return new MarketPriceResult(request.productId(), median, request.currency(), median, p25, p75,
				sampleSize, confidenceScore, confidenceLevel, outliers, outliers.size(), METHOD, computedAt);
	}

	private static List<ComparableListing> eligibleListings(ComparableListings request) {
		List<ComparableListing> eligible = new ArrayList<>();
		Set<UUID> seenIds = new HashSet<>();
		for (ComparableListing listing : request.listings()) {
			if (!seenIds.add(listing.id())) {
				throw new IllegalArgumentException("Duplicate comparable listing ID: " + listing.id());
			}
			if (!listing.id().equals(request.candidateId())
					&& listing.productId().equals(request.productId())
					&& listing.condition() == request.condition()
					&& listing.currency().equals(request.currency())
					&& listing.active()) {
				eligible.add(listing);
			}
		}
		eligible.sort(PRICE_ORDER);
		return eligible;
	}

	private static Set<UUID> findOutliers(List<ComparableListing> listings) {
		if (listings.size() < 4) {
			return Set.of();
		}
		BigDecimal median = percentile(listings, new BigDecimal("0.5"));
		List<ComparableListing> deviations = listings.stream()
				.map(listing -> new ComparableListing(
						listing.id(), listing.productId(), listing.condition(), listing.currency(),
						listing.amount().subtract(median).abs(), listing.active()))
				.sorted(PRICE_ORDER)
				.toList();
		BigDecimal mad = percentile(deviations, new BigDecimal("0.5"));
		BigDecimal q1 = percentile(listings, QUARTER);
		BigDecimal q3 = percentile(listings, THREE_QUARTERS);
		BigDecimal iqr = q3.subtract(q1);
		BigDecimal lowerFence = q1.subtract(iqr.multiply(IQR_FENCE_MULTIPLIER));
		BigDecimal upperFence = q3.add(iqr.multiply(IQR_FENCE_MULTIPLIER));
		BigDecimal madFence = mad.signum() == 0 ? null
				: mad.multiply(MAD_Z_SCORE_CUTOFF).divide(MAD_NORMALIZATION, MathContext.DECIMAL128);

		Set<UUID> outlierIds = new HashSet<>();
		for (ComparableListing listing : listings) {
			BigDecimal deviation = listing.amount().subtract(median).abs();
			boolean outsideIqr = listing.amount().compareTo(lowerFence) < 0
					|| listing.amount().compareTo(upperFence) > 0;
			boolean outsideMad = madFence != null && deviation.compareTo(madFence) > 0;
			boolean zeroDispersionOutlier = mad.signum() == 0 && iqr.signum() == 0
					&& listing.amount().compareTo(median) != 0;
			if (outsideIqr || outsideMad || zeroDispersionOutlier) {
				outlierIds.add(listing.id());
			}
		}

		int maxOutliers = (listings.size() - 1) / 2;
		if (outlierIds.size() > maxOutliers) {
			return listings.stream()
					.filter(listing -> outlierIds.contains(listing.id()))
					.sorted(Comparator.comparing((ComparableListing listing) ->
							listing.amount().subtract(median).abs()).thenComparing(ComparableListing::id))
					.skip(outlierIds.size() - maxOutliers)
					.map(ComparableListing::id)
					.collect(java.util.stream.Collectors.toSet());
		}
		return outlierIds;
	}

	private static BigDecimal percentile(List<ComparableListing> listings, BigDecimal quantile) {
		if (listings.isEmpty()) {
			return ZERO;
		}
		List<ComparableListing> sorted = listings.stream().sorted(PRICE_ORDER).toList();
		BigDecimal position = BigDecimal.valueOf(sorted.size() - 1L).multiply(quantile);
		int lowerIndex = position.intValue();
		int upperIndex = Math.min(lowerIndex + 1, sorted.size() - 1);
		BigDecimal fraction = position.subtract(BigDecimal.valueOf(lowerIndex));
		BigDecimal value = sorted.get(lowerIndex).amount().multiply(BigDecimal.ONE.subtract(fraction))
				.add(sorted.get(upperIndex).amount().multiply(fraction));
		return value.setScale(2, RoundingMode.HALF_UP);
	}
}
