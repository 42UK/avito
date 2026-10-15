package ru.avitodeals.ingestion.infrastructure.avito;

import java.net.URI;

@FunctionalInterface
public interface AvitoPageFetcher {

	String fetch(URI uri);
}
