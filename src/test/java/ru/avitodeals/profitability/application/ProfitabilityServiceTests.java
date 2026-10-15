package ru.avitodeals.profitability.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfitabilityServiceTests {

	private final ProfitabilityService service = new ProfitabilityService();

	@Test
	void calculatesNetProfitRoiBreakEvenAndPurchaseLimitWithoutDoubleCounting() {
		ProfitabilityResult result = service.calculate(request(
				"800", "1200", "25", "50", "60", "10", "20", "35", "1000", "1500"));

		assertMoney("905.00", result.totalInvestment());
		assertMoney("235.00", result.netProfit());
		assertMoney("25.97", result.roiPercent());
		assertMoney("1035.00", result.breakEvenPurchasePrice());
		assertMoney("1000.00", result.maximumPurchasePrice());
		assertMoney("35.00", result.scenarios().get(0).netProfit());
		assertMoney("235.00", result.scenarios().get(1).netProfit());
		assertMoney("535.00", result.scenarios().get(2).netProfit());
		assertEquals("RUB", result.currency());
	}

	@Test
	void keepsLossesNegativeAndAllowsNegativeBreakEvenPrice() {
		ProfitabilityResult result = service.calculate(request(
				"2000", "1000", "100", "200", "100", "50", "50", "0", "900", "1100"));

		assertMoney("-1500.00", result.netProfit());
		assertMoney("-62.50", result.roiPercent());
		assertMoney("500.00", result.breakEvenPurchasePrice());
		assertMoney("-1600.00", result.scenarios().get(0).netProfit());
	}

	@Test
	void rejectsNegativeCostsAndInconsistentScenarios() {
		assertThrows(IllegalArgumentException.class, () -> request(
				"100", "200", "-1", "0", "0", "0", "0", "0", "100", "300"));
		assertThrows(IllegalArgumentException.class, () -> request(
				"100", "200", "0", "0", "0", "0", "0", "0", "250", "300"));
	}

	@Test
	void rejectsZeroInvestmentBecauseRoiWouldBeUndefined() {
		assertThrows(IllegalArgumentException.class, () -> new ProfitabilityRequest(
				money("0"), money("100"), money("0"), money("0"), money("0"), money("0"),
				money("0"), money("0"), money("50"), money("150"), "RUB"));
	}

	@Test
	void roundsMoneyToCentsAndRejectsUnknownCurrencyCodes() {
		ProfitabilityRequest request = new ProfitabilityRequest(
				new BigDecimal("100.005"), money("200"), money("0"), money("0"), money("0"),
				money("0"), money("0"), money("0"), money("100"), money("300"), "rub");
		assertMoney("100.01", request.purchasePrice());
		assertThrows(IllegalArgumentException.class, () -> new ProfitabilityRequest(
				money("100"), money("200"), money("0"), money("0"), money("0"),
				money("0"), money("0"), money("0"), money("100"), money("300"), "ZZZ"));
	}

	private static ProfitabilityRequest request(
			String purchase, String resale, String logistics, String repair, String fees,
			String other, String reserve, String minimumProfit, String pessimistic, String optimistic) {
		return new ProfitabilityRequest(
				money(purchase), money(resale), money(logistics), money(repair), money(fees),
				money(other), money(reserve), money(minimumProfit), money(pessimistic),
				money(optimistic), "rub");
	}

	private static BigDecimal money(String value) {
		return new BigDecimal(value);
	}

	private static void assertMoney(String expected, BigDecimal actual) {
		assertEquals(0, money(expected).compareTo(actual), () -> "Expected " + expected + " but was " + actual);
	}
}
