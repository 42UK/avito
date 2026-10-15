package ru.avitodeals.notification.application;

import org.junit.jupiter.api.Test;
import ru.avitodeals.notification.domain.Notification;
import ru.avitodeals.notification.infrastructure.persistence.NotificationRepository;
import ru.avitodeals.shared.application.TransactionScopedKeyLock;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationClaimServiceTests {

	private static final UUID SCORE_ID = UUID.randomUUID();
	private static final UUID LISTING_ID = UUID.randomUUID();
	private static final UUID NOTIFICATION_ID = UUID.randomUUID();
	private final NotificationRepository repository = mock(NotificationRepository.class);
	private final TransactionScopedKeyLock keyLock = mock(TransactionScopedKeyLock.class);
	private final NotificationClaimService service = new NotificationClaimService(repository, keyLock, 3);

	@Test
	void claimsNewNotificationAndUsesChannelAndDedupKeyForLockAndLookup() {
		when(repository.saveAndFlush(any(Notification.class))).thenAnswer(invocation -> {
			Notification notification = invocation.getArgument(0);
			notification.setId(NOTIFICATION_ID);
			return notification;
		});

		NotificationClaim claim = service.claim(message());

		assertTrue(claim.claimed());
		assertEquals(NOTIFICATION_ID, claim.notificationId());
		verify(repository).findByChannelAndDedupKeyForUpdate("TELEGRAM", "deal-score:" + SCORE_ID);
		verify(keyLock).acquire("notification:TELEGRAM:deal-score:" + SCORE_ID);
		verify(repository).saveAndFlush(any(Notification.class));
	}

	@Test
	void sentNotificationIsDeduplicatedAndNeverClaimedAgain() {
		Notification sent = existing("SENT", 1);
		when(repository.findByChannelAndDedupKeyForUpdate("TELEGRAM", message().dedupKey()))
				.thenReturn(Optional.of(sent));

		NotificationClaim claim = service.claim(message());

		assertFalse(claim.claimed());
		assertEquals(NotificationResult.Status.DUPLICATE, claim.existingStatus());
		assertEquals(NOTIFICATION_ID, claim.notificationId());
	}

	@Test
	void failedDeliveryMayBeRetriedUpToTheConfiguredAttemptLimit() {
		Notification failed = existing("FAILED", 1);
		when(repository.findByChannelAndDedupKeyForUpdate("TELEGRAM", message().dedupKey()))
				.thenReturn(Optional.of(failed));
		when(repository.saveAndFlush(failed)).thenReturn(failed);

		NotificationClaim claim = service.claim(message());

		assertTrue(claim.claimed());
		assertEquals("SENDING", failed.getStatus());
		assertEquals(2, failed.getAttemptCount());
		assertNull(failed.getErrorCode());
		verify(repository).saveAndFlush(failed);
	}

	@Test
	void uncertainAndInProgressDeliveriesAreNotRepeatedAndAttemptsAreBounded() {
		Notification uncertain = existing("UNCERTAIN", 1);
		Notification inProgress = existing("SENDING", 1);
		Notification exhausted = existing("FAILED", 3);
		when(repository.findByChannelAndDedupKeyForUpdate("TELEGRAM", message().dedupKey()))
				.thenReturn(Optional.of(uncertain))
				.thenReturn(Optional.of(inProgress))
				.thenReturn(Optional.of(exhausted));
		NotificationClaim uncertainClaim = service.claim(message());
		assertFalse(uncertainClaim.claimed());
		assertEquals(NotificationResult.Status.UNCERTAIN, uncertainClaim.existingStatus());

		NotificationClaim inProgressClaim = service.claim(message());
		assertFalse(inProgressClaim.claimed());
		assertEquals(NotificationResult.Status.UNCERTAIN, inProgressClaim.existingStatus());
		assertEquals("DELIVERY_IN_PROGRESS", inProgressClaim.errorCode());

		NotificationClaim exhaustedClaim = service.claim(message());
		assertFalse(exhaustedClaim.claimed());
		assertEquals("MAX_ATTEMPTS_REACHED", exhaustedClaim.errorCode());
	}

	private static Notification existing(String status, int attempts) {
		Notification notification = Notification.create(SCORE_ID, "TELEGRAM", "deal-score:" + SCORE_ID);
		notification.setId(NOTIFICATION_ID);
		notification.setStatus(status);
		notification.setAttemptCount(attempts);
		return notification;
	}

	private static NotificationMessage message() {
		return new NotificationMessage(SCORE_ID, "deal-score:" + SCORE_ID, "TELEGRAM", LISTING_ID,
				"Deal title", "Deal body", "https://example.test/listing");
	}
}
