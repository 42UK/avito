package ru.avitodeals.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.avitodeals.ingestion.application.IngestionReport;
import ru.avitodeals.search.application.SearchManagementService;
import ru.avitodeals.search.application.SearchScheduler;
import ru.avitodeals.search.application.SearchView;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/searches")
public class SearchController {

	private final SearchManagementService searches;
	private final SearchScheduler scheduler;

	public SearchController(SearchManagementService searches, SearchScheduler scheduler) {
		this.searches = searches;
		this.scheduler = scheduler;
	}

	@GetMapping
	public PageResponse<SearchView> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return PageResponse.from(searches.list(page, size));
	}

	@GetMapping("/{id}")
	public SearchView get(@PathVariable UUID id) {
		return searches.get(id);
	}

	@PostMapping("/{id}/run")
	public IngestionReport runNow(@PathVariable UUID id) {
		return scheduler.runNow(id);
	}

	@PostMapping
	public ResponseEntity<SearchView> create(@RequestBody SearchRequest request) {
		SearchView created = searches.create(request.toCommand());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
				.buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	public SearchView update(@PathVariable UUID id, @RequestBody SearchRequest request) {
		return searches.update(id, request.toCommand());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		searches.delete(id);
		return ResponseEntity.noContent().build();
	}
}
