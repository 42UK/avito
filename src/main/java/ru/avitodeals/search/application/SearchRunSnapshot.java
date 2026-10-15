package ru.avitodeals.search.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SearchRunSnapshot(
		UUID id, String name, String query, Map<String, Object> filters, Instant claimedAt) {

	public SearchRunSnapshot {
		filters = filters == null ? Map.of() : Map.copyOf(filters);
	}
}
