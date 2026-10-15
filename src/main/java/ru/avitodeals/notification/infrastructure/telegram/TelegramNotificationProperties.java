package ru.avitodeals.notification.infrastructure.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "avito-deals.notifications.telegram")
public class TelegramNotificationProperties {

	private String botToken = "";
	private String chatId = "";
	private Duration timeout = Duration.ofSeconds(10);

	public String getBotToken() {
		return botToken;
	}

	public void setBotToken(String botToken) {
		this.botToken = botToken;
	}

	public String getChatId() {
		return chatId;
	}

	public void setChatId(String chatId) {
		this.chatId = chatId;
	}

	public Duration getTimeout() {
		return timeout;
	}

	public void setTimeout(Duration timeout) {
		this.timeout = timeout;
	}
}
