package ru.avitodeals.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import ru.avitodeals.listing.application.DataOrigin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "listing",
		uniqueConstraints = @UniqueConstraint(name = "uq_listing_source_external_id",
				columnNames = {"source", "external_id"}),
		indexes = {
				@Index(name = "idx_listing_seller_id", columnList = "seller_id"),
				@Index(name = "idx_listing_status_last_seen_at", columnList = "status, last_seen_at"),
				@Index(name = "idx_listing_price", columnList = "price")
		})
public class Listing {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 100)
	private String source;

	@Column(name = "external_id", nullable = false, length = 255)
	private String externalId;

	@Enumerated(EnumType.STRING)
	@Column(name = "data_origin", nullable = false, length = 16)
	private DataOrigin dataOrigin = DataOrigin.DEMO;

	@Column(name = "seller_id")
	private UUID sellerId;

	@Column(nullable = false, columnDefinition = "text")
	private String title;

	@Column(columnDefinition = "text")
	private String description;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal price;

	@Column(nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(name = "condition", nullable = false, length = 16)
	private ListingCondition condition;

	@Column(columnDefinition = "text")
	private String location;

	@Column(nullable = false, columnDefinition = "text")
	private String url;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ListingStatus status;

	@Column(name = "content_hash", length = 128)
	private String contentHash;

	@Column(name = "first_seen_at", nullable = false, columnDefinition = "timestamp with time zone")
	private Instant firstSeenAt;

	@Column(name = "last_seen_at", nullable = false, columnDefinition = "timestamp with time zone")
	private Instant lastSeenAt;

	protected Listing() {
	}

	public Listing(String source, String externalId) {
		this.source = source;
		this.externalId = externalId;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getExternalId() {
		return externalId;
	}

	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	public DataOrigin getDataOrigin() {
		return dataOrigin;
	}

	public void setDataOrigin(DataOrigin dataOrigin) {
		this.dataOrigin = dataOrigin;
	}

	public UUID getSellerId() {
		return sellerId;
	}

	public void setSellerId(UUID sellerId) {
		this.sellerId = sellerId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
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

	public ListingCondition getCondition() {
		return condition;
	}

	public void setCondition(ListingCondition condition) {
		this.condition = condition;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public ListingStatus getStatus() {
		return status;
	}

	public void setStatus(ListingStatus status) {
		this.status = status;
	}

	public String getContentHash() {
		return contentHash;
	}

	public void setContentHash(String contentHash) {
		this.contentHash = contentHash;
	}

	public Instant getFirstSeenAt() {
		return firstSeenAt;
	}

	public void setFirstSeenAt(Instant firstSeenAt) {
		this.firstSeenAt = firstSeenAt;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public void setLastSeenAt(Instant lastSeenAt) {
		this.lastSeenAt = lastSeenAt;
	}
}
