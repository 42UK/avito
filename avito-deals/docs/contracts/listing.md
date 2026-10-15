# Contract: Listing
Status: Draft
Поля: id, source, externalId, title, description, price{amount,currency}, condition(NEW|GOOD|FAIR|DEFECTIVE|UNKNOWN), location, url, seller, status, firstSeenAt, lastSeenAt.
Порт: ListingSource { fetch(SearchCriteria): List<RawListing> } — RawListing не содержит деталей источника.
События: ListingIngested, ListingPriceChanged, ListingRemoved.
