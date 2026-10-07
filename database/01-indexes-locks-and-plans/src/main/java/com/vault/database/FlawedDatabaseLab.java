package com.vault.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * THE FLAW:
 * 1. Sammansatta B-Tree index utan 'Leftmost Prefix': Databasen gör Full Table Scan
 *    trots att kolumnen finns i indexet, eftersom den ledande kolumnen saknas.
 * 2. Write Skew: Två samtidiga transaktioner läser och uppdaterar olika rader under
 *    READ COMMITTED, vilket bryter affärsinvarianten ("Minst 1 läkare i jour").
 * 3. Oskyddad DDL: Migration utan lock_timeout blockerar alla anslutningar.
 */
public class FlawedDatabaseLab {

    public static String getPlanForMissingLeadingPrefix(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("EXPLAIN SELECT * FROM orders WHERE created_at >= '2026-01-01'")) {
            StringBuilder plan = new StringBuilder();
            while (rs.next()) {
                plan.append(rs.getString(1)).append("\n");
            }
            return plan.toString();
        }
    }

    public static int executeWriteSkewUnderReadCommitted(DataSource dataSource) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS on_call_doctors (" +
                    "id INT PRIMARY KEY, name VARCHAR(50), is_on_call BOOLEAN)");
            stmt.execute("DELETE FROM on_call_doctors");
            stmt.execute("INSERT INTO on_call_doctors VALUES (1, 'Dr. Alice', true), (2, 'Dr. Bob', true)");
        }

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(2);
        AtomicInteger successfulCheckouts = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        int[] doctorIds = {1, 2};
        for (int doctorId : doctorIds) {
            executor.submit(() -> {
                try (Connection conn = dataSource.getConnection()) {
                    conn.setAutoCommit(false);
                    conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                    startGate.await();

                    int activeCount;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT COUNT(*) FROM on_call_doctors WHERE is_on_call = true")) {
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            activeCount = rs.getInt(1);
                        }
                    }

                    if (activeCount >= 2) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE on_call_doctors SET is_on_call = false WHERE id = ?")) {
                            ps.setInt(1, doctorId);
                            ps.executeUpdate();
                        }
                        conn.commit();
                        successfulCheckouts.incrementAndGet();
                    } else {
                        conn.rollback();
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM on_call_doctors WHERE is_on_call = true")) {
            rs.next();
            return rs.getInt(1); // Returnerar 0! Båda checkade ut!
        }
    }
}
