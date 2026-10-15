package ru.avitodeals.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "deal_score",
		indexes = {
				@Index(name = "idx_deal_score_listing_computed_at",
						columnList = "listing_id, computed_at DESC"),
				@Index(name = "idx_deal_score_score", columnList = "deal_score DESC")
		})
public class DealScore {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "listing_id", nullable = false)
	private UUID listingId;

	@Column(name = "market_price_id", nullable = false)
	private UUID marketPriceId;

	@Column(name = "discount_percent", nullable = false, precision = 19, scale = 2)
	private BigDecimal discountPercent;

	@Column(name = "deal_score", nullable = false, precision = 5, scale = 2)
	private BigDecimal dealScore;

	@Column(name = "risk_score", nullable = false, precision = 5, scale = 2)
	private BigDecimal riskScore;

	@Column(nullable = false, precision = 5, scale = 4)
	private BigDecimal confidence;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private List<String> flags = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private List<Map<String, Object>> explanation = new ArrayList<>();

	@Column(name = "computed_at", nullable = false)
	private Instant computedAt;

	protected DealScore() {
	}

	public static DealScore create(
			UUID listingId,
			UUID marketPriceId,
			BigDecimal discountPercent,
			BigDecimal dealScore,
			BigDecimal riskScore,
			BigDecimal confidence,
			List<String> flags,
			List<Map<String, Object>> explanation,
			Instant computedAt) {
		DealScore score = new DealScore();
		score.listingId = Objects.requireNonNull(listingId, "listingId");
		score.marketPriceId = Objects.requireNonNull(marketPriceId, "marketPriceId");
		score.discountPercent = Objects.requireNonNull(discountPercent, "discountPercent");
		score.dealScore = Objects.requireNonNull(dealScore, "dealScore");
		score.riskScore = Objects.requireNonNull(riskScore, "riskScore");
		score.confidence = Objects.requireNonNull(confidence, "confidence");
		score.flags = new ArrayList<>(Objects.requireNonNull(flags, "flags"));
		score.explanation = new ArrayList<>(Objects.requireNonNull(explanation, "explanation"));
		score.computedAt = Objects.requireNonNull(computedAt, "computedAt");
		return score;
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

	public UUID getMarketPriceId() {
		return marketPriceId;
	}

	public void setMarketPriceId(UUID marketPriceId) {
		this.marketPriceId = marketPriceId;
	}

	public BigDecimal getDiscountPercent() {
		return discountPercent;
	}

	public void setDiscountPercent(BigDecimal discountPercent) {
		this.discountPercent = discountPercent;
	}

	public BigDecimal getDealScore() {
		return dealScore;
	}

	public void setDealScore(BigDecimal dealScore) {
		this.dealScore = dealScore;
	}

	public BigDecimal getRiskScore() {
		return riskScore;
	}

	public void setRiskScore(BigDecimal riskScore) {
		this.riskScore = riskScore;
	}

	public BigDecimal getConfidence() {
		return confidence;
	}

	public void setConfidence(BigDecimal confidence) {
		this.confidence = confidence;
	}

	public List<String> getFlags() {
		return flags;
	}

	public boolean isDealAllowed() {
		return flags.contains("DEAL");
	}

	public void setFlags(List<String> flags) {
		this.flags = flags;
	}

	public List<Map<String, Object>> getExplanation() {
		return explanation;
	}

	public void setExplanation(List<Map<String, Object>> explanation) {
		this.explanation = explanation;
	}

	public Instant getComputedAt() {
		return computedAt;
	}

	public void setComputedAt(Instant computedAt) {
		this.computedAt = computedAt;
	}
}
