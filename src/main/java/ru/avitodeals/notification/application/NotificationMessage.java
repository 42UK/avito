package ru.avitodeals.notification.application;

import java.util.Objects;
import java.util.UUID;

public record NotificationMessage(
		UUID dealScoreId,
		String dedupKey,
		String channel,
		UUID listingId,
		String title,
		String body,
		String url) {

	public NotificationMessage {
		Objects.requireNonNull(dealScoreId, "dealScoreId");
		Objects.requireNonNull(listingId, "listingId");
		if (blankOrTooLong(dedupKey, 512) || blankOrTooLong(channel, 32)
				|| title == null || title.isBlank()
				|| body == null || body.isBlank()
				|| url == null || url.isBlank()) {
			throw new IllegalArgumentException("Notification dedup key, channel, title, body and URL are required");
		}
	}

	private static boolean blankOrTooLong(String value, int maximumLength) {
		return value == null || value.isBlank() || value.length() > maximumLength;
	}
}
