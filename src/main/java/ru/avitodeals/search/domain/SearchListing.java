package ru.avitodeals.search.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import jakarta.persistence.Column;

import java.time.Instant;

@Entity
@Table(name = "search_listing",
		indexes = @Index(name = "idx_search_listing_listing_id", columnList = "listing_id"))
public class SearchListing {

	@EmbeddedId
	private SearchListingId id;

	@Column(name = "first_seen_at", nullable = false)
	private Instant firstSeenAt;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	protected SearchListing() {
	}

	public SearchListing(SearchListingId id, Instant firstSeenAt, Instant lastSeenAt) {
		this.id = id;
		this.firstSeenAt = firstSeenAt;
		this.lastSeenAt = lastSeenAt;
	}

	public SearchListingId getId() {
		return id;
	}

	public void setId(SearchListingId id) {
		this.id = id;
	}

	public Instant getFirstSeenAt() {
		return firstSeenAt;
	}

	public void setFirstSeenAt(Instant firstSeenAt) {
		this.firstSeenAt = firstSeenAt;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public void setLastSeenAt(Instant lastSeenAt) {
		this.lastSeenAt = lastSeenAt;
	}
}
