package ru.avitodeals.notification.infrastructure.telegram;

public interface TelegramTransport {

	TelegramResponse send(String botToken, String chatId, String text);
}
