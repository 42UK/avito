package ru.avitodeals.listing.application;

import java.util.Objects;
import java.util.UUID;

public record ListingNotificationDetails(UUID listingId, String title, String url) {

	public ListingNotificationDetails {
		Objects.requireNonNull(listingId, "listingId");
		if (title == null || title.isBlank() || url == null || url.isBlank()) {
			throw new IllegalArgumentException("Listing title and URL are required for a notification");
		}
	}
}
