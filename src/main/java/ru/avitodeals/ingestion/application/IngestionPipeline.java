package ru.avitodeals.ingestion.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class IngestionPipeline {

	private final ListingSource source;
	private final IngestionPersistence persistence;
	private final Clock clock;

	@Autowired
	public IngestionPipeline(
			ListingSource source,
			IngestionPersistence persistence,
			Clock clock) {
		this.source = source;
		this.persistence = persistence;
		this.clock = clock;
	}

	IngestionPipeline(
			ListingSource source,
			ru.avitodeals.listing.application.ListingCatalog catalog,
			ApplicationEventPublisher eventPublisher,
			Clock clock) {
		this(source, new IngestionPersistence(catalog, eventPublisher, clock), clock);
	}

	/**
	 * Fetches outside a database transaction. Persistence starts only after the source responds.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public IngestionReport ingest(IngestionRequest request) {
		List<RawListing> fetched = source.fetch(request.criteria());
		return persistence.persist(source.sourceId(), source.dataOrigin(), request, fetched, clock.instant());
	}
}
