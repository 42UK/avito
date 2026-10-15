package ru.avitodeals.ingestion.application;

import java.util.UUID;

public record IngestionRequest(
		SearchCriteria criteria,
		UUID searchId,
		boolean authoritativeSourceSnapshot) {

	public IngestionRequest {
		if (criteria == null) {
			throw new IllegalArgumentException("Search criteria are required");
		}
	}

	public static IngestionRequest forSearch(SearchCriteria criteria, UUID searchId) {
		return new IngestionRequest(criteria, searchId, false);
	}

	public static IngestionRequest fullSourceSnapshot() {
		return new IngestionRequest(SearchCriteria.all(), null, true);
	}
}
