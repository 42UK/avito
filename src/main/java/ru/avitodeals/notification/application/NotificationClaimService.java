package ru.avitodeals.notification.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.notification.domain.Notification;
import ru.avitodeals.notification.infrastructure.persistence.NotificationRepository;
import ru.avitodeals.shared.application.TransactionScopedKeyLock;

import java.time.Instant;

@Service
public class NotificationClaimService {

	private final NotificationRepository repository;
	private final TransactionScopedKeyLock keyLock;
	private final int maximumAttempts;

	public NotificationClaimService(
			NotificationRepository repository,
			TransactionScopedKeyLock keyLock,
			@Value("${avito-deals.notifications.max-attempts:3}") int maximumAttempts) {
		if (maximumAttempts < 1) {
			throw new IllegalArgumentException("Notification maximum attempts must be positive");
		}
		this.repository = repository;
		this.keyLock = keyLock;
		this.maximumAttempts = maximumAttempts;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public NotificationClaim claim(NotificationMessage message) {
		keyLock.acquire("notification:" + message.channel() + ":" + message.dedupKey());
		Notification notification = repository
				.findByChannelAndDedupKeyForUpdate(message.channel(), message.dedupKey())
				.orElse(null);
		if (notification == null) {
			notification = Notification.create(message.dealScoreId(), message.channel(), message.dedupKey());
			notification.setAttemptCount(0);
		} else if ("SENT".equals(notification.getStatus())) {
			return new NotificationClaim(false, notification.getId(),
					NotificationResult.Status.DUPLICATE, null);
		} else if ("SENDING".equals(notification.getStatus())) {
			return new NotificationClaim(false, notification.getId(),
					NotificationResult.Status.UNCERTAIN, "DELIVERY_IN_PROGRESS");
		} else if ("UNCERTAIN".equals(notification.getStatus())) {
			return new NotificationClaim(false, notification.getId(),
					NotificationResult.Status.UNCERTAIN, notification.getErrorCode());
		} else if (!"FAILED".equals(notification.getStatus())) {
			return new NotificationClaim(false, notification.getId(),
					NotificationResult.Status.FAILED, "INVALID_NOTIFICATION_STATE");
		} else if (notification.getAttemptCount() >= maximumAttempts) {
			return new NotificationClaim(false, notification.getId(),
					NotificationResult.Status.FAILED, "MAX_ATTEMPTS_REACHED");
		}

		notification.setStatus("SENDING");
		notification.setAttemptCount(notification.getAttemptCount() + 1);
		notification.setLastAttemptAt(Instant.now());
		notification.setErrorCode(null);
		notification.setErrorMessage(null);
		Notification claimed = repository.saveAndFlush(notification);
		return new NotificationClaim(true, claimed.getId(), null, null);
	}
}
