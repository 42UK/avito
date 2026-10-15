package ru.avitodeals.notification.infrastructure.telegram;

import org.junit.jupiter.api.Test;
import ru.avitodeals.notification.application.NotificationMessage;
import ru.avitodeals.notification.application.NotificationResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramNotificationAdapterTests {

	private final TelegramTransport transport = mock(TelegramTransport.class);
	private final TelegramNotificationProperties properties = new TelegramNotificationProperties();
	private final TelegramNotificationAdapter adapter = new TelegramNotificationAdapter(properties, transport);

	@Test
	void missingRuntimeCredentialsFailWithoutCallingTransport() {
		NotificationResult result = adapter.deliver(message());

		assertEquals(NotificationResult.Status.FAILED, result.status());
		assertEquals("TELEGRAM_NOT_CONFIGURED", result.errorCode());
		verify(transport, never()).send(org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	void configuredTelegramUsesPlainMessageAndReportsAcceptance() {
		properties.setBotToken("test-token");
		properties.setChatId("test-chat");
		when(transport.send(eq("test-token"), eq("test-chat"),
				eq("Deal title\n\nCurrent price: 10 RUB\n\nhttps://example.test/listing")))
				.thenReturn(new TelegramResponse(true, null));

		NotificationResult result = adapter.deliver(message());

		assertEquals(NotificationResult.Status.SENT, result.status());
		verify(transport).send("test-token", "test-chat",
				"Deal title\n\nCurrent price: 10 RUB\n\nhttps://example.test/listing");
	}

	@Test
	void rejectedAndAmbiguousTransportOutcomesRemainDistinct() {
		properties.setBotToken("test-token");
		properties.setChatId("test-chat");
		when(transport.send(eq("test-token"), eq("test-chat"), org.mockito.ArgumentMatchers.anyString()))
				.thenReturn(new TelegramResponse(false, 400))
				.thenThrow(new IllegalStateException("transport timed out"));

		NotificationResult rejected = adapter.deliver(message());
		NotificationResult uncertain = adapter.deliver(message());

		assertEquals(NotificationResult.Status.FAILED, rejected.status());
		assertEquals("TELEGRAM_REJECTED", rejected.errorCode());
		assertEquals(NotificationResult.Status.UNCERTAIN, uncertain.status());
		assertEquals("TELEGRAM_TRANSPORT_ERROR", uncertain.errorCode());

		when(transport.send(eq("test-token"), eq("test-chat"), org.mockito.ArgumentMatchers.anyString()))
				.thenThrow(new TelegramHttpFailure(503));
		NotificationResult serverError = adapter.deliver(message());
		assertEquals(NotificationResult.Status.UNCERTAIN, serverError.status());
		assertEquals("TELEGRAM_HTTP_OUTCOME_UNKNOWN", serverError.errorCode());
	}

	private static NotificationMessage message() {
		return new NotificationMessage(UUID.randomUUID(), "dedup-key", "TELEGRAM", UUID.randomUUID(),
				"Deal title", "Current price: 10 RUB", "https://example.test/listing");
	}
}
