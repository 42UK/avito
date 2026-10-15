package ru.avitodeals.scoring.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.listing.application.ListingCatalog;
import ru.avitodeals.listing.application.ListingNotificationDetails;
import ru.avitodeals.product.application.ExtractedCondition;
import ru.avitodeals.pricing.application.MarketPriceResult;
import ru.avitodeals.scoring.domain.DealScore;
import ru.avitodeals.scoring.infrastructure.persistence.DealScoreRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DealScoringService {

	private static final BigDecimal ZERO = BigDecimal.ZERO;
	private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
	private static final BigDecimal DISCOUNT_SCORE_MULTIPLIER = BigDecimal.valueOf(2);
	private static final BigDecimal MEDIUM_CONFIDENCE_RISK = BigDecimal.valueOf(35);
	private static final BigDecimal UNCERTAIN_MATCH_RISK = BigDecimal.valueOf(60);
	private static final BigDecimal SUSPICIOUS_PRICE_RISK = BigDecimal.valueOf(75);
	private static final BigDecimal HARD_BLOCK_RISK = ONE_HUNDRED;

	private final DealScoringSettings settings;
	private final DealScoreRepository repository;
	private final ListingCatalog listingCatalog;
	private final ApplicationEventPublisher eventPublisher;

	public DealScoringService(
			DealScoringSettings settings,
			DealScoreRepository repository,
			ListingCatalog listingCatalog,
			ApplicationEventPublisher eventPublisher) {
		this.settings = settings;
		this.repository = repository;
		this.listingCatalog = listingCatalog;
		this.eventPublisher = eventPublisher;
	}

	public DealScore score(DealScoringInput input) {
		return score(input, Instant.now());
	}

	public DealScore score(DealScoringInput input, Instant computedAt) {
		ScoringState state = evaluate(input);
		return toDealScore(input, computedAt, state);
	}

	@Transactional
	public DealScore scoreAndSave(DealScoringInput input) {
		return scoreAndSave(input, Instant.now());
	}

	@Transactional
	public DealScore scoreAndSave(DealScoringInput input, Instant computedAt) {
		DealScore score = score(input, computedAt);
		DealScore savedScore = repository.save(score);
		ListingNotificationDetails listing = listingCatalog.findNotificationDetails(input.listingId())
				.orElseThrow(() -> new IllegalStateException(
						"Listing details are unavailable for scored listing " + input.listingId()));
		eventPublisher.publishEvent(new DealScored(
				savedScore.getId(),
				input.listingId(),
				listing.title(),
				listing.url(),
				input.candidatePrice(),
				input.candidateCurrency(),
				savedScore.getDiscountPercent(),
				savedScore.getDealScore(),
				savedScore.getRiskScore(),
				savedScore.getFlags(),
				savedScore.getComputedAt()));
		return savedScore;
	}

	private ScoringState evaluate(DealScoringInput input) {
		List<Map<String, Object>> explanation = new ArrayList<>();
		Set<String> flags = new LinkedHashSet<>();
		MarketPriceResult marketPrice = input.persistedMarketPrice().result();
		String marketCondition = input.persistedMarketPrice().conditionCode();
		BigDecimal marketValue = marketPrice.marketPrice();
		boolean hasMarketReference = marketValue.signum() > 0;
		BigDecimal discountPercent = hasMarketReference
				? marketValue.subtract(input.candidatePrice())
						.multiply(ONE_HUNDRED)
						.divide(marketValue, 2, RoundingMode.HALF_UP)
				: ZERO.setScale(2);
		BigDecimal rawDealScore = clamp(discountPercent.multiply(DISCOUNT_SCORE_MULTIPLIER));
		BigDecimal confidence = marketPrice.confidenceScore()
				.multiply(input.productMatch().confidence())
				.setScale(4, RoundingMode.HALF_UP);
		BigDecimal riskScore = ZERO.setScale(2);

		addReason(explanation, "MARKET_PRICE", Map.of(
				"marketPriceId", input.persistedMarketPrice().marketPriceId().toString(),
				"productId", marketPrice.productId().toString(),
				"marketPrice", marketValue,
				"currency", marketPrice.currency()),
				"Рыночная цена взята из сохранённого снимка; минимальная цена comparables не используется.");
		if (hasMarketReference) {
			addReason(explanation, "DISCOUNT_CALCULATED", Map.of(
					"candidatePrice", input.candidatePrice(),
					"marketPrice", marketValue,
					"discountPercent", discountPercent),
					"Скидка рассчитана относительно marketPrice: (рынок − цена объявления) / рынок × 100.");
		} else {
			addFlag(flags, "MARKET_PRICE_UNAVAILABLE");
			riskScore = max(riskScore, HARD_BLOCK_RISK);
			addReason(explanation, "MARKET_PRICE_UNAVAILABLE", Map.of("marketPrice", marketValue),
					"У сохранённого снимка нет положительной рыночной цены, поэтому скидку вычислить нельзя.");
		}

		addReason(explanation, "PRICE_CONFIDENCE", Map.of(
				"level", marketPrice.confidenceLevelCode(),
				"score", marketPrice.confidenceScore(),
				"sampleSize", marketPrice.sampleSize()),
				"Уверенность цены учитывает confidenceLevel и confidenceScore сохранённого снимка.");
		boolean hasPriceConfidence = marketPrice.confidenceScore().signum() > 0;
		if (!hasPriceConfidence) {
			addFlag(flags, "PRICE_CONFIDENCE_UNAVAILABLE");
			riskScore = max(riskScore, HARD_BLOCK_RISK);
			addReason(explanation, "PRICE_CONFIDENCE_UNAVAILABLE", Map.of(
					"level", marketPrice.confidenceLevelCode(),
					"score", marketPrice.confidenceScore()),
					"Числовая уверенность ценового снимка равна нулю; сделка заблокирована.");
		}
		addReason(explanation, "PRODUCT_MATCH", Map.of(
				"status", input.productMatch().statusCode(),
				"productId", productId(input),
				"confidence", input.productMatch().confidence(),
				"minimumConfidence", settings.minimumMatchConfidence(),
				"matcherVersion", input.productMatch().matcherVersion()),
				"Решение требует точного MATCHED с canonical product из того же ценового снимка.");
		addReason(explanation, "CONDITION_COMPARISON", Map.of(
				"candidateCondition", input.candidateCondition(),
				"marketCondition", marketCondition),
				"Состояние объявления сравнивается с состоянием, сохранённым для снимка рынка.");
		addReason(explanation, "CURRENCY_COMPARISON", Map.of(
				"candidateCurrency", normalizedCurrency(input.candidateCurrency()),
				"marketCurrency", normalizedCurrency(marketPrice.currency())),
				"Сравнение разрешено только в одной валюте.");

		boolean defective = input.attributes().defective()
				|| input.attributes().forParts()
				|| input.attributes().condition() == ExtractedCondition.DEFECTIVE
				|| !input.attributes().defects().isEmpty()
				|| input.candidateCondition().equals("DEFECTIVE");
		boolean forParts = input.attributes().forParts();
		if (defective) {
			addFlag(flags, "DEFECTIVE");
			riskScore = max(riskScore, HARD_BLOCK_RISK);
			addReason(explanation, "DEFECTIVE", Map.of(
					"condition", input.candidateCondition(),
					"defectFlags", input.attributes().defects().stream().sorted().toList()),
					"Найден функциональный дефект или состояние DEFECTIVE: высокий deal score запрещён.");
		}
		if (forParts) {
			addFlag(flags, "FOR_PARTS");
			riskScore = max(riskScore, HARD_BLOCK_RISK);
			addReason(explanation, "FOR_PARTS", Map.of("forParts", true),
					"Объявление помечено «на запчасти»; выдача сделки запрещена.");
		}
		if (input.attributes().accessory()) {
			addFlag(flags, "ACCESSORY");
			riskScore = max(riskScore, HARD_BLOCK_RISK);
			addReason(explanation, "ACCESSORY", Map.of("accessory", true),
					"Аксессуар не может оцениваться как сделка на целевой товар.");
		}

		String matchStatus = input.productMatch().statusCode();
		boolean exactProductMatch = matchStatus.equals("MATCHED")
				&& input.productMatch().productId() != null
				&& input.productMatch().productId().equals(marketPrice.productId());
		if (!exactProductMatch) {
			addFlag(flags, "PRODUCT_MISMATCH");
			riskScore = max(riskScore, HARD_BLOCK_RISK);
			addReason(explanation, "PRODUCT_MISMATCH", Map.of(
					"matchStatus", matchStatus,
					"matchedProductId", productId(input),
					"marketProductId", marketPrice.productId().toString()),
					"Нет точного MATCHED canonical product, совпадающего с продуктом снимка рынка.");
		} else if (input.productMatch().confidence().compareTo(settings.minimumMatchConfidence()) < 0) {
			addFlag(flags, "LOW_MATCH_CONFIDENCE");
			riskScore = max(riskScore, UNCERTAIN_MATCH_RISK);
			addReason(explanation, "LOW_MATCH_CONFIDENCE", Map.of(
					"confidence", input.productMatch().confidence(),
					"minimumConfidence", settings.minimumMatchConfidence()),
					"Уверенность сопоставления ниже настроенного порога точного матча.");
		}

		boolean currencyMatches = normalizedCurrency(input.candidateCurrency())
				.equals(normalizedCurrency(marketPrice.currency()));
		if (!currencyMatches) {
			addFlag(flags, "CURRENCY_MISMATCH");
			riskScore = max(riskScore, HARD_BLOCK_RISK);
			addReason(explanation, "CURRENCY_MISMATCH", Map.of(
					"candidateCurrency", normalizedCurrency(input.candidateCurrency()),
					"marketCurrency", normalizedCurrency(marketPrice.currency())),
					"Валюта объявления не совпадает с валютой снимка рынка.");
		}

		boolean conditionMatches = input.candidateCondition().equals(marketCondition);
		if (!conditionMatches) {
			addFlag(flags, "CONDITION_MISMATCH");
			riskScore = max(riskScore, HARD_BLOCK_RISK);
			addReason(explanation, "CONDITION_MISMATCH", Map.of(
					"candidateCondition", input.candidateCondition(),
					"marketCondition", marketCondition),
					"Состояние объявления не совпадает с состоянием comparables снимка рынка.");
		}

		String confidenceLevel = marketPrice.confidenceLevelCode();
		if (confidenceLevel.equals("LOW")) {
			addFlag(flags, "LOW_CONFIDENCE");
			riskScore = max(riskScore, HARD_BLOCK_RISK);
			addReason(explanation, "LOW_CONFIDENCE", Map.of(
					"level", confidenceLevel,
					"sampleSize", marketPrice.sampleSize()),
					"LOW confidence запрещает выдачу сделки независимо от величины скидки.");
		} else if (confidenceLevel.equals("MEDIUM")) {
			addFlag(flags, "MEDIUM_CONFIDENCE");
			riskScore = max(riskScore, MEDIUM_CONFIDENCE_RISK);
			addReason(explanation, "MEDIUM_CONFIDENCE", Map.of(
					"level", confidenceLevel,
					"sampleSize", marketPrice.sampleSize()),
					"MEDIUM confidence не проходит высокий confidence gate сделки и требует проверки.");
		}

		boolean suspiciousDiscount = discountPercent.compareTo(settings.suspiciousDiscountPercent()) >= 0;
		if (suspiciousDiscount) {
			addFlag(flags, "SUSPICIOUSLY_LOW_PRICE");
			riskScore = max(riskScore, SUSPICIOUS_PRICE_RISK);
			addReason(explanation, "SUSPICIOUSLY_LOW_PRICE", Map.of(
					"discountPercent", discountPercent,
					"thresholdPercent", settings.suspiciousDiscountPercent()),
					"Чрезмерная скидка может быть признаком ошибки или скрытого риска; требуется ручная проверка.");
		}

		Decision decision = determineDecision(input, exactProductMatch, defective, currencyMatches,
				conditionMatches, hasMarketReference, hasPriceConfidence, suspiciousDiscount,
				discountPercent, rawDealScore);
		if (decision != Decision.DEAL) {
			rawDealScore = rawDealScore.min(settings.maximumRejectedDealScore());
		}
		addFlag(flags, decision.name());
		addReason(explanation, decision.name(), Map.of(
				"minimumDiscountPercent", settings.minimumDiscountPercent(),
				"minimumDealScore", settings.minimumDealScore(),
				"dealScore", rawDealScore,
				"riskScore", riskScore),
				decisionText(decision));
		return new ScoringState(discountPercent, rawDealScore, riskScore, confidence, flags, explanation);
	}

	private Decision determineDecision(
			DealScoringInput input,
			boolean exactProductMatch,
			boolean defective,
			boolean currencyMatches,
			boolean conditionMatches,
			boolean hasMarketReference,
			boolean hasPriceConfidence,
			boolean suspiciousDiscount,
			BigDecimal discountPercent,
			BigDecimal dealScore) {
		if (defective || input.attributes().forParts() || input.attributes().accessory()) {
			return Decision.BLOCKED;
		}
		if (input.productMatch().statusCode().equals("UNMATCHED")) {
			return Decision.BLOCKED;
		}
		if (input.productMatch().statusCode().equals("AMBIGUOUS")
				|| input.productMatch().confidence().compareTo(settings.minimumMatchConfidence()) < 0) {
			return Decision.REVIEW;
		}
		if (!exactProductMatch || !currencyMatches || !conditionMatches || !hasMarketReference
				|| !hasPriceConfidence
				|| input.persistedMarketPrice().result().confidenceLevelCode().equals("LOW")) {
			return Decision.BLOCKED;
		}
		if (input.candidateCondition().equals("UNKNOWN")
				|| input.persistedMarketPrice().conditionCode().equals("UNKNOWN")
				|| input.persistedMarketPrice().result().confidenceLevelCode().equals("MEDIUM")
				|| suspiciousDiscount) {
			return Decision.REVIEW;
		}
		if (discountPercent.compareTo(settings.minimumDiscountPercent()) < 0
				|| dealScore.compareTo(settings.minimumDealScore()) < 0) {
			return Decision.NO_DEAL;
		}
		return Decision.DEAL;
	}

	private static DealScore toDealScore(DealScoringInput input, Instant computedAt, ScoringState state) {
		return DealScore.create(
				input.listingId(), input.persistedMarketPrice().marketPriceId(),
				state.discountPercent(), state.dealScore(),
				state.riskScore(), state.confidence(), List.copyOf(state.flags()),
				List.copyOf(state.explanation()), computedAt);
	}

	private static void addReason(
			List<Map<String, Object>> explanation, String code, Map<String, Object> parameters, String text) {
		explanation.add(Map.of("code", code, "parameters", parameters, "text", text));
	}

	private static void addFlag(Set<String> flags, String code) {
		flags.add(code);
	}

	private static BigDecimal clamp(BigDecimal value) {
		if (value.signum() < 0) {
			return ZERO.setScale(2);
		}
		return value.min(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal max(BigDecimal left, BigDecimal right) {
		return left.max(right).setScale(2, RoundingMode.HALF_UP);
	}

	private static String normalizedCurrency(String currency) {
		return currency.toUpperCase(java.util.Locale.ROOT);
	}

	private static String productId(DealScoringInput input) {
		return input.productMatch().productId() == null ? "NONE" : input.productMatch().productId().toString();
	}

	private static String decisionText(Decision decision) {
		return switch (decision) {
			case DEAL -> "Все обязательные gates пройдены: точный товар, HIGH confidence, совпадающие валюта и состояние, скидка выше порога.";
			case NO_DEAL -> "Достаточно надёжные данные не подтверждают минимально необходимую скидку и deal score.";
			case BLOCKED -> "Жёсткий gate запрещает сделку: товар, риск, валюта, состояние или уверенность цены не подходят.";
			case REVIEW -> "Автоматическая сделка запрещена; неоднозначность или риск требует ручной проверки.";
		};
	}

	private enum Decision {
		DEAL,
		NO_DEAL,
		BLOCKED,
		REVIEW
	}

	private record ScoringState(
			BigDecimal discountPercent,
			BigDecimal dealScore,
			BigDecimal riskScore,
			BigDecimal confidence,
			Set<String> flags,
			List<Map<String, Object>> explanation) {
	}
}
