package com.vault.database;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MigrationAndReplicationLab
 *
 * Demonstrates:
 * 1. Expand & Contract Migration Pattern (Dual-Write safety)
 * 2. Read-Your-Own-Writes Consistency with Sticky Routing Window
 * 3. Keyset Pagination vs Offset/Limit Pagination under concurrent inserts
 */
public class MigrationAndReplicationLab {

    // =========================================================================
    // 1. Expand & Contract Dual-Write Entity
    // =========================================================================

    public static class OrderRecord {
        public final String orderId;
        public String customerPhone;   // Old column (to be deprecated)
        public String mobileNumber;    // New column

        public OrderRecord(String orderId, String phone, String mobile) {
            this.orderId = orderId;
            this.customerPhone = phone;
            this.mobileNumber = mobile;
        }
    }

    public static class DualWriteOrderService {
        private final Map<String, OrderRecord> table = new ConcurrentHashMap<>();

        // Dual-Write Phase: writes to both fields, reads from old or fallback to new
        public void saveOrderDualWrite(String orderId, String phoneNumber) {
            table.put(orderId, new OrderRecord(orderId, phoneNumber, phoneNumber));
        }

        public String readForV1Client(String orderId) {
            OrderRecord r = table.get(orderId);
            return r != null ? r.customerPhone : null;
        }

        public String readForV2Client(String orderId) {
            OrderRecord r = table.get(orderId);
            return r != null ? r.mobileNumber : null;
        }
    }

    // =========================================================================
    // 2. Read-Your-Own-Writes with Sticky Primary Routing
    // =========================================================================

    public static class ProfileDatabaseCluster {
        public final Map<String, String> primaryDb = new ConcurrentHashMap<>();
        public final Map<String, String> replicaDb = new ConcurrentHashMap<>();
        private final Map<String, Long> userLastWriteTimes = new ConcurrentHashMap<>();
        private final long stickyWindowMs;

        public ProfileDatabaseCluster(long stickyWindowMs) {
            this.stickyWindowMs = stickyWindowMs;
        }

        public void writeProfile(String userId, String address, long currentTimestamp) {
            // Write to primary
            primaryDb.put(userId, address);
            // Record write timestamp for session stickiness
            userLastWriteTimes.put(userId, currentTimestamp);
            // Note: replicaDb is NOT updated immediately (simulating replication lag)
        }

        public void simulateReplicationCatchUp(String userId) {
            String val = primaryDb.get(userId);
            if (val != null) {
                replicaDb.put(userId, val);
            }
        }

        /**
         * FLAW: Blindly routes all reads to replica if readOnly=true,
         * causing user to see stale data before replication catches up.
         */
        public String readFlawed(String userId) {
            return replicaDb.get(userId);
        }

        /**
         * FIX: Sticky Routing Window.
         * If the user wrote within the sticky window, read from Primary; otherwise use Replica.
         */
        public String readWithStickyRouting(String userId, long currentTimestamp) {
            Long lastWrite = userLastWriteTimes.get(userId);
            if (lastWrite != null && (currentTimestamp - lastWrite) <= stickyWindowMs) {
                // Route to Primary for read-your-own-writes guarantee!
                return primaryDb.get(userId);
            }
            return replicaDb.get(userId);
        }
    }

    // =========================================================================
    // 3. Keyset Pagination
    // =========================================================================

    public record AuditLog(long id, String message) {}

    public static class KeysetPaginationService {
        private final List<AuditLog> dataset = new ArrayList<>();

        public void populateDescending(int count) {
            for (long i = count; i >= 1; i--) {
                dataset.add(new AuditLog(i, "AuditLog #" + i));
            }
        }

        public void insertNewLog(long id, String msg) {
            // New log inserted at the top of the timeline
            dataset.add(0, new AuditLog(id, msg));
        }

        // Keyset seek: WHERE id < lastSeenId ORDER BY id DESC LIMIT pageSize
        public List<AuditLog> fetchNextKeysetPage(Long lastSeenId, int pageSize) {
            List<AuditLog> result = new ArrayList<>();
            for (AuditLog log : dataset) {
                if (lastSeenId == null || log.id < lastSeenId) {
                    result.add(log);
                    if (result.size() >= pageSize) {
                        break;
                    }
                }
            }
            return result;
        }

        // Offset method: LIMIT pageSize OFFSET offset
        public List<AuditLog> fetchNextOffsetPage(int offset, int pageSize) {
            if (offset >= dataset.size()) return Collections.emptyList();
            int toIndex = Math.min(offset + pageSize, dataset.size());
            return new ArrayList<>(dataset.subList(offset, toIndex));
        }
    }
}
