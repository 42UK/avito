package ru.avitodeals.product.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.product.domain.MatchStatus;
import ru.avitodeals.product.infrastructure.persistence.ListingProductMatchRepository;

import java.util.List;
import java.util.UUID;

@Service
public class ProductQueryService implements ProductQueries {

	private final ListingProductMatchRepository repository;

	public ProductQueryService(ListingProductMatchRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional(readOnly = true)
	public List<UUID> matchedListingIds(UUID productId) {
		return repository.findByProductIdAndStatus(productId, MatchStatus.MATCHED).stream()
				.map(match -> match.getListingId()).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ProductMatchResult> matchedListings(UUID productId) {
		return repository.findByProductIdAndStatus(productId, MatchStatus.MATCHED).stream()
				.map(match -> new ProductMatchResult(match.getListingId(), match.getProductId(),
						match.getConfidence(), match.getStatus(), match.getMatcherVersion()))
				.toList();
	}
}
