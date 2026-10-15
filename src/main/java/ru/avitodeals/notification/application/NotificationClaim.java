package ru.avitodeals.notification.application;

import java.util.UUID;

public record NotificationClaim(
		boolean claimed,
		UUID notificationId,
		NotificationResult.Status existingStatus,
		String errorCode) {
}
