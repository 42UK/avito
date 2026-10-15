package ru.avitodeals.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.avitodeals.profitability.application.ProfitabilityRequest;
import ru.avitodeals.profitability.application.ProfitabilityResult;
import ru.avitodeals.profitability.application.ProfitabilityService;

@RestController
@RequestMapping("/api/profitability")
public class ProfitabilityController {

	private final ProfitabilityService profitability;

	public ProfitabilityController(ProfitabilityService profitability) {
		this.profitability = profitability;
	}

	@PostMapping("/calculate")
	public ProfitabilityResult calculate(@RequestBody ProfitabilityRequest request) {
		return profitability.calculate(request);
	}
}
