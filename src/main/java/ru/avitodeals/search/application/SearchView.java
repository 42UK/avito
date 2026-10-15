package ru.avitodeals.search.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SearchView(
		UUID id,
		String name,
		String query,
		Map<String, Object> filters,
		int intervalMinutes,
		BigDecimal minDealScore,
		boolean active,
		Instant lastRunAt) {

	public SearchView {
		filters = filters == null ? Map.of() : Map.copyOf(filters);
	}
}
