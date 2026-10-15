package ru.avitodeals.product.application;

import java.util.Map;
import java.util.Set;

public record ExtractedAttributes(
		Map<String, String> values,
		ExtractedCondition condition,
		boolean accessory,
		boolean forParts,
		boolean defective,
		Set<String> defects,
		Set<String> unresolvedAttributes) {

	public ExtractedAttributes {
		values = Map.copyOf(values);
		defects = Set.copyOf(defects);
		unresolvedAttributes = Set.copyOf(unresolvedAttributes);
	}
}
