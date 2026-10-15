package ru.avitodeals.ingestion.application;

import java.time.Duration;

public class ListingSourceException extends RuntimeException {

	private final int statusCode;
	private final Duration retryAfter;
	private final boolean deferredByRateLimit;

	public ListingSourceException(String message) {
		super(message);
		this.statusCode = -1;
		this.retryAfter = Duration.ZERO;
		this.deferredByRateLimit = false;
	}

	public ListingSourceException(String message, Throwable cause) {
		super(message, cause);
		this.statusCode = -1;
		this.retryAfter = Duration.ZERO;
		this.deferredByRateLimit = false;
	}

	public ListingSourceException(
			String message, int statusCode, Duration retryAfter, boolean deferredByRateLimit) {
		super(message);
		this.statusCode = statusCode;
		this.retryAfter = retryAfter;
		this.deferredByRateLimit = deferredByRateLimit;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public Duration getRetryAfter() {
		return retryAfter;
	}

	public boolean isDeferredByRateLimit() {
		return deferredByRateLimit;
	}
}
