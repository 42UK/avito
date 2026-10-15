package ru.avitodeals.ingestion.application;

import java.util.List;

public interface ListingSource {

	String sourceId();

	List<RawListing> fetch(SearchCriteria criteria);
}
