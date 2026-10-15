package ru.avitodeals.ingestion.application;

import java.util.List;
import ru.avitodeals.listing.application.DataOrigin;

public interface ListingSource {

	String sourceId();

	default DataOrigin dataOrigin() {
		return DataOrigin.DEMO;
	}

	List<RawListing> fetch(SearchCriteria criteria);
}
