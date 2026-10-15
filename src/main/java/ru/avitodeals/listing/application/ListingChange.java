package ru.avitodeals.listing.application;

import java.math.BigDecimal;

public record ListingChange(
		ListingSnapshot listing,
		boolean created,
		boolean reactivated,
		boolean priceChanged,
		BigDecimal previousPrice,
		String previousCurrency) {
}
