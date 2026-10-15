package ru.avitodeals.notification.application;

import org.junit.jupiter.api.Test;
import ru.avitodeals.scoring.application.DealScored;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DealScoredNotificationListenerTests {

	private final NotificationService notificationService = mock(NotificationService.class);
	private final DealScoredNotificationListener listener =
			new DealScoredNotificationListener(notificationService);

	@Test
	void sendsOnlyADealAndIncludesRequiredListingDataInStableMessage() {
		DealScored event = event(List.of("DEAL"));

		listener.onDealScored(event);

		org.mockito.ArgumentCaptor<NotificationMessage> message =
				org.mockito.ArgumentCaptor.forClass(NotificationMessage.class);
		verify(notificationService).send(message.capture());
		assertEquals(event.dealScoreId(), message.getValue().dealScoreId());
		assertEquals(event.listingId(), message.getValue().listingId());
		assertEquals(event.listingTitle(), message.getValue().title());
		assertEquals(event.listingUrl(), message.getValue().url());
		assertEquals("deal-score:" + event.dealScoreId(), message.getValue().dedupKey());
		assertEquals("TELEGRAM", message.getValue().channel());
	}

	@Test
	void neverNotifiesBlockedOrReviewScoresEvenIfDealFlagIsPresent() {
		listener.onDealScored(event(List.of("DEAL", "BLOCKED")));
		listener.onDealScored(event(List.of("DEAL", "REVIEW")));
		listener.onDealScored(event(List.of("BLOCKED")));

		verify(notificationService, never()).send(any(NotificationMessage.class));
	}

	private static DealScored event(List<String> flags) {
		return new DealScored(UUID.randomUUID(), UUID.randomUUID(), "Телефон",
				"https://example.test/listing", new BigDecimal("39000"), "RUB",
				new BigDecimal("25.00"), new BigDecimal("50.00"), new BigDecimal("0.00"),
				flags, Instant.parse("2026-10-08T10:00:00Z"));
	}
}
