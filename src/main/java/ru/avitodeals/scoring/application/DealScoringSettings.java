package ru.avitodeals.scoring.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

@Component
public final class DealScoringSettings {

	private final BigDecimal minimumDiscountPercent;
	private final BigDecimal minimumDealScore;
	private final BigDecimal minimumMatchConfidence;
	private final BigDecimal suspiciousDiscountPercent;
	private final BigDecimal maximumRejectedDealScore;

	public DealScoringSettings(
			@Value("${avito-deals.scoring.minimum-discount-percent:20}") BigDecimal minimumDiscountPercent,
			@Value("${avito-deals.scoring.minimum-deal-score:40}") BigDecimal minimumDealScore,
			@Value("${avito-deals.scoring.minimum-match-confidence:0.90}") BigDecimal minimumMatchConfidence,
			@Value("${avito-deals.scoring.suspicious-discount-percent:60}") BigDecimal suspiciousDiscountPercent,
			@Value("${avito-deals.scoring.maximum-rejected-deal-score:25}") BigDecimal maximumRejectedDealScore) {
		this.minimumDiscountPercent = Objects.requireNonNull(minimumDiscountPercent, "minimumDiscountPercent");
		this.minimumDealScore = Objects.requireNonNull(minimumDealScore, "minimumDealScore");
		this.minimumMatchConfidence = Objects.requireNonNull(minimumMatchConfidence, "minimumMatchConfidence");
		this.suspiciousDiscountPercent = Objects.requireNonNull(suspiciousDiscountPercent, "suspiciousDiscountPercent");
		this.maximumRejectedDealScore = Objects.requireNonNull(maximumRejectedDealScore, "maximumRejectedDealScore");
		if (minimumDiscountPercent.signum() < 0
				|| minimumDealScore.signum() < 0 || minimumDealScore.compareTo(BigDecimal.valueOf(100)) > 0
				|| minimumMatchConfidence.signum() < 0 || minimumMatchConfidence.compareTo(BigDecimal.ONE) > 0
				|| suspiciousDiscountPercent.compareTo(minimumDiscountPercent) <= 0
				|| maximumRejectedDealScore.signum() < 0
				|| maximumRejectedDealScore.compareTo(BigDecimal.valueOf(100)) > 0
				|| maximumRejectedDealScore.compareTo(minimumDealScore) >= 0) {
			throw new IllegalArgumentException("Invalid scoring settings");
		}
	}

	public BigDecimal minimumDiscountPercent() {
		return minimumDiscountPercent;
	}

	public BigDecimal minimumDealScore() {
		return minimumDealScore;
	}

	public BigDecimal minimumMatchConfidence() {
		return minimumMatchConfidence;
	}

	public BigDecimal suspiciousDiscountPercent() {
		return suspiciousDiscountPercent;
	}

	public BigDecimal maximumRejectedDealScore() {
		return maximumRejectedDealScore;
	}
}
