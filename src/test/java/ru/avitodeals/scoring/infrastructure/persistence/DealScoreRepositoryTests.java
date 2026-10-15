package ru.avitodeals.scoring.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.avitodeals.scoring.domain.DealScore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:deal-score-repository-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class DealScoreRepositoryTests {

	@Autowired
	private DealScoreRepository repository;

	@Test
	@Transactional
	void findsLatestScoreForListingByTimestampThenIdDescending() {
		UUID listingId = UUID.randomUUID();
		Instant computedAt = Instant.parse("2026-10-08T10:00:00Z");
		repository.saveAndFlush(score(listingId, computedAt));
		repository.saveAndFlush(score(listingId, computedAt));
		List<UUID> orderedIds = repository.findAllByOrderByComputedAtDescIdDesc().stream()
				.filter(score -> listingId.equals(score.getListingId()))
				.map(DealScore::getId).toList();

		assertEquals(orderedIds.getFirst(),
				repository.findFirstByListingIdOrderByComputedAtDescIdDesc(listingId).orElseThrow().getId());
	}

	private static DealScore score(UUID listingId, Instant computedAt) {
		return DealScore.create(listingId, UUID.randomUUID(), BigDecimal.TEN,
				BigDecimal.valueOf(50), BigDecimal.ZERO, BigDecimal.ONE,
				List.of("DEAL"), List.of(), computedAt);
	}
}
