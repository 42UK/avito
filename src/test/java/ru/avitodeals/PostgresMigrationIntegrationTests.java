package ru.avitodeals;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationIntegrationTests {
	@Container
	private static final PostgreSQLContainer<?> postgres =
			new PostgreSQLContainer<>("postgres:16-alpine");

	@Test
	void appliesVersionedMigrationsAndBackfillsListingDataOrigin() throws Exception {
		Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.locations("classpath:db/migration")
				.target("3")
				.load()
				.migrate();

		try (var connection = DriverManager.getConnection(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
			 var statement = connection.createStatement()) {
			statement.executeUpdate("""
					INSERT INTO listing
					    (source, external_id, title, price, currency, condition, url, status,
					     first_seen_at, last_seen_at)
					VALUES
					    ('avito', 'legacy-real', 'Legacy Avito listing', 100, 'RUB', 'UNKNOWN',
					     'https://www.avito.ru/item/legacy-real', 'ACTIVE', now(), now()),
					    ('mock', 'legacy-demo', 'Legacy mock listing', 100, 'RUB', 'UNKNOWN',
					     'https://example.invalid/legacy-demo', 'ACTIVE', now(), now())
					""");
		}

		Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.locations("classpath:db/migration")
				.load()
				.migrate();

		try (var connection = DriverManager.getConnection(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
			 var statement = connection.createStatement();
			 var resultSet = statement.executeQuery("""
					 SELECT table_name
					 FROM information_schema.tables
					 WHERE table_schema = 'public'
					   AND table_name IN ('flyway_schema_history', 'listing',
					                      'listing_price_history', 'market_price', 'deal_score',
					                      'notification')
					 """)) {
			Set<String> tables = new HashSet<>();
			while (resultSet.next()) {
				tables.add(resultSet.getString("table_name"));
			}

			assertEquals(Set.of("flyway_schema_history", "listing", "listing_price_history",
					"market_price", "deal_score", "notification"), tables);
			try (var originColumn = statement.executeQuery("""
					SELECT column_name
					FROM information_schema.columns
					WHERE table_schema = 'public'
					  AND table_name = 'listing'
					  AND column_name = 'data_origin'
					""")) {
				assertTrue(originColumn.next());
			}
			try (var origins = statement.executeQuery("""
					SELECT source, data_origin
					FROM listing
					ORDER BY external_id
					""")) {
				Map<String, String> actualOrigins = new java.util.HashMap<>();
				while (origins.next()) {
					actualOrigins.put(origins.getString("source"), origins.getString("data_origin"));
				}
				assertEquals(Map.of("avito", "REAL", "mock", "DEMO"), actualOrigins);
			}
		}

		assertTrue(postgres.isRunning());
	}
}
