package ru.avitodeals.api;

public record StatsResponse(long searches, long activeSearches, long listings, long activeListings, long deals) {
}
