package ru.avitodeals.product.application;

import org.springframework.stereotype.Component;
import ru.avitodeals.product.domain.MatchStatus;

import java.util.Objects;

@Component
public final class DeterministicProductClassifier implements ProductClassifier {

	@Override
	public ProductClassification classify(ProductNormalization normalization) {
		Objects.requireNonNull(normalization, "normalization");

		if (normalization.type() != ProductType.PRODUCT) {
			return new ProductClassification(MatchStatus.UNMATCHED, normalization);
		}
		if (!normalization.unresolvedAttributes().isEmpty()) {
			return new ProductClassification(MatchStatus.AMBIGUOUS, normalization);
		}
		if (normalization.canonicalKey() == null || normalization.canonicalKey().isBlank()) {
			return new ProductClassification(MatchStatus.UNMATCHED, normalization);
		}
		return new ProductClassification(MatchStatus.MATCHED, normalization);
	}
}
