package ru.avitodeals.product.application;

import java.util.Set;

public record ProductNormalization(
		String canonicalKey,
		String brand,
		String model,
		String category,
		ProductType type,
		ExtractedAttributes attributes,
		Set<String> unresolvedAttributes) {

	public ProductNormalization {
		unresolvedAttributes = Set.copyOf(unresolvedAttributes);
	}

	public boolean hasCanonicalKey() {
		return canonicalKey != null;
	}
}
