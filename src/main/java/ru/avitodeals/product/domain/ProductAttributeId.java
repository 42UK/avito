package ru.avitodeals.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProductAttributeId implements Serializable {

	@Column(name = "product_id", nullable = false)
	private UUID productId;

	@Column(name = "attribute_key", nullable = false, length = 255)
	private String key;

	protected ProductAttributeId() {
	}

	public ProductAttributeId(UUID productId, String key) {
		this.productId = productId;
		this.key = key;
	}

	public UUID getProductId() {
		return productId;
	}

	public String getKey() {
		return key;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof ProductAttributeId that)) {
			return false;
		}
		return Objects.equals(productId, that.productId) && Objects.equals(key, that.key);
	}

	@Override
	public int hashCode() {
		return Objects.hash(productId, key);
	}
}
