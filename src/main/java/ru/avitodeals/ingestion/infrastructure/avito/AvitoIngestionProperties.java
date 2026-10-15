package ru.avitodeals.ingestion.infrastructure.avito;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "avito-deals.ingestion.avito")
public class AvitoIngestionProperties {

	private String regionSlug = "rossiya";
	private int maxPages = 2;
	private Duration requestTimeout = Duration.ofSeconds(15);
	private Duration minimumRequestInterval = Duration.ofSeconds(30);
	private Duration rateLimitFallbackCooldown = Duration.ofMinutes(5);
	private int maximumResponseBytes = 5 * 1024 * 1024;

	public String getRegionSlug() {
		return regionSlug;
	}

	public void setRegionSlug(String regionSlug) {
		this.regionSlug = regionSlug;
	}

	public int getMaxPages() {
		return maxPages;
	}

	public void setMaxPages(int maxPages) {
		this.maxPages = maxPages;
	}

	public Duration getRequestTimeout() {
		return requestTimeout;
	}

	public void setRequestTimeout(Duration requestTimeout) {
		this.requestTimeout = requestTimeout;
	}

	public Duration getMinimumRequestInterval() {
		return minimumRequestInterval;
	}

	public void setMinimumRequestInterval(Duration minimumRequestInterval) {
		this.minimumRequestInterval = minimumRequestInterval;
	}

	public Duration getRateLimitFallbackCooldown() {
		return rateLimitFallbackCooldown;
	}

	public void setRateLimitFallbackCooldown(Duration rateLimitFallbackCooldown) {
		this.rateLimitFallbackCooldown = rateLimitFallbackCooldown;
	}

	public int getMaximumResponseBytes() {
		return maximumResponseBytes;
	}

	public void setMaximumResponseBytes(int maximumResponseBytes) {
		this.maximumResponseBytes = maximumResponseBytes;
	}
}
