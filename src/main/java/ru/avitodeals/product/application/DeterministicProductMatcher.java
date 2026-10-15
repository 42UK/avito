package ru.avitodeals.product.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.product.domain.ListingProductMatch;
import ru.avitodeals.product.domain.MatchStatus;
import ru.avitodeals.product.domain.Product;
import ru.avitodeals.product.domain.ProductAttribute;
import ru.avitodeals.product.domain.ProductAttributeId;
import ru.avitodeals.product.infrastructure.persistence.ListingProductMatchRepository;
import ru.avitodeals.product.infrastructure.persistence.ProductAttributeRepository;
import ru.avitodeals.product.infrastructure.persistence.ProductRepository;
import ru.avitodeals.shared.application.TransactionScopedKeyLock;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DeterministicProductMatcher implements ProductMatcher {

	public static final String MATCHER_VERSION = "deterministic-v1";
	private static final BigDecimal EXACT_MATCH_CONFIDENCE = new BigDecimal("0.9500");
	private static final BigDecimal NO_MATCH_CONFIDENCE = new BigDecimal("0.0000");

	private final ProductRepository productRepository;
	private final ProductAttributeRepository productAttributeRepository;
	private final ListingProductMatchRepository matchRepository;
	private final ProductNormalizer normalizer;
	private final ProductClassifier classifier;
	private final TransactionScopedKeyLock keyLock;

	public DeterministicProductMatcher(
			ProductRepository productRepository,
			ProductAttributeRepository productAttributeRepository,
			ListingProductMatchRepository matchRepository,
			ProductNormalizer normalizer,
			ProductClassifier classifier,
			TransactionScopedKeyLock keyLock) {
		this.productRepository = productRepository;
		this.productAttributeRepository = productAttributeRepository;
		this.matchRepository = matchRepository;
		this.normalizer = normalizer;
		this.classifier = classifier;
		this.keyLock = keyLock;
	}

	@Override
	@Transactional
	public ProductMatchResult match(UUID listingId, String title, String description) {
		if (listingId == null) {
			throw new IllegalArgumentException("Listing ID is required");
		}
		ProductNormalization normalization = normalizer.normalize(title, description);
		ProductClassification classification = classifier.classify(normalization);

		if (classification.status() == MatchStatus.MATCHED) {
			keyLock.acquireAll("product-key:" + normalization.canonicalKey(),
					"listing-match:" + listingId);
		} else {
			keyLock.acquire("listing-match:" + listingId);
		}

		UUID productId = null;
		BigDecimal confidence = NO_MATCH_CONFIDENCE;
		if (classification.status() == MatchStatus.MATCHED) {
			Product product = findOrCreateProduct(normalization);
			productId = product.getId();
			persistStableAttributes(product, normalization);
			confidence = EXACT_MATCH_CONFIDENCE;
		}

		ListingProductMatch match = new ListingProductMatch(
				listingId, productId, confidence, MATCHER_VERSION, classification.status());
		matchRepository.save(match);

		return new ProductMatchResult(listingId, productId, confidence,
				classification.status(), MATCHER_VERSION);
	}

	private Product findOrCreateProduct(ProductNormalization normalization) {
		return productRepository.findByCanonicalKey(normalization.canonicalKey())
				.orElseGet(() -> {
					Product product = new Product(normalization.canonicalKey(), normalization.brand(),
							normalization.model(), normalization.category());
					return productRepository.save(product);
				});
	}

	private void persistStableAttributes(Product product, ProductNormalization normalization) {
		String storage = normalization.attributes().values().get("storage_gb");
		if (storage == null) {
			return;
		}
		Set<String> existingKeys = productAttributeRepository.findByIdProductId(product.getId()).stream()
				.map(attribute -> attribute.getId().getKey())
				.collect(Collectors.toSet());
		if (!existingKeys.contains("storage_gb")) {
			ProductAttribute attribute = new ProductAttribute(
					new ProductAttributeId(product.getId(), "storage_gb"), storage);
			productAttributeRepository.save(attribute);
		}
	}
}
