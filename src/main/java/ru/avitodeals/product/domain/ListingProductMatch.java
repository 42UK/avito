package ru.avitodeals.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "listing_product_match",
		indexes = @Index(name = "idx_listing_product_match_product_id", columnList = "product_id"))
public class ListingProductMatch {

	@Id
	@Column(name = "listing_id")
	private UUID listingId;

	@Column(name = "product_id")
	private UUID productId;

	@Column(nullable = false, precision = 5, scale = 4)
	private BigDecimal confidence;

	@Column(name = "matcher_version", nullable = false, length = 100)
	private String matcherVersion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private MatchStatus status;

	protected ListingProductMatch() {
	}

	public ListingProductMatch(
			UUID listingId, UUID productId, BigDecimal confidence, String matcherVersion, MatchStatus status) {
		this.listingId = listingId;
		this.productId = productId;
		this.confidence = confidence;
		this.matcherVersion = matcherVersion;
		this.status = status;
	}

	public UUID getListingId() {
		return listingId;
	}

	public void setListingId(UUID listingId) {
		this.listingId = listingId;
	}

	public UUID getProductId() {
		return productId;
	}

	public void setProductId(UUID productId) {
		this.productId = productId;
	}

	public BigDecimal getConfidence() {
		return confidence;
	}

	public void setConfidence(BigDecimal confidence) {
		this.confidence = confidence;
	}

	public String getMatcherVersion() {
		return matcherVersion;
	}

	public void setMatcherVersion(String matcherVersion) {
		this.matcherVersion = matcherVersion;
	}

	public MatchStatus getStatus() {
		return status;
	}

	public void setStatus(MatchStatus status) {
		this.status = status;
	}
}
