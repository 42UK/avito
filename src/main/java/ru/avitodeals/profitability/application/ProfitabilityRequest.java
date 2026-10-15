package ru.avitodeals.profitability.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

public record ProfitabilityRequest(
		BigDecimal purchasePrice,
		BigDecimal resalePrice,
		BigDecimal logisticsCost,
		BigDecimal repairCost,
		BigDecimal sellingFees,
		BigDecimal otherCosts,
		BigDecimal riskReserve,
		BigDecimal minimumRequiredProfit,
		BigDecimal pessimisticResalePrice,
		BigDecimal optimisticResalePrice,
		String currency) {

	private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999.99");

	public ProfitabilityRequest {
		purchasePrice = money(purchasePrice, "purchasePrice");
		resalePrice = money(resalePrice, "resalePrice");
		logisticsCost = money(logisticsCost, "logisticsCost");
		repairCost = money(repairCost, "repairCost");
		sellingFees = money(sellingFees, "sellingFees");
		otherCosts = money(otherCosts, "otherCosts");
		riskReserve = money(riskReserve, "riskReserve");
		minimumRequiredProfit = money(minimumRequiredProfit, "minimumRequiredProfit");
		pessimisticResalePrice = money(pessimisticResalePrice, "pessimisticResalePrice");
		optimisticResalePrice = money(optimisticResalePrice, "optimisticResalePrice");
		if (currency == null || currency.isBlank()) {
			throw new IllegalArgumentException("currency is required");
		}
		currency = currency.trim().toUpperCase(Locale.ROOT);
		try {
			Currency.getInstance(currency);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("currency must be a three-letter ISO currency code", exception);
		}
		if (pessimisticResalePrice.compareTo(resalePrice) > 0
				|| resalePrice.compareTo(optimisticResalePrice) > 0) {
			throw new IllegalArgumentException(
					"resale prices must satisfy pessimisticResalePrice <= resalePrice <= optimisticResalePrice");
		}
		if (purchasePrice.add(logisticsCost).add(repairCost).add(otherCosts).add(riskReserve).signum() <= 0) {
			throw new IllegalArgumentException("total investment must be greater than zero");
		}
	}

	private static BigDecimal money(BigDecimal value, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " is required");
		}
		if (value.signum() < 0) {
			throw new IllegalArgumentException(name + " must not be negative");
		}
		BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
		if (rounded.compareTo(MAX_MONEY) > 0) {
			throw new IllegalArgumentException(name + " exceeds the supported currency amount");
		}
		return rounded;
	}
}
