package ru.avitodeals.notification.application;

import org.junit.jupiter.api.Test;
import ru.avitodeals.notification.domain.Notification;
import ru.avitodeals.notification.infrastructure.persistence.NotificationRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationStateServiceTests {

	private static final UUID NOTIFICATION_ID = UUID.randomUUID();
	private final NotificationRepository repository = mock(NotificationRepository.class);
	private final NotificationStateService service = new NotificationStateService(repository);

	@Test
	void persistsSentTimestampAfterSuccessfulDelivery() {
		Notification notification = sendingNotification();
		when(repository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(notification));

		service.complete(NOTIFICATION_ID, new NotificationResult(
				NOTIFICATION_ID, NotificationResult.Status.SENT, null));

		assertEquals("SENT", notification.getStatus());
		assertNotNull(notification.getSentAt());
		assertNull(notification.getErrorCode());
		verify(repository).save(notification);
	}

	@Test
	void persistsSafeFailureStatusAndMessageWithoutPretendingDeliverySucceeded() {
		Notification notification = sendingNotification();
		when(repository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(notification));

		service.complete(NOTIFICATION_ID, new NotificationResult(
				NOTIFICATION_ID, NotificationResult.Status.FAILED, "TELEGRAM_REJECTED"));

		assertEquals("FAILED", notification.getStatus());
		assertNull(notification.getSentAt());
		assertEquals("TELEGRAM_REJECTED", notification.getErrorCode());
		assertEquals("Telegram rejected the message.", notification.getErrorMessage());
		verify(repository).save(notification);
	}

	private static Notification sendingNotification() {
		Notification notification = Notification.create(UUID.randomUUID(), "TELEGRAM", "dedup-key");
		notification.setId(NOTIFICATION_ID);
		notification.setStatus("SENDING");
		notification.setAttemptCount(1);
		return notification;
	}
}
