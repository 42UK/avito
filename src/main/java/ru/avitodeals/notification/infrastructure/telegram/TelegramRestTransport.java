package ru.avitodeals.notification.infrastructure.telegram;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.Map;

@Component
public class TelegramRestTransport implements TelegramTransport {

	private final RestClient restClient;

	public TelegramRestTransport(TelegramNotificationProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.getTimeout())
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.getTimeout());
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	@Override
	public TelegramResponse send(String botToken, String chatId, String text) {
		try {
			return restClient.post()
					.uri("https://api.telegram.org/bot{token}/sendMessage", botToken)
					.body(Map.of("chat_id", chatId, "text", text))
					.retrieve()
					.body(TelegramResponse.class);
		} catch (RestClientResponseException exception) {
			throw new TelegramHttpFailure(exception.getStatusCode().value());
		}
	}
}
