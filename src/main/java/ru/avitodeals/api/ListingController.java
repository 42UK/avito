package ru.avitodeals.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.avitodeals.listing.application.ListingQueries;
import ru.avitodeals.listing.application.ListingView;

import java.util.UUID;

@RestController
@RequestMapping("/api/listings")
public class ListingController {

	private final ListingQueries listings;

	public ListingController(ListingQueries listings) {
		this.listings = listings;
	}

	@GetMapping
	public PageResponse<ListingView> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(listings.list(page, size));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ListingView> get(@PathVariable UUID id) {
		return listings.find(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}
}
