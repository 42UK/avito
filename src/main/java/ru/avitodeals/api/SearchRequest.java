package ru.avitodeals.api;

import ru.avitodeals.search.application.SearchCommand;

import java.math.BigDecimal;
import java.util.Map;

public record SearchRequest(
		String name,
		String query,
		Map<String, Object> filters,
		int intervalMinutes,
		BigDecimal minDealScore,
		Boolean active) {

	public SearchCommand toCommand() {
		if (active == null) {
			throw new IllegalArgumentException("active is required");
		}
		return new SearchCommand(name, query, filters, intervalMinutes, minDealScore, active);
	}
}
