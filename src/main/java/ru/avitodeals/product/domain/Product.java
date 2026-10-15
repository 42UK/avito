package ru.avitodeals.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "product", uniqueConstraints = @UniqueConstraint(
		name = "uq_product_canonical_key", columnNames = "canonical_key"))
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "canonical_key", nullable = false, length = 512)
	private String canonicalKey;

	@Column(columnDefinition = "text")
	private String brand;

	@Column(columnDefinition = "text")
	private String model;

	@Column(columnDefinition = "text")
	private String category;

	protected Product() {
	}

	public Product(String canonicalKey, String brand, String model, String category) {
		this.canonicalKey = canonicalKey;
		this.brand = brand;
		this.model = model;
		this.category = category;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getCanonicalKey() {
		return canonicalKey;
	}

	public void setCanonicalKey(String canonicalKey) {
		this.canonicalKey = canonicalKey;
	}

	public String getBrand() {
		return brand;
	}

	public void setBrand(String brand) {
		this.brand = brand;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}
}
