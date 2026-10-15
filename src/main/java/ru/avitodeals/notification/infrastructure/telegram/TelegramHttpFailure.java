package ru.avitodeals.notification.infrastructure.telegram;

public class TelegramHttpFailure extends RuntimeException {

	private final int statusCode;

	public TelegramHttpFailure(int statusCode) {
		super("Telegram returned an unsuccessful HTTP status");
		this.statusCode = statusCode;
	}

	public int getStatusCode() {
		return statusCode;
	}
}
