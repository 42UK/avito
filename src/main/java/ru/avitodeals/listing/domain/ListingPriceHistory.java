package ru.avitodeals.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "listing_price_history",
		indexes = @Index(name = "idx_listing_price_history_listing_observed_at",
				columnList = "listing_id, observed_at"))
public class ListingPriceHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "listing_id", nullable = false)
	private UUID listingId;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal price;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "observed_at", nullable = false, columnDefinition = "timestamp with time zone")
	private Instant observedAt;

	protected ListingPriceHistory() {
	}

	public ListingPriceHistory(UUID listingId, BigDecimal price, String currency, Instant observedAt) {
		this.listingId = listingId;
		this.price = price;
		this.currency = currency;
		this.observedAt = observedAt;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getListingId() {
		return listingId;
	}

	public void setListingId(UUID listingId) {
		this.listingId = listingId;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public Instant getObservedAt() {
		return observedAt;
	}

	public void setObservedAt(Instant observedAt) {
		this.observedAt = observedAt;
	}
}
