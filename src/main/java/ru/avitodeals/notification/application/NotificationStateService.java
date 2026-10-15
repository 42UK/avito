package ru.avitodeals.notification.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.notification.domain.Notification;
import ru.avitodeals.notification.infrastructure.persistence.NotificationRepository;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationStateService {

	private final NotificationRepository repository;

	public NotificationStateService(NotificationRepository repository) {
		this.repository = repository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void complete(UUID notificationId, NotificationResult result) {
		Notification notification = repository.findById(notificationId)
				.orElseThrow(() -> new IllegalStateException("Claimed notification no longer exists"));
		if (!"SENDING".equals(notification.getStatus())) {
			throw new IllegalStateException("Notification is no longer in SENDING state");
		}
		notification.setStatus(result.status().name());
		notification.setSentAt(result.status() == NotificationResult.Status.SENT ? Instant.now() : null);
		notification.setErrorCode(result.errorCode());
		notification.setErrorMessage(result.errorCode() == null ? null : safeErrorMessage(result.errorCode()));
		repository.save(notification);
	}

	private static String safeErrorMessage(String errorCode) {
		return switch (errorCode) {
			case "TELEGRAM_NOT_CONFIGURED" -> "Telegram bot token or chat ID is not configured.";
			case "TELEGRAM_REJECTED" -> "Telegram rejected the message.";
			case "TELEGRAM_HTTP_ERROR" -> "Telegram returned an HTTP error.";
			case "TELEGRAM_HTTP_OUTCOME_UNKNOWN" -> "Telegram returned a server error; delivery outcome is unknown.";
			case "TELEGRAM_TRANSPORT_ERROR" -> "Telegram delivery outcome is unknown; automatic retry is disabled.";
			case "CHANNEL_UNAVAILABLE" -> "No adapter is registered for this notification channel.";
			default -> "Notification delivery failed.";
		};
	}
}
