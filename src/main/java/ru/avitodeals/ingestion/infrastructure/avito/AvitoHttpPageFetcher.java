package ru.avitodeals.ingestion.infrastructure.avito;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AvitoHttpPageFetcher implements AvitoPageFetcher {

	private static final int MAX_REDIRECTS = 3;
	private static final Duration ABSOLUTE_MINIMUM_INTERVAL = Duration.ofSeconds(1);
	private static final Pattern CHARSET = Pattern.compile("(?i)charset\\s*=\\s*[\"']?([^;\"'\\s]+)");

	private final HttpClient httpClient;
	private final AvitoIngestionProperties properties;
	private final Clock clock;
	private long nextRequestAtNanos;
	private Instant rateLimitedUntil;

	@Autowired
	public AvitoHttpPageFetcher(AvitoIngestionProperties properties, Clock clock) {
		this(HttpClient.newBuilder()
				.connectTimeout(properties.getRequestTimeout())
				.followRedirects(HttpClient.Redirect.NEVER)
				.build(), properties, clock);
	}

	AvitoHttpPageFetcher(HttpClient httpClient, AvitoIngestionProperties properties) {
		this(httpClient, properties, Clock.systemUTC());
	}

	AvitoHttpPageFetcher(HttpClient httpClient, AvitoIngestionProperties properties, Clock clock) {
		this.httpClient = httpClient;
		this.properties = properties;
		this.clock = clock;
		validateProperties(properties);
	}

	@Override
	public String fetch(URI uri) {
		validateUri(uri);
		URI currentUri = uri;
		for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
			HttpResponse<InputStream> response = request(currentUri);
			int status = response.statusCode();
			if (isRedirect(status)) {
				try (InputStream ignored = response.body()) {
					if (redirectCount == MAX_REDIRECTS) {
						throw new AvitoFetchException("Avito exceeded the same-host redirect limit");
					}
					String location = response.headers().firstValue("Location")
							.orElseThrow(() -> new AvitoFetchException("Avito redirect did not include a location"));
					URI redirected = currentUri.resolve(location);
					validateUri(redirected);
					currentUri = redirected;
				} catch (IOException exception) {
					throw new AvitoFetchException("Unable to close Avito redirect response", exception);
				}
				continue;
			}
			try (InputStream body = response.body()) {
				if (status != 200) {
					if (status == 429) {
						Duration retryAfter = activateRateLimitCooldown(response);
						throw new AvitoFetchException(
								"Avito returned HTTP status 429; requests paused for " + retryAfter,
								429, retryAfter, false);
					}
					throw new AvitoFetchException("Avito returned HTTP status " + status
							+ "; automatic retries are disabled", status);
				}
				String contentType = response.headers().firstValue("Content-Type").orElse("");
				if (!contentType.isBlank() && !contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
					throw new AvitoFetchException("Avito response is not HTML");
				}
				return new String(readBounded(body), responseCharset(contentType));
			} catch (IOException exception) {
				throw new AvitoFetchException("Unable to read public Avito page", exception);
			}
		}
		throw new AvitoFetchException("Avito redirect processing ended unexpectedly");
	}

	private HttpResponse<InputStream> request(URI uri) {
		awaitRequestSlot();
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(properties.getRequestTimeout())
				.header("Accept", "text/html")
				.GET()
				.build();
		try {
			return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AvitoFetchException("Interrupted while requesting public Avito page", exception);
		} catch (IOException exception) {
			throw new AvitoFetchException("Unable to request public Avito page", exception);
		}
	}

	private synchronized void awaitRequestSlot() {
		Duration cooldownRemaining = rateLimitCooldownRemaining();
		if (!cooldownRemaining.isZero()) {
			throw new AvitoFetchException(
					"Avito requests are paused after HTTP 429 for another " + cooldownRemaining,
					429, cooldownRemaining, true);
		}
		Duration interval = properties.getMinimumRequestInterval().compareTo(ABSOLUTE_MINIMUM_INTERVAL) < 0
				? ABSOLUTE_MINIMUM_INTERVAL : properties.getMinimumRequestInterval();
		long waitNanos = nextRequestAtNanos - System.nanoTime();
		if (waitNanos > 0) {
			try {
				TimeUnit.NANOSECONDS.sleep(waitNanos);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new AvitoFetchException("Interrupted while respecting the Avito request interval", exception);
			}
		}
		nextRequestAtNanos = System.nanoTime() + interval.toNanos();
	}

	private synchronized Duration activateRateLimitCooldown(HttpResponse<?> response) {
		Instant now = clock.instant();
		Duration retryAfter = response.headers().firstValue("Retry-After")
				.map(value -> parseRetryAfter(value, now, properties.getRateLimitFallbackCooldown()))
				.orElse(properties.getRateLimitFallbackCooldown());
		if (retryAfter.isNegative() || retryAfter.isZero()) {
			retryAfter = properties.getRateLimitFallbackCooldown();
		}
		Instant candidate;
		try {
			candidate = now.plus(retryAfter);
		} catch (java.time.DateTimeException | ArithmeticException exception) {
			candidate = Instant.MAX;
		}
		if (rateLimitedUntil == null || candidate.isAfter(rateLimitedUntil)) {
			rateLimitedUntil = candidate;
		}
		return Duration.between(now, rateLimitedUntil);
	}

	private Duration rateLimitCooldownRemaining() {
		if (rateLimitedUntil == null) {
			return Duration.ZERO;
		}
		Duration remaining = Duration.between(clock.instant(), rateLimitedUntil);
		if (remaining.isNegative() || remaining.isZero()) {
			rateLimitedUntil = null;
			return Duration.ZERO;
		}
		return remaining;
	}

	private static Duration parseRetryAfter(String value, Instant now, Duration fallbackCooldown) {
		try {
			long seconds = Long.parseLong(value.trim());
			return seconds > 0 ? Duration.ofSeconds(seconds) : fallbackCooldown;
		} catch (NumberFormatException ignored) {
			try {
				Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
				Duration delay = Duration.between(now, retryAt);
				return delay.isNegative() || delay.isZero() ? fallbackCooldown : delay;
			} catch (java.time.format.DateTimeParseException exception) {
				return fallbackCooldown;
			}
		}
	}

	private static boolean isRedirect(int status) {
		return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
	}

	private byte[] readBounded(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = input.read(buffer)) != -1) {
			if (output.size() + read > properties.getMaximumResponseBytes()) {
				throw new AvitoFetchException("Avito HTML response exceeds configured size limit");
			}
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	private static Charset responseCharset(String contentType) {
		Matcher matcher = CHARSET.matcher(contentType);
		if (!matcher.find()) {
			return StandardCharsets.UTF_8;
		}
		try {
			return Charset.forName(matcher.group(1));
		} catch (IllegalArgumentException exception) {
			throw new AvitoFetchException("Avito response declares an unsupported character encoding", exception);
		}
	}

	private static void validateUri(URI uri) {
		if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())
				|| !"www.avito.ru".equalsIgnoreCase(uri.getHost())
				|| uri.getUserInfo() != null
				|| uri.getPort() != -1 && uri.getPort() != 443
				|| uri.getFragment() != null) {
			throw new IllegalArgumentException("Only HTTPS pages on www.avito.ru can be requested");
		}
	}

	private static void validateProperties(AvitoIngestionProperties properties) {
		if (properties.getRequestTimeout() == null || properties.getRequestTimeout().isZero()
				|| properties.getRequestTimeout().isNegative()
				|| properties.getMinimumRequestInterval() == null
				|| properties.getMinimumRequestInterval().isNegative()
				|| properties.getRateLimitFallbackCooldown() == null
				|| properties.getRateLimitFallbackCooldown().isZero()
				|| properties.getRateLimitFallbackCooldown().isNegative()
				|| properties.getMaximumResponseBytes() < 1024
				|| properties.getMaximumResponseBytes() > 10 * 1024 * 1024) {
			throw new IllegalArgumentException("Avito fetch timeout, interval or response size limit is invalid");
		}
	}
}
