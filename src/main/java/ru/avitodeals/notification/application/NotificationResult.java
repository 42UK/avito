package ru.avitodeals.notification.application;

import java.util.UUID;

public record NotificationResult(UUID notificationId, Status status, String errorCode) {

	public enum Status {
		SENT,
		DUPLICATE,
		SKIPPED,
		FAILED,
		UNCERTAIN
	}

	public boolean successful() {
		return status == Status.SENT || status == Status.DUPLICATE;
	}
}
