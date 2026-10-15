package ru.avitodeals.search.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "search")
public class Search {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, columnDefinition = "text")
	private String name;

	@Column(nullable = false, columnDefinition = "text")
	private String query;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private Map<String, Object> filters = new HashMap<>();

	@Column(name = "min_deal_score", nullable = false, precision = 5, scale = 2)
	private BigDecimal minDealScore = BigDecimal.ZERO;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "interval_minutes", nullable = false)
	private int intervalMinutes;

	@Column(name = "last_run_at")
	private Instant lastRunAt;

	protected Search() {
	}

	public Search(
			String name,
			String query,
			Map<String, Object> filters,
			BigDecimal minDealScore,
			boolean active,
			int intervalMinutes) {
		this.name = name;
		this.query = query;
		this.filters = filters == null ? new HashMap<>() : new HashMap<>(filters);
		this.minDealScore = minDealScore;
		this.active = active;
		this.intervalMinutes = intervalMinutes;
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public Map<String, Object> getFilters() {
		return filters;
	}

	public void setFilters(Map<String, Object> filters) {
		this.filters = filters;
	}

	public BigDecimal getMinDealScore() {
		return minDealScore;
	}

	public void setMinDealScore(BigDecimal minDealScore) {
		this.minDealScore = minDealScore;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public int getIntervalMinutes() {
		return intervalMinutes;
	}

	public void setIntervalMinutes(int intervalMinutes) {
		this.intervalMinutes = intervalMinutes;
	}

	public Instant getLastRunAt() {
		return lastRunAt;
	}

	public void setLastRunAt(Instant lastRunAt) {
		this.lastRunAt = lastRunAt;
	}
}
