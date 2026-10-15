package ru.avitodeals;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import ru.avitodeals.listing.domain.Listing;
import ru.avitodeals.listing.domain.ListingCondition;
import ru.avitodeals.listing.domain.ListingPriceHistory;
import ru.avitodeals.listing.domain.ListingStatus;
import ru.avitodeals.listing.domain.Seller;
import ru.avitodeals.notification.domain.Notification;
import ru.avitodeals.pricing.domain.ConfidenceLevel;
import ru.avitodeals.pricing.domain.MarketPrice;
import ru.avitodeals.product.domain.ListingProductMatch;
import ru.avitodeals.product.domain.MatchStatus;
import ru.avitodeals.product.domain.Product;
import ru.avitodeals.product.domain.ProductAttribute;
import ru.avitodeals.scoring.domain.DealScore;
import ru.avitodeals.search.domain.Search;
import ru.avitodeals.search.domain.SearchListing;
import ru.avitodeals.search.domain.SearchListingId;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceMappingTests {

	private static final List<Class<?>> ENTITIES = List.of(
			Seller.class, Listing.class, ListingPriceHistory.class, Search.class, SearchListing.class,
			Product.class, ProductAttribute.class, ListingProductMatch.class, MarketPrice.class,
			DealScore.class, Notification.class);

	@Test
	void everyV1TableHasAnEntityMapping() {
		assertEquals(11, ENTITIES.size());
		assertTrue(ENTITIES.stream().allMatch(type -> type.isAnnotationPresent(Entity.class)));
		assertTrue(ENTITIES.stream().allMatch(type -> type.isAnnotationPresent(Table.class)));
		assertEquals(List.of("seller", "listing", "listing_price_history", "search", "search_listing",
						"product", "product_attribute", "listing_product_match", "market_price",
						"deal_score", "notification"),
				ENTITIES.stream().map(type -> type.getAnnotation(Table.class).name()).toList());
	}

	@Test
	void compositeKeyTypesHaveValueEqualityAndMapTheirColumns() throws Exception {
		UUID searchId = UUID.randomUUID();
		UUID listingId = UUID.randomUUID();
		SearchListingId first = new SearchListingId(searchId, listingId);
		SearchListingId equal = new SearchListingId(searchId, listingId);

		assertEquals(first, equal);
		assertEquals(first.hashCode(), equal.hashCode());
		assertNotEquals(first, new SearchListingId(UUID.randomUUID(), listingId));
		assertEquals("search_id", field(SearchListingId.class, "searchId").getAnnotation(Column.class).name());
		assertEquals("listing_id", field(SearchListingId.class, "listingId").getAnnotation(Column.class).name());
		assertTrue(field(SearchListing.class, "id").isAnnotationPresent(EmbeddedId.class));

		assertTrue(field(ProductAttribute.class, "id").isAnnotationPresent(EmbeddedId.class));
	}

	@Test
	void enumsUseTheirDatabaseStringValuesAndMoneyKeepsItsPrecision() throws Exception {
		assertEquals(EnumType.STRING, field(Listing.class, "condition").getAnnotation(Enumerated.class).value());
		assertEquals(EnumType.STRING, field(Listing.class, "status").getAnnotation(Enumerated.class).value());
		assertEquals(EnumType.STRING,
				field(ListingProductMatch.class, "status").getAnnotation(Enumerated.class).value());
		assertEquals(EnumType.STRING,
				field(MarketPrice.class, "confidenceLevel").getAnnotation(Enumerated.class).value());

		Column listingPrice = field(Listing.class, "price").getAnnotation(Column.class);
		assertEquals(14, listingPrice.precision());
		assertEquals(2, listingPrice.scale());
		Column discount = field(DealScore.class, "discountPercent").getAnnotation(Column.class);
		assertEquals(19, discount.precision());
		assertEquals(2, discount.scale());
		assertEquals(List.of(ListingCondition.values()), List.of(
				ListingCondition.NEW, ListingCondition.GOOD, ListingCondition.FAIR,
				ListingCondition.DEFECTIVE, ListingCondition.UNKNOWN));
		assertEquals(List.of(ListingStatus.values()), List.of(ListingStatus.ACTIVE, ListingStatus.REMOVED));
		assertEquals(List.of(MatchStatus.values()), List.of(MatchStatus.MATCHED, MatchStatus.UNMATCHED,
				MatchStatus.AMBIGUOUS));
		assertEquals(List.of(ConfidenceLevel.values()), List.of(ConfidenceLevel.LOW,
				ConfidenceLevel.MEDIUM, ConfidenceLevel.HIGH));
	}

	@Test
	void jsonbFieldsAreExplicitlyTypedAndInterModuleReferencesRemainUuidValues() throws Exception {
		assertEquals(SqlTypes.JSON, field(Search.class, "filters").getAnnotation(JdbcTypeCode.class).value());
		assertEquals(SqlTypes.JSON, field(DealScore.class, "flags").getAnnotation(JdbcTypeCode.class).value());
		assertEquals(SqlTypes.JSON,
				field(DealScore.class, "explanation").getAnnotation(JdbcTypeCode.class).value());
		assertEquals(SqlTypes.JSON,
				field(MarketPrice.class, "outliers").getAnnotation(JdbcTypeCode.class).value());

		assertEquals(UUID.class, field(Listing.class, "sellerId").getType());
		assertEquals(UUID.class, field(SearchListingId.class, "listingId").getType());
		assertEquals(UUID.class, field(ListingProductMatch.class, "listingId").getType());
		assertEquals(UUID.class, field(MarketPrice.class, "productId").getType());
		assertEquals(UUID.class, field(DealScore.class, "listingId").getType());
		assertEquals(UUID.class, field(DealScore.class, "marketPriceId").getType());
		assertEquals(UUID.class, field(Notification.class, "dealScoreId").getType());
		assertTrue(Map.class.isAssignableFrom(field(Search.class, "filters").getType()));
		assertFalse(List.class.isAssignableFrom(field(Search.class, "filters").getType()));
		assertEquals(UUID.class,
				((ParameterizedType) field(MarketPrice.class, "outliers").getGenericType())
						.getActualTypeArguments()[0]);
	}

	private static Field field(Class<?> type, String name) throws NoSuchFieldException {
		return type.getDeclaredField(name);
	}
}
