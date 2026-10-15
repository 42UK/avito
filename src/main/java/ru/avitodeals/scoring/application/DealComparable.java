package ru.avitodeals.scoring.application;

import ru.avitodeals.listing.application.ListingView;

public record DealComparable(ListingView listing, boolean outlier) {
}
