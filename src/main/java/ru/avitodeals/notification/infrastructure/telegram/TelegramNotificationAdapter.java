package ru.avitodeals.notification.infrastructure.telegram;

import org.springframework.stereotype.Component;
import ru.avitodeals.notification.application.NotificationChannel;
import ru.avitodeals.notification.application.NotificationMessage;
import ru.avitodeals.notification.application.NotificationResult;

import java.util.Objects;

@Component
public class TelegramNotificationAdapter implements NotificationChannel {

	public static final String CHANNEL = "TELEGRAM";

	private final TelegramNotificationProperties properties;
	private final TelegramTransport transport;

	public TelegramNotificationAdapter(
			TelegramNotificationProperties properties,
			TelegramTransport transport) {
		this.properties = properties;
		this.transport = transport;
	}

	@Override
	public String channel() {
		return CHANNEL;
	}

	@Override
	public NotificationResult deliver(NotificationMessage message) {
		if (blank(properties.getBotToken()) || blank(properties.getChatId())) {
			return failure("TELEGRAM_NOT_CONFIGURED");
		}
		String text = message.title() + "\n\n" + message.body() + "\n\n" + message.url();
		try {
			TelegramResponse response = transport.send(properties.getBotToken(), properties.getChatId(), text);
			if (response == null) {
				return new NotificationResult(null, NotificationResult.Status.UNCERTAIN,
						"TELEGRAM_TRANSPORT_ERROR");
			}
			if (response.ok()) {
				return new NotificationResult(null, NotificationResult.Status.SENT, null);
			}
			return failure("TELEGRAM_REJECTED");
		} catch (TelegramHttpFailure exception) {
			if (exception.getStatusCode() >= 500) {
				return new NotificationResult(null, NotificationResult.Status.UNCERTAIN,
						"TELEGRAM_HTTP_OUTCOME_UNKNOWN");
			}
			return failure("TELEGRAM_HTTP_ERROR");
		} catch (RuntimeException exception) {
			return new NotificationResult(null, NotificationResult.Status.UNCERTAIN,
					"TELEGRAM_TRANSPORT_ERROR");
		}
	}

	private static NotificationResult failure(String errorCode) {
		return new NotificationResult(null, NotificationResult.Status.FAILED, errorCode);
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
