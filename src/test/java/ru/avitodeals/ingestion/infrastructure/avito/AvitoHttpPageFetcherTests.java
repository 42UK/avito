package ru.avitodeals.ingestion.infrastructure.avito;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AvitoHttpPageFetcherTests {

	@Test
	void requestsOnlyHttpsAvitoPublicPagesAndReadsHtml() throws Exception {
		HttpClient client = mock(HttpClient.class);
		AvitoIngestionProperties properties = properties();
		HttpResponse<java.io.InputStream> response = mockResponse();
		when(response.statusCode()).thenReturn(200);
		when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(
				Map.of("Content-Type", List.of("text/html; charset=UTF-8")),
				(name, value) -> true));
		when(response.body()).thenReturn(new ByteArrayInputStream("<html>public</html>"
				.getBytes(StandardCharsets.UTF_8)));
		when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
		AvitoHttpPageFetcher fetcher = new AvitoHttpPageFetcher(client, properties);

		String html = fetcher.fetch(URI.create("https://www.avito.ru/rossiya?q=phone"));

		assertEquals("<html>public</html>", html);
	}

	@Test
	void refusesNonAvitoOrInsecureUrlsBeforeRequest() {
		HttpClient client = mock(HttpClient.class);
		AvitoHttpPageFetcher fetcher = new AvitoHttpPageFetcher(client, properties());

		assertThrows(IllegalArgumentException.class,
				() -> fetcher.fetch(URI.create("https://example.com/")));
		assertThrows(IllegalArgumentException.class,
				() -> fetcher.fetch(URI.create("http://www.avito.ru/")));
	}

	@Test
	void reportsRateLimitingInsteadOfRetryingOrBypassingIt() throws Exception {
		HttpClient client = mock(HttpClient.class);
		HttpResponse<java.io.InputStream> response = mockResponse();
		when(response.statusCode()).thenReturn(429);
		when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(
				Map.of("Retry-After", List.of("120")), (name, value) -> true));
		when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
		AvitoHttpPageFetcher fetcher = new AvitoHttpPageFetcher(client, properties());

		AvitoFetchException exception = assertThrows(AvitoFetchException.class,
				() -> fetcher.fetch(URI.create("https://www.avito.ru/rossiya?q=phone")));
		assertEquals(429, exception.getStatusCode());
		assertEquals(Duration.ofSeconds(120), exception.getRetryAfter());
		AvitoFetchException deferred = assertThrows(AvitoFetchException.class,
				() -> fetcher.fetch(URI.create("https://www.avito.ru/rossiya?q=another")));
		org.junit.jupiter.api.Assertions.assertTrue(deferred.isDeferredByRateLimit());
		org.mockito.Mockito.verify(client, org.mockito.Mockito.times(1))
				.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
	}

	@Test
	void usesServerRetryAfterLongerThanFallbackCooldown() throws Exception {
		HttpClient client = mock(HttpClient.class);
		HttpResponse<java.io.InputStream> response = mockResponse();
		when(response.statusCode()).thenReturn(429);
		when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(
				Map.of("Retry-After", List.of("7200")), (name, value) -> true));
		when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
		Clock clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);
		AvitoHttpPageFetcher fetcher = new AvitoHttpPageFetcher(client, properties(), clock);

		AvitoFetchException exception = assertThrows(AvitoFetchException.class,
				() -> fetcher.fetch(URI.create("https://www.avito.ru/rossiya?q=phone")));

		assertEquals(Duration.ofHours(2), exception.getRetryAfter());
	}

	@Test
	void usesConfiguredFallbackCooldownWhenRetryAfterIsMissing() throws Exception {
		HttpClient client = mock(HttpClient.class);
		HttpResponse<java.io.InputStream> response = mockResponse();
		when(response.statusCode()).thenReturn(429);
		when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (name, value) -> true));
		when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
		AvitoIngestionProperties properties = properties();
		properties.setRateLimitFallbackCooldown(Duration.ofMinutes(5));
		AvitoHttpPageFetcher fetcher = new AvitoHttpPageFetcher(
				client, properties, Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC));

		AvitoFetchException exception = assertThrows(AvitoFetchException.class,
				() -> fetcher.fetch(URI.create("https://www.avito.ru/rossiya?q=phone")));

		assertEquals(Duration.ofMinutes(5), exception.getRetryAfter());
	}

	@Test
	void rejectsRedirectsAndUnexpectedContentTypes() throws Exception {
		HttpClient client = mock(HttpClient.class);
		HttpResponse<java.io.InputStream> response = mockResponse();
		when(response.statusCode()).thenReturn(302);
		when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of(), (name, value) -> true));
		when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
		AvitoHttpPageFetcher fetcher = new AvitoHttpPageFetcher(client, properties());

		assertThrows(AvitoFetchException.class,
				() -> fetcher.fetch(URI.create("https://www.avito.ru/rossiya?q=phone")));
	}

	@Test
	void followsOnlySameHostHttpsRedirects() throws Exception {
		HttpClient client = mock(HttpClient.class);
		HttpResponse<java.io.InputStream> redirect = mockResponse();
		when(redirect.statusCode()).thenReturn(302);
		when(redirect.headers()).thenReturn(java.net.http.HttpHeaders.of(
				Map.of("Location", List.of("/rossiya/canonical")), (name, value) -> true));
		HttpResponse<java.io.InputStream> page = mockResponse();
		when(page.statusCode()).thenReturn(200);
		when(page.headers()).thenReturn(java.net.http.HttpHeaders.of(
				Map.of("Content-Type", List.of("text/html")), (name, value) -> true));
		when(page.body()).thenReturn(new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)));
		when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(redirect, page);
		AvitoHttpPageFetcher fetcher = new AvitoHttpPageFetcher(client, properties());

		assertEquals("ok", fetcher.fetch(URI.create("https://www.avito.ru/rossiya?q=phone")));
	}

	@Test
	void rejectsOffsiteRedirectsAndOversizedPages() throws Exception {
		HttpClient redirectClient = mock(HttpClient.class);
		HttpResponse<java.io.InputStream> redirect = mockResponse();
		when(redirect.statusCode()).thenReturn(302);
		when(redirect.headers()).thenReturn(java.net.http.HttpHeaders.of(
				Map.of("Location", List.of("https://example.com/")), (name, value) -> true));
		when(redirectClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(redirect);
		AvitoHttpPageFetcher redirectFetcher = new AvitoHttpPageFetcher(redirectClient, properties());
		assertThrows(IllegalArgumentException.class,
				() -> redirectFetcher.fetch(URI.create("https://www.avito.ru/rossiya?q=phone")));

		HttpClient largePageClient = mock(HttpClient.class);
		HttpResponse<java.io.InputStream> largePage = mockResponse();
		when(largePage.statusCode()).thenReturn(200);
		when(largePage.headers()).thenReturn(java.net.http.HttpHeaders.of(
				Map.of("Content-Type", List.of("text/html")), (name, value) -> true));
		when(largePage.body()).thenReturn(new ByteArrayInputStream(new byte[1025]));
		when(largePageClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
				.thenReturn(largePage);
		AvitoHttpPageFetcher largePageFetcher = new AvitoHttpPageFetcher(largePageClient, properties());
		assertThrows(AvitoFetchException.class,
				() -> largePageFetcher.fetch(URI.create("https://www.avito.ru/rossiya?q=phone")));
	}

	@SuppressWarnings("unchecked")
	private static HttpResponse<java.io.InputStream> mockResponse() {
		return mock(HttpResponse.class);
	}

	private static AvitoIngestionProperties properties() {
		AvitoIngestionProperties properties = new AvitoIngestionProperties();
		properties.setRequestTimeout(Duration.ofSeconds(1));
		properties.setMinimumRequestInterval(Duration.ZERO);
		properties.setMaximumResponseBytes(1024);
		return properties;
	}
}
