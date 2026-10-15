package ru.avitodeals.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.avitodeals.listing.application.ListingQueries;
import ru.avitodeals.scoring.application.DealQueryService;
import ru.avitodeals.search.application.SearchManagementService;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

	private final SearchManagementService searches;
	private final ListingQueries listings;
	private final DealQueryService deals;

	public StatsController(SearchManagementService searches, ListingQueries listings, DealQueryService deals) {
		this.searches = searches;
		this.listings = listings;
		this.deals = deals;
	}

	@GetMapping
	public StatsResponse stats() {
		return new StatsResponse(searches.count(), searches.countActive(),
				listings.count(), listings.countActive(), deals.count());
	}
}
