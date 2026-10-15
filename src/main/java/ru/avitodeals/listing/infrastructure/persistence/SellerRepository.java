package ru.avitodeals.listing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.avitodeals.listing.domain.Seller;

import java.util.Optional;
import java.util.UUID;

public interface SellerRepository extends JpaRepository<Seller, UUID> {

	Optional<Seller> findBySourceAndExternalId(String source, String externalId);
}
