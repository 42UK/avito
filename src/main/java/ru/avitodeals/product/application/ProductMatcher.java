package ru.avitodeals.product.application;

import java.util.UUID;

public interface ProductMatcher {

	ProductMatchResult match(UUID listingId, String title, String description);
}
