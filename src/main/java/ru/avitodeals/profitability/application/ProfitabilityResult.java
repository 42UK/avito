package ru.avitodeals.profitability.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record ProfitabilityResult(
		String currency,
		BigDecimal purchasePrice,
		BigDecimal resalePrice,
		BigDecimal totalInvestment,
		BigDecimal netProfit,
		BigDecimal roiPercent,
		BigDecimal breakEvenPurchasePrice,
		BigDecimal maximumPurchasePrice,
		List<ProfitabilityScenario> scenarios) {

	public ProfitabilityResult {
		Objects.requireNonNull(currency, "currency");
		Objects.requireNonNull(purchasePrice, "purchasePrice");
		Objects.requireNonNull(resalePrice, "resalePrice");
		Objects.requireNonNull(totalInvestment, "totalInvestment");
		Objects.requireNonNull(netProfit, "netProfit");
		Objects.requireNonNull(roiPercent, "roiPercent");
		Objects.requireNonNull(breakEvenPurchasePrice, "breakEvenPurchasePrice");
		Objects.requireNonNull(maximumPurchasePrice, "maximumPurchasePrice");
		scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
	}
}
