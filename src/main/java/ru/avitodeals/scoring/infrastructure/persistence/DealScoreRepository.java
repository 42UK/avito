package ru.avitodeals.scoring.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.avitodeals.scoring.domain.DealScore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DealScoreRepository extends JpaRepository<DealScore, UUID> {

	Optional<DealScore> findFirstByListingIdOrderByComputedAtDescIdDesc(UUID listingId);

	List<DealScore> findAllByOrderByComputedAtDescIdDesc();
}
