package ru.avitodeals.search.application;

import java.math.BigDecimal;
import java.util.Map;

public record SearchCommand(
		String name,
		String query,
		Map<String, Object> filters,
		int intervalMinutes,
		BigDecimal minDealScore,
		boolean active) {
}
