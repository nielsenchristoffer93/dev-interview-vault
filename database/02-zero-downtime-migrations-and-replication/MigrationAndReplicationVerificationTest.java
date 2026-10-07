package com.vault.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationAndReplicationVerificationTest {

    @Test
    @DisplayName("Expand & Contract: Dual-Write allows simultaneous compatibility for v1 and v2 clients")
    void testDualWritePhase() {
        var service = new MigrationAndReplicationLab.DualWriteOrderService();

        // Write order using dual-write pattern
        service.saveOrderDualWrite("ORD-101", "+46701234567");

        // Legacy v1 reader sees data in customerPhone
        assertThat(service.readForV1Client("ORD-101")).isEqualTo("+46701234567");

        // Modern v2 reader sees data in mobileNumber
        assertThat(service.readForV2Client("ORD-101")).isEqualTo("+46701234567");
    }

    @Test
    @DisplayName("Replication Lag Flaw: Naive read-replica routing returns stale profile data")
    void testReplicationLagFlaw() {
        var cluster = new MigrationAndReplicationLab.ProfileDatabaseCluster(3000L);

        // User had old address on both primary and replica
        cluster.primaryDb.put("usr-1", "Old Street 1");
        cluster.replicaDb.put("usr-1", "Old Street 1");

        // User updates profile at t=1000ms
        cluster.writeProfile("usr-1", "New Avenue 99", 1000L);

        // Flawed read from replica at t=1100ms (100ms later, replica lagging)
        String readAddress = cluster.readFlawed("usr-1");

        // FLAW: Returns old address because replica has not caught up yet!
        assertThat(readAddress).isEqualTo("Old Street 1");
    }

    @Test
    @DisplayName("Replication Lag Fix: Sticky routing window guarantees Read-Your-Own-Writes")
    void testStickyRoutingFix() {
        var cluster = new MigrationAndReplicationLab.ProfileDatabaseCluster(3000L); // 3-second sticky window

        cluster.primaryDb.put("usr-1", "Old Street 1");
        cluster.replicaDb.put("usr-1", "Old Street 1");

        // User writes at t=1000ms
        cluster.writeProfile("usr-1", "New Avenue 99", 1000L);

        // Read at t=1200ms (within 3s window): Sticky router directs to PRIMARY
        String immediateRead = cluster.readWithStickyRouting("usr-1", 1200L);
        assertThat(immediateRead).isEqualTo("New Avenue 99");

        // At t=4500ms (after window expired) and replication has caught up:
        cluster.simulateReplicationCatchUp("usr-1");
        String laterRead = cluster.readWithStickyRouting("usr-1", 4500L);
        assertThat(laterRead).isEqualTo("New Avenue 99");
    }

    @Test
    @DisplayName("Keyset Pagination: Remains stable without duplicates even when rows are inserted concurrently")
    void testKeysetPaginationStability() {
        var service = new MigrationAndReplicationLab.KeysetPaginationService();
        service.populateDescending(100); // Items with IDs 100 down to 1

        // Page 1: Top 5 items (IDs 100, 99, 98, 97, 96)
        List<MigrationAndReplicationLab.AuditLog> page1 = service.fetchNextKeysetPage(null, 5);
        assertThat(page1).extracting(MigrationAndReplicationLab.AuditLog::id)
                .containsExactly(100L, 99L, 98L, 97L, 96L);

        // Someone concurrently inserts a new log item (ID 101) at the top!
        service.insertNewLog(101L, "Concurrent Insert Log");

        // Page 2 using Keyset cursor (id < 96L):
        // Fetches next 5 items: 95, 94, 93, 92, 91 without skipping or duplicates!
        List<MigrationAndReplicationLab.AuditLog> page2 = service.fetchNextKeysetPage(96L, 5);
        assertThat(page2).extracting(MigrationAndReplicationLab.AuditLog::id)
                .containsExactly(95L, 94L, 93L, 92L, 91L);
    }
}
