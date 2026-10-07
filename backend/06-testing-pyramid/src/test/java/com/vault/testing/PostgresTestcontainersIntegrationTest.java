package com.vault.testing;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PYRAMID TOP (Integration Test with Testcontainers):
 * - Startar en riktig PostgreSQL-container via Docker API om Docker körs.
 * - Testar äkta Postgres-syntax och dialektfunktioner som inte fungerar i H2.
 * - Isolerat och försvinner automatiskt efter testkörning via Ryuk.
 */
public class PostgresTestcontainersIntegrationTest {

    @Test
    @DisplayName("Integration: Verifierar att riktig PostgreSQL körs och hanterar Postgres-specifika tabeller")
    void testRealPostgreSQLDialect() throws Exception {
        boolean dockerAvailable = false;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
        }

        // Hoppa över graciöst om Docker daemon inte är igång i utvecklarens miljö
        Assumptions.assumeTrue(dockerAvailable, "Docker daemon körs inte - hoppar över Testcontainers-integrationstest");

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("testdb")
                .withUsername("testuser")
                .withPassword("testpass")) {
            postgres.start();

            assertThat(postgres.isRunning()).isTrue();

            try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                try (Statement stmt = conn.createStatement()) {
                    // Skapa en tabell med Postgres-specifika funktioner
                    stmt.execute("CREATE TABLE orders_integration (id VARCHAR(50) PRIMARY KEY, metadata JSONB, created_at TIMESTAMPTZ DEFAULT NOW())");

                    // Sätt in giltig JSONB
                    stmt.execute("INSERT INTO orders_integration (id, metadata) VALUES ('ORD-999', '{\"source\": \"mobile_app\"}'::jsonb)");

                    // Läs ut med Postgres JSON-operator ->>
                    ResultSet rs = stmt.executeQuery("SELECT metadata->>'source' as src FROM orders_integration WHERE id = 'ORD-999'");
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("src")).isEqualTo("mobile_app");
                }
            }
        }
    }
}
