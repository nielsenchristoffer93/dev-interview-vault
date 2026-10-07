package com.vault.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("01-indexes-locks-and-plans: Database Performance & Concurrency Lab")
class DatabaseVerificationTest {

    private static HikariDataSource dataSource;

    @BeforeAll
    static void setupDatabase() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:interview_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(10);
        dataSource = new HikariDataSource(config);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE orders (" +
                    "id INT PRIMARY KEY, " +
                    "status VARCHAR(20), " +
                    "created_at TIMESTAMP, " +
                    "amount DECIMAL(10,2))");

            stmt.execute("CREATE INDEX idx_status_created ON orders (status, created_at)");

            stmt.execute("INSERT INTO orders VALUES " +
                    "(1, 'COMPLETED', '2026-01-10 10:00:00', 100.00), " +
                    "(2, 'PENDING',   '2026-02-15 12:00:00', 250.00), " +
                    "(3, 'COMPLETED', '2026-03-01 15:30:00', 500.00)");
        }
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    @DisplayName("The Flaw: Fråga utan ledande kolumn i sammansatt index tvingas till Full Table Scan")
    void testFlaw_MissingLeadingPrefixCausesTableScan() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String plan = FlawedDatabaseLab.getPlanForMissingLeadingPrefix(conn);

            assertThat(plan)
                    .containsIgnoringCase("SCAN")
                    .doesNotContain("IDX_STATUS_CREATED");
        }
    }

    @Test
    @DisplayName("The Fix: Fråga med ledande prefix utnyttjar indexet effektivt")
    void testFix_LeadingPrefixUsesIndex() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String plan = OptimizedDatabaseLab.getPlanForLeadingPrefix(conn);

            assertThat(plan)
                    .containsIgnoringCase("IDX_STATUS_CREATED");
        }
    }

    @Test
    @DisplayName("The Flaw: Write Skew under READ COMMITTED bryter affärsinvarianten (0 läkare kvar)")
    void testFlaw_WriteSkewBreaksBusinessInvariant() throws Exception {
        int activeDoctorsLeft = FlawedDatabaseLab.executeWriteSkewUnderReadCommitted(dataSource);

        assertThat(activeDoctorsLeft).isZero();
    }

    @Test
    @DisplayName("The Fix: SELECT ... FOR UPDATE serialiserar åtkomsten och bevarar invarianten")
    void testFix_SelectForUpdatePreservesBusinessInvariant() throws Exception {
        int activeDoctorsLeft = OptimizedDatabaseLab.executeSafeCheckoutWithSelectForUpdate(dataSource);

        assertThat(activeDoctorsLeft).isEqualTo(1);
    }

    @Test
    @DisplayName("The Fix: Lock Timeout avbryter blockerade DDL-operationer snabbt och förhindrar anslutningssvält")
    void testFix_LockTimeoutFailsFastWhenTableIsLocked() throws Exception {
        CountDownLatch lockAcquired = new CountDownLatch(1);

        Thread lockerThread = new Thread(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("UPDATE orders SET amount = amount + 1 WHERE id = 1");
                }
                lockAcquired.countDown();
                Thread.sleep(2000);
                conn.rollback();
            } catch (Exception e) {
                // ignore
            }
        });
        lockerThread.start();

        lockAcquired.await(2, TimeUnit.SECONDS);

        try (Connection conn = dataSource.getConnection()) {
            assertThatThrownBy(() -> {
                OptimizedDatabaseLab.executeDdlWithLockTimeout(
                        conn,
                        "ALTER TABLE orders ADD COLUMN priority_flag BOOLEAN",
                        0
                );
            }).isInstanceOf(SQLException.class);
        }

        lockerThread.join();
    }
}
