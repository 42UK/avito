package ru.avitodeals.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "seller", uniqueConstraints = @UniqueConstraint(
		name = "uq_seller_source_external_id", columnNames = {"source", "external_id"}))
public class Seller {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "source", nullable = false, length = 100)
	private String source;

	@Column(name = "external_id", nullable = false, length = 255)
	private String externalId;

	@Column(name = "name", columnDefinition = "text")
	private String name;

	protected Seller() {
	}

	public Seller(String source, String externalId) {
		this.source = source;
		this.externalId = externalId;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getExternalId() {
		return externalId;
	}

	public void setExternalId(String externalId) {
		this.externalId = externalId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
