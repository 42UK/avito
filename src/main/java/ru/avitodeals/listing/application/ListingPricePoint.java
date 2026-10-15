package ru.avitodeals.listing.application;

import java.math.BigDecimal;
import java.time.Instant;

public record ListingPricePoint(BigDecimal price, String currency, Instant observedAt) {
}
