package ru.avitodeals.notification.application;

import org.junit.jupiter.api.Test;
import ru.avitodeals.notification.infrastructure.persistence.NotificationRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationApplicationServiceTests {

	private static final UUID NOTIFICATION_ID = UUID.randomUUID();
	private final NotificationClaimService claimService = mock(NotificationClaimService.class);
	private final NotificationStateService stateService = mock(NotificationStateService.class);
	private final NotificationRepository repository = mock(NotificationRepository.class);
	private final NotificationChannel channel = mock(NotificationChannel.class);
	private final NotificationApplicationService service =
			new NotificationApplicationService(claimService, stateService, repository, List.of(channel));

	@Test
	void persistsSuccessfulDeliveryAndReturnsItsNotificationId() {
		NotificationMessage message = message();
		when(claimService.claim(message)).thenReturn(new NotificationClaim(true, NOTIFICATION_ID, null, null));
		when(channel.channel()).thenReturn("TELEGRAM");
		when(channel.deliver(message)).thenReturn(
				new NotificationResult(null, NotificationResult.Status.SENT, null));

		NotificationResult result = service.send(message);

		assertEquals(NotificationResult.Status.SENT, result.status());
		assertEquals(NOTIFICATION_ID, result.notificationId());
		verify(stateService).complete(NOTIFICATION_ID, result);
	}

	@Test
	void persistsFailureAndDoesNotReportItAsSuccess() {
		NotificationMessage message = message();
		when(claimService.claim(message)).thenReturn(new NotificationClaim(true, NOTIFICATION_ID, null, null));
		when(channel.channel()).thenReturn("TELEGRAM");
		when(channel.deliver(message)).thenReturn(
				new NotificationResult(null, NotificationResult.Status.FAILED, "TELEGRAM_REJECTED"));

		NotificationResult result = service.send(message);

		assertEquals(NotificationResult.Status.FAILED, result.status());
		org.junit.jupiter.api.Assertions.assertFalse(result.successful());
		verify(stateService).complete(NOTIFICATION_ID, result);
	}

	@Test
	void duplicateOrConcurrentClaimDoesNotSendAgain() {
		NotificationMessage message = message();
		when(claimService.claim(message)).thenReturn(new NotificationClaim(
				false, NOTIFICATION_ID, NotificationResult.Status.DUPLICATE, null));

		NotificationResult result = service.send(message);

		assertEquals(NotificationResult.Status.DUPLICATE, result.status());
		verify(channel, never()).deliver(message);
		verify(stateService, never()).complete(NOTIFICATION_ID, result);
	}

	@Test
	void missingChannelIsPersistedAsAFailure() {
		NotificationMessage message = message();
		when(claimService.claim(message)).thenReturn(new NotificationClaim(true, NOTIFICATION_ID, null, null));
		NotificationApplicationService withoutChannels =
				new NotificationApplicationService(claimService, stateService, repository, List.of());

		NotificationResult result = withoutChannels.send(message);

		assertEquals(NotificationResult.Status.FAILED, result.status());
		assertEquals("CHANNEL_UNAVAILABLE", result.errorCode());
		verify(stateService).complete(NOTIFICATION_ID, result);
	}

	private static NotificationMessage message() {
		return new NotificationMessage(UUID.randomUUID(), "dedup-key", "TELEGRAM",
				UUID.randomUUID(), "Title", "Body", "https://example.test");
	}
}
