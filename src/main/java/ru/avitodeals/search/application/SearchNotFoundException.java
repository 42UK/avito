package ru.avitodeals.search.application;

import java.util.UUID;

public class SearchNotFoundException extends RuntimeException {

	public SearchNotFoundException(UUID id) {
		super("Search not found: " + id);
	}
}
