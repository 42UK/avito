package ru.avitodeals.notification.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.avitodeals.notification.domain.Notification;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	Optional<Notification> findByChannelAndDedupKey(String channel, String dedupKey);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select notification from Notification notification "
			+ "where notification.channel = :channel and notification.dedupKey = :dedupKey")
	Optional<Notification> findByChannelAndDedupKeyForUpdate(
			@Param("channel") String channel,
			@Param("dedupKey") String dedupKey);
}
