package ru.avitodeals.ingestion.infrastructure.avito;

import ru.avitodeals.ingestion.application.ListingSourceException;

import java.time.Duration;

public class AvitoFetchException extends ListingSourceException {

	public AvitoFetchException(String message) {
		super(message);
	}

	public AvitoFetchException(String message, Throwable cause) {
		super(message, cause);
	}

	public AvitoFetchException(String message, int statusCode) {
		this(message, statusCode, Duration.ZERO, false);
	}

	public AvitoFetchException(
			String message, int statusCode, Duration retryAfter, boolean deferredByRateLimit) {
		super(message, statusCode, retryAfter, deferredByRateLimit);
	}
}
