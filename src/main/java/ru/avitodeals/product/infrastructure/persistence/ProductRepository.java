package ru.avitodeals.product.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.avitodeals.product.domain.Product;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

	Optional<Product> findByCanonicalKey(String canonicalKey);
}
