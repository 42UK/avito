package ru.avitodeals.pricing.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.pricing.domain.ConditionGroup;
import ru.avitodeals.pricing.domain.MarketPrice;
import ru.avitodeals.pricing.infrastructure.persistence.MarketPriceRepository;

import java.time.Instant;
import java.util.Objects;

@Service
public class MarketPriceService {

	private final MarketPriceCalculator calculator;
	private final MarketPriceRepository repository;

	public MarketPriceService(MarketPriceCalculator calculator, MarketPriceRepository repository) {
		this.calculator = calculator;
		this.repository = repository;
	}

	@Transactional
	public MarketPriceResult calculateAndSave(ComparableListings comparables) {
		return calculateAndSave(comparables, Instant.now());
	}

	@Transactional
	public MarketPriceResult calculateAndSave(ComparableListings comparables, Instant computedAt) {
		MarketPriceResult result = calculator.calculate(comparables, computedAt);
		repository.save(toEntity(result, comparables.condition()));
		return result;
	}

	@Transactional
	public PersistedMarketPrice calculateAndSaveSnapshot(ComparableListings comparables) {
		return calculateAndSaveSnapshot(comparables, Instant.now());
	}

	@Transactional
	public PersistedMarketPrice calculateAndSaveSnapshot(ComparableListings comparables, Instant computedAt) {
		MarketPriceResult result = calculator.calculate(comparables, computedAt);
		MarketPrice snapshot = repository.save(toEntity(result, comparables.condition()));
		return new PersistedMarketPrice(snapshot.getId(), comparables.condition(), result);
	}

	private static MarketPrice toEntity(MarketPriceResult result, ConditionGroup condition) {
		MarketPrice snapshot = new MarketPrice();
		snapshot.setProductId(result.productId());
		snapshot.setConditionGroup(condition);
		snapshot.setMarketPrice(result.marketPrice());
		snapshot.setMedian(result.median());
		snapshot.setP25(result.p25());
		snapshot.setP75(result.p75());
		snapshot.setCurrency(result.currency());
		snapshot.setSampleSize(result.sampleSize());
		snapshot.setConfidenceScore(result.confidenceScore());
		snapshot.setConfidenceLevel(result.confidenceLevel());
		snapshot.setOutliers(result.outliers());
		snapshot.setOutliersCount(result.outliersCount());
		snapshot.setMethod(result.method());
		snapshot.setComputedAt(Objects.requireNonNull(result.computedAt(), "computedAt"));
		return snapshot;
	}
}
