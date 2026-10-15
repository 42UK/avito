package ru.avitodeals.search.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class SearchListingId implements Serializable {

	@Column(name = "search_id", nullable = false)
	private UUID searchId;

	@Column(name = "listing_id", nullable = false)
	private UUID listingId;

	protected SearchListingId() {
	}

	public SearchListingId(UUID searchId, UUID listingId) {
		this.searchId = searchId;
		this.listingId = listingId;
	}

	public UUID getSearchId() {
		return searchId;
	}

	public UUID getListingId() {
		return listingId;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof SearchListingId that)) {
			return false;
		}
		return Objects.equals(searchId, that.searchId) && Objects.equals(listingId, that.listingId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(searchId, listingId);
	}
}
