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
 * THE FIX:
 * 1. Leftmost Prefix Rule: Korrekt indexerat för frågans filter och sort.
 * 2. Förhindra Write Skew med SELECT ... FOR UPDATE (Pessimistisk låsning) eller SERIALIZABLE.
 * 3. Safe Zero-Downtime DDL: Alltid sätta lock_timeout före schemaändringar.
 */
public class OptimizedDatabaseLab {

    public static String getPlanForLeadingPrefix(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("EXPLAIN SELECT * FROM orders WHERE status = 'COMPLETED' AND created_at >= '2026-01-01'")) {
            StringBuilder plan = new StringBuilder();
            while (rs.next()) {
                plan.append(rs.getString(1)).append("\n");
            }
            return plan.toString();
        }
    }

    public static int executeSafeCheckoutWithSelectForUpdate(DataSource dataSource) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS on_call_doctors (" +
                    "id INT PRIMARY KEY, name VARCHAR(50), is_on_call BOOLEAN)");
            stmt.execute("DELETE FROM on_call_doctors");
            stmt.execute("INSERT INTO on_call_doctors VALUES (1, 'Dr. Alice', true), (2, 'Dr. Bob', true)");
        }

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(2);
        AtomicInteger rejectedCheckouts = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        int[] doctorIds = {1, 2};
        for (int doctorId : doctorIds) {
            executor.submit(() -> {
                try (Connection conn = dataSource.getConnection()) {
                    conn.setAutoCommit(false);

                    startGate.await();

                    // FIX: SELECT ... FOR UPDATE låser de aktiva raderna!
                    int activeCount = 0;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT id FROM on_call_doctors WHERE is_on_call = true FOR UPDATE")) {
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                activeCount++;
                            }
                        }
                    }

                    if (activeCount >= 2) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE on_call_doctors SET is_on_call = false WHERE id = ?")) {
                            ps.setInt(1, doctorId);
                            ps.executeUpdate();
                        }
                        conn.commit();
                    } else {
                        conn.rollback();
                        rejectedCheckouts.incrementAndGet();
                    }
                } catch (Exception e) {
                    rejectedCheckouts.incrementAndGet();
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
            return rs.getInt(1); // Returnerar 1! Invarianten hölls!
        }
    }

    public static void executeDdlWithLockTimeout(Connection conn, String ddlSql, int timeoutSeconds) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET LOCK_TIMEOUT " + (timeoutSeconds * 1000));
            stmt.execute(ddlSql);
        }
    }
}
