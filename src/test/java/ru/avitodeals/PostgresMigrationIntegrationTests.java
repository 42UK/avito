package ru.avitodeals;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationIntegrationTests {
	@Container
	private static final PostgreSQLContainer<?> postgres =
			new PostgreSQLContainer<>("postgres:16-alpine");

	@Test
	void appliesVersionedMigrationsAndCreatesCoreTables() throws Exception {
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
		}

		assertTrue(postgres.isRunning());
	}
}
