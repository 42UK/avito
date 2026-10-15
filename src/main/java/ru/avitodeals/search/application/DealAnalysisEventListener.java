package ru.avitodeals.search.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.avitodeals.ingestion.application.ListingIngested;
import ru.avitodeals.ingestion.application.ListingPriceChanged;
import ru.avitodeals.scoring.application.DealAnalysisPipeline;

@Component
public class DealAnalysisEventListener {

	private final DealAnalysisPipeline pipeline;

	public DealAnalysisEventListener(DealAnalysisPipeline pipeline) {
		this.pipeline = pipeline;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onListingIngested(ListingIngested event) {
		pipeline.analyze(event.listingId());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onListingPriceChanged(ListingPriceChanged event) {
		pipeline.analyze(event.listingId());
	}
}
