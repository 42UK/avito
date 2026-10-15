package ru.avitodeals.ingestion.application;

public record IngestionReport(
		int processed,
		int duplicates,
		int created,
		int priceChanges,
		int removed) {
}
