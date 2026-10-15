package ru.avitodeals.pricing.application;

import java.util.Optional;
import java.util.UUID;

public interface MarketPriceQueries {

	Optional<MarketPriceView> find(UUID id);
}
