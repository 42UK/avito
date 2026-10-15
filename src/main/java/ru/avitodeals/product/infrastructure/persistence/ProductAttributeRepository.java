package ru.avitodeals.product.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.avitodeals.product.domain.ProductAttribute;
import ru.avitodeals.product.domain.ProductAttributeId;

import java.util.List;
import java.util.UUID;

public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, ProductAttributeId> {

	List<ProductAttribute> findByIdProductId(UUID productId);
}
