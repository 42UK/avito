package ru.avitodeals.ingestion.infrastructure.avito;

import java.util.List;

public record ParsedFeed(List<ParsedFeedItem> items, Integer totalCount, List<Integer> pageNumbers) {

	public ParsedFeed {
		items = List.copyOf(items);
		pageNumbers = List.copyOf(pageNumbers);
	}
}
