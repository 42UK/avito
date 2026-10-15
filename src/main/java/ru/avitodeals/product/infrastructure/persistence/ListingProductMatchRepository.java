package ru.avitodeals.product.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.avitodeals.product.domain.ListingProductMatch;
import ru.avitodeals.product.domain.MatchStatus;

import java.util.List;
import java.util.UUID;

public interface ListingProductMatchRepository extends JpaRepository<ListingProductMatch, UUID> {

	List<ListingProductMatch> findByProductId(UUID productId);

	List<ListingProductMatch> findByProductIdAndStatus(UUID productId, MatchStatus status);
}
