package ru.avitodeals.pricing.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.pricing.domain.MarketPrice;
import ru.avitodeals.pricing.infrastructure.persistence.MarketPriceRepository;

import java.util.Optional;
import java.util.UUID;

@Service
public class MarketPriceQueryService implements MarketPriceQueries {

	private final MarketPriceRepository repository;

	public MarketPriceQueryService(MarketPriceRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<MarketPriceView> find(UUID id) {
		return repository.findById(id).map(MarketPriceQueryService::toView);
	}

	private static MarketPriceView toView(MarketPrice snapshot) {
		return new MarketPriceView(snapshot.getId(), snapshot.getProductId(),
				snapshot.getConditionGroup().name(), snapshot.getMarketPrice(), snapshot.getCurrency(),
				snapshot.getMedian(), snapshot.getP25(), snapshot.getP75(), snapshot.getSampleSize(),
				snapshot.getConfidenceScore(), snapshot.getConfidenceLevel().name(), snapshot.getOutliers(),
				snapshot.getMethod(), snapshot.getComputedAt());
	}
}
