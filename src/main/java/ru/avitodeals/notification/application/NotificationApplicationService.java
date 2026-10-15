package ru.avitodeals.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ru.avitodeals.notification.infrastructure.persistence.NotificationRepository;

import java.util.List;

@Service
public class NotificationApplicationService implements NotificationService {

	private static final Logger LOGGER = LoggerFactory.getLogger(NotificationApplicationService.class);

	private final NotificationClaimService claimService;
	private final NotificationStateService stateService;
	private final NotificationRepository repository;
	private final List<NotificationChannel> channels;

	public NotificationApplicationService(
			NotificationClaimService claimService,
			NotificationStateService stateService,
			NotificationRepository repository,
			List<NotificationChannel> channels) {
		this.claimService = claimService;
		this.stateService = stateService;
		this.repository = repository;
		this.channels = List.copyOf(channels);
	}

	@Override
	public NotificationResult send(NotificationMessage message) {
		NotificationClaim claim;
		try {
			claim = claimService.claim(message);
		} catch (DataIntegrityViolationException concurrentClaim) {
			return repository.findByChannelAndDedupKey(message.channel(), message.dedupKey())
					.map(existing -> {
						if ("SENT".equals(existing.getStatus())) {
							return new NotificationResult(existing.getId(),
									NotificationResult.Status.DUPLICATE, null);
						}
						return new NotificationResult(existing.getId(),
								NotificationResult.Status.UNCERTAIN, "DELIVERY_IN_PROGRESS");
					})
					.orElseThrow(() -> concurrentClaim);
		}
		if (!claim.claimed()) {
			return new NotificationResult(claim.notificationId(), claim.existingStatus(), claim.errorCode());
		}

		NotificationChannel channel = channels.stream()
				.filter(candidate -> candidate.channel().equals(message.channel()))
				.findFirst()
				.orElse(null);
		NotificationResult result;
		if (channel == null) {
			result = new NotificationResult(claim.notificationId(),
					NotificationResult.Status.FAILED, "CHANNEL_UNAVAILABLE");
		} else {
			try {
				NotificationResult delivered = channel.deliver(message);
				result = new NotificationResult(claim.notificationId(), delivered.status(), delivered.errorCode());
			} catch (RuntimeException exception) {
				LOGGER.error("Notification delivery outcome is unknown for notification {}; exception type {}",
						claim.notificationId(), exception.getClass().getSimpleName());
				result = new NotificationResult(claim.notificationId(),
						NotificationResult.Status.UNCERTAIN, "TELEGRAM_TRANSPORT_ERROR");
			}
		}
		stateService.complete(claim.notificationId(), result);
		if (result.status() == NotificationResult.Status.FAILED
				|| result.status() == NotificationResult.Status.UNCERTAIN) {
			LOGGER.warn("Notification {} delivery ended with status {} and code {}",
					claim.notificationId(), result.status(), result.errorCode());
		}
		return result;
	}
}
