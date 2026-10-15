package ru.avitodeals.ingestion.application;

import java.util.Map;

public record SearchCriteria(String query, Map<String, Object> filters) {

	public SearchCriteria {
		query = query == null ? "" : query.trim();
		filters = filters == null ? Map.of() : Map.copyOf(filters);
	}

	public static SearchCriteria all() {
		return new SearchCriteria("", Map.of());
	}
}
