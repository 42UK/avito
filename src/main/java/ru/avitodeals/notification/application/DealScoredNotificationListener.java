package ru.avitodeals.notification.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.avitodeals.scoring.application.DealScored;

import java.util.Locale;

@Component
public class DealScoredNotificationListener {

	private static final String TELEGRAM = "TELEGRAM";
	private final NotificationService notificationService;

	public DealScoredNotificationListener(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onDealScored(DealScored event) {
		if (!event.flags().contains("DEAL")
				|| event.flags().contains("BLOCKED")
				|| event.flags().contains("REVIEW")) {
			return;
		}
		String body = String.format(Locale.ROOT,
				"Цена: %s %s%nСкидка: %s%%%nОценка сделки: %s/100%nРиск: %s/100",
				event.listingPrice().toPlainString(),
				event.currency().toUpperCase(Locale.ROOT),
				event.discountPercent().toPlainString(),
				event.dealScore().toPlainString(),
				event.riskScore().toPlainString());
		notificationService.send(new NotificationMessage(
				event.dealScoreId(),
				"deal-score:" + event.dealScoreId(),
				TELEGRAM,
				event.listingId(),
				event.listingTitle(),
				body,
				event.listingUrl()));
	}
}
