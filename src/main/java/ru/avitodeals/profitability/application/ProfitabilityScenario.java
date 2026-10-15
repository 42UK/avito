package ru.avitodeals.profitability.application;

import java.math.BigDecimal;
import java.util.Objects;

public record ProfitabilityScenario(
		String name,
		BigDecimal resalePrice,
		BigDecimal netProfit,
		BigDecimal roiPercent) {

	public ProfitabilityScenario {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(resalePrice, "resalePrice");
		Objects.requireNonNull(netProfit, "netProfit");
		Objects.requireNonNull(roiPercent, "roiPercent");
	}
}
