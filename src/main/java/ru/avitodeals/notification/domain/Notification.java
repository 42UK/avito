package ru.avitodeals.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification", uniqueConstraints = @UniqueConstraint(
		name = "uq_notification_channel_dedup_key", columnNames = {"channel", "dedup_key"}))
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "deal_score_id", nullable = false)
	private UUID dealScoreId;

	@Column(nullable = false, length = 32)
	private String channel;

	@Column(name = "dedup_key", nullable = false, length = 512)
	private String dedupKey;

	@Column(nullable = false, length = 32)
	private String status;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "last_attempt_at")
	private Instant lastAttemptAt;

	@Column(name = "error_code", length = 64)
	private String errorCode;

	@Column(name = "error_message", columnDefinition = "text")
	private String errorMessage;

	protected Notification() {
	}

	public static Notification create(UUID dealScoreId, String channel, String dedupKey) {
		Notification notification = new Notification();
		notification.dealScoreId = dealScoreId;
		notification.channel = channel;
		notification.dedupKey = dedupKey;
		notification.status = "PENDING";
		return notification;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public UUID getDealScoreId() {
		return dealScoreId;
	}

	public void setDealScoreId(UUID dealScoreId) {
		this.dealScoreId = dealScoreId;
	}

	public String getChannel() {
		return channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public String getDedupKey() {
		return dedupKey;
	}

	public void setDedupKey(String dedupKey) {
		this.dedupKey = dedupKey;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public void setSentAt(Instant sentAt) {
		this.sentAt = sentAt;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public void setAttemptCount(int attemptCount) {
		this.attemptCount = attemptCount;
	}

	public Instant getLastAttemptAt() {
		return lastAttemptAt;
	}

	public void setLastAttemptAt(Instant lastAttemptAt) {
		this.lastAttemptAt = lastAttemptAt;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
}
