package ru.avitodeals.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.avitodeals.scoring.application.DealCard;
import ru.avitodeals.scoring.application.DealDetails;
import ru.avitodeals.scoring.application.DealQueryService;

import java.util.UUID;

@RestController
@RequestMapping("/api/deals")
public class DealController {

	private final DealQueryService deals;

	public DealController(DealQueryService deals) {
		this.deals = deals;
	}

	@GetMapping
	public PageResponse<DealCard> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(deals.list(page, size));
	}

	@GetMapping("/{id}")
	public ResponseEntity<DealDetails> get(@PathVariable UUID id) {
		return deals.find(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}
}
