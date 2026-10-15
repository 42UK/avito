package ru.avitodeals.pricing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "market_price",
		indexes = @Index(name = "idx_market_price_product_condition_computed_at",
				columnList = "product_id, condition_group, computed_at DESC"))
public class MarketPrice {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "product_id", nullable = false)
	private UUID productId;

	@Enumerated(EnumType.STRING)
	@Column(name = "condition_group", nullable = false, length = 16)
	private ConditionGroup conditionGroup;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal median;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal p25;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal p75;

	@Column(name = "market_price", nullable = false, precision = 14, scale = 2)
	private BigDecimal marketPrice;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(name = "sample_size", nullable = false)
	private int sampleSize;

	@Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
	private BigDecimal confidenceScore;

	@Enumerated(EnumType.STRING)
	@Column(name = "confidence_level", nullable = false, length = 8)
	private ConfidenceLevel confidenceLevel;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private List<UUID> outliers = new ArrayList<>();

	@Column(name = "outliers_count", nullable = false)
	private int outliersCount;

	@Column(nullable = false, length = 100)
	private String method;

	@Column(name = "computed_at", nullable = false)
	private Instant computedAt;

	public MarketPrice() {
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getProductId() {
		return productId;
	}

	public void setProductId(UUID productId) {
		this.productId = productId;
	}

	public ConditionGroup getConditionGroup() {
		return conditionGroup;
	}

	public void setConditionGroup(ConditionGroup conditionGroup) {
		this.conditionGroup = conditionGroup;
	}

	public BigDecimal getMedian() {
		return median;
	}

	public void setMedian(BigDecimal median) {
		this.median = median;
	}

	public BigDecimal getP25() {
		return p25;
	}

	public void setP25(BigDecimal p25) {
		this.p25 = p25;
	}

	public BigDecimal getP75() {
		return p75;
	}

	public void setP75(BigDecimal p75) {
		this.p75 = p75;
	}

	public BigDecimal getMarketPrice() {
		return marketPrice;
	}

	public void setMarketPrice(BigDecimal marketPrice) {
		this.marketPrice = marketPrice;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public int getSampleSize() {
		return sampleSize;
	}

	public void setSampleSize(int sampleSize) {
		this.sampleSize = sampleSize;
	}

	public BigDecimal getConfidenceScore() {
		return confidenceScore;
	}

	public void setConfidenceScore(BigDecimal confidenceScore) {
		this.confidenceScore = confidenceScore;
	}

	public ConfidenceLevel getConfidenceLevel() {
		return confidenceLevel;
	}

	public void setConfidenceLevel(ConfidenceLevel confidenceLevel) {
		this.confidenceLevel = confidenceLevel;
	}

	public List<UUID> getOutliers() {
		return outliers;
	}

	public void setOutliers(List<UUID> outliers) {
		this.outliers = new ArrayList<>(outliers);
	}

	public int getOutliersCount() {
		return outliersCount;
	}

	public void setOutliersCount(int outliersCount) {
		this.outliersCount = outliersCount;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public Instant getComputedAt() {
		return computedAt;
	}

	public void setComputedAt(Instant computedAt) {
		this.computedAt = computedAt;
	}
}
