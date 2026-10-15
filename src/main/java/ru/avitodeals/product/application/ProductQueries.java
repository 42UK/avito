package ru.avitodeals.product.application;

import java.util.List;
import java.util.UUID;

public interface ProductQueries {

	List<UUID> matchedListingIds(UUID productId);
}
