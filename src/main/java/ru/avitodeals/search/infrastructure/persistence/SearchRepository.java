package ru.avitodeals.search.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import ru.avitodeals.search.domain.Search;

import java.util.List;
import java.util.UUID;

public interface SearchRepository extends JpaRepository<Search, UUID> {

	List<Search> findByActiveTrue();

	Page<Search> findAll(Pageable pageable);

	long countByActiveTrue();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select search from Search search where search.id = :id")
	java.util.Optional<Search> findByIdForUpdate(@Param("id") UUID id);
}
