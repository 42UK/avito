package ru.avitodeals.product.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_attribute")
public class ProductAttribute {

	@EmbeddedId
	private ProductAttributeId id;

	@Column(name = "attribute_value", nullable = false, columnDefinition = "text")
	private String value;

	protected ProductAttribute() {
	}

	public ProductAttribute(ProductAttributeId id, String value) {
		this.id = id;
		this.value = value;
	}

	public ProductAttributeId getId() {
		return id;
	}

	public void setId(ProductAttributeId id) {
		this.id = id;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}
