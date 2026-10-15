package ru.avitodeals.profitability.application;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ProfitabilityService {

	private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
	private static final int MONEY_SCALE = 2;
	private static final int PERCENT_SCALE = 2;

	public ProfitabilityResult calculate(ProfitabilityRequest request) {
		BigDecimal investment = request.purchasePrice()
				.add(request.logisticsCost())
				.add(request.repairCost())
				.add(request.otherCosts())
				.add(request.riskReserve());
		BigDecimal netProfit = netProfit(request, request.resalePrice());
		BigDecimal breakEvenPurchasePrice = request.resalePrice()
				.subtract(request.logisticsCost())
				.subtract(request.repairCost())
				.subtract(request.sellingFees())
				.subtract(request.otherCosts())
				.subtract(request.riskReserve());
		BigDecimal maximumPurchasePrice = breakEvenPurchasePrice.subtract(request.minimumRequiredProfit());

		List<ProfitabilityScenario> scenarios = List.of(
				scenario("PESSIMISTIC", request.pessimisticResalePrice(), request, investment),
				scenario("BASE", request.resalePrice(), request, investment),
				scenario("OPTIMISTIC", request.optimisticResalePrice(), request, investment));

		return new ProfitabilityResult(
				request.currency(),
				money(request.purchasePrice()),
				money(request.resalePrice()),
				money(investment),
				money(netProfit),
				roi(netProfit, investment),
				money(breakEvenPurchasePrice),
				money(maximumPurchasePrice),
				scenarios);
	}

	private static ProfitabilityScenario scenario(
			String name, BigDecimal resalePrice, ProfitabilityRequest request, BigDecimal investment) {
		BigDecimal profit = netProfit(request, resalePrice);
		return new ProfitabilityScenario(name, money(resalePrice), money(profit), roi(profit, investment));
	}

	private static BigDecimal netProfit(ProfitabilityRequest request, BigDecimal resalePrice) {
		return resalePrice
				.subtract(request.purchasePrice())
				.subtract(request.logisticsCost())
				.subtract(request.repairCost())
				.subtract(request.sellingFees())
				.subtract(request.otherCosts())
				.subtract(request.riskReserve());
	}

	private static BigDecimal roi(BigDecimal netProfit, BigDecimal investment) {
		return netProfit.multiply(ONE_HUNDRED).divide(investment, PERCENT_SCALE, RoundingMode.HALF_UP);
	}

	private static BigDecimal money(BigDecimal amount) {
		return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}
}
