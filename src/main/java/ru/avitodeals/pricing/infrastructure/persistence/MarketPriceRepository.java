package ru.avitodeals.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.avitodeals.pricing.domain.ConditionGroup;
import ru.avitodeals.pricing.domain.MarketPrice;

import java.util.List;
import java.util.UUID;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, UUID> {

	List<MarketPrice> findByProductIdAndConditionGroupOrderByComputedAtDesc(
			UUID productId, ConditionGroup conditionGroup);
}
