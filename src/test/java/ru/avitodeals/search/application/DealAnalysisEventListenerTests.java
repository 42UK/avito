package ru.avitodeals.search.application;

import org.junit.jupiter.api.Test;
import ru.avitodeals.ingestion.application.ListingIngested;
import ru.avitodeals.ingestion.application.ListingPriceChanged;
import ru.avitodeals.scoring.application.DealAnalysisPipeline;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DealAnalysisEventListenerTests {

	private final DealAnalysisPipeline pipeline = mock(DealAnalysisPipeline.class);
	private final DealAnalysisEventListener listener = new DealAnalysisEventListener(pipeline);
	private final UUID listingId = UUID.randomUUID();

	@Test
	void analyzesNewListingsAfterTheirIngestionCommit() {
		listener.onListingIngested(new ListingIngested(
				listingId, "avito", "external-1", UUID.randomUUID(), Instant.EPOCH));

		verify(pipeline).analyze(listingId);
	}

	@Test
	void recalculatesAnalysisAfterAnObservedPriceChangeCommits() {
		listener.onListingPriceChanged(new ListingPriceChanged(
				listingId, new BigDecimal("100.00"), "RUB",
				new BigDecimal("90.00"), "RUB", Instant.EPOCH));

		verify(pipeline).analyze(listingId);
	}
}
