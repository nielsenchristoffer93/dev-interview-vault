package com.vault.realtime;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * RealtimeCommunicationLab
 *
 * Demonstrates:
 * 1. SSE Event Streaming & formatting
 * 2. In-Memory WebSocket Trap vs Redis Pub/Sub Backplane
 * 3. Heartbeat / Zombie connection detection (ALB idle timeout prevention)
 */
public class RealtimeCommunicationLab {

    // =========================================================================
    // 1. SSE (Server-Sent Events) Formatter
    // =========================================================================
    public static class SseEvent {
        public final String id;
        public final String eventType;
        public final String data;
        public final Integer retryMs;

        public SseEvent(String id, String eventType, String data, Integer retryMs) {
            this.id = id;
            this.eventType = eventType;
            this.data = data;
            this.retryMs = retryMs;
        }

        public String toHttpStreamFormat() {
            StringBuilder sb = new StringBuilder();
            if (id != null) sb.append("id: ").append(id).append("\n");
            if (eventType != null) sb.append("event: ").append(eventType).append("\n");
            if (retryMs != null) sb.append("retry: ").append(retryMs).append("\n");
            sb.append("data: ").append(data).append("\n\n");
            return sb.toString();
        }
    }

    // =========================================================================
    // 2. WebSocket Cluster Trap & Pub/Sub Backplane
    // =========================================================================
    public static class MockSocket {
        public final String userId;
        public final List<String> receivedMessages = new CopyOnWriteArrayList<>();
        private long lastActiveTimestamp;
        private boolean open = true;

        public MockSocket(String userId, long initialTimestamp) {
            this.userId = userId;
            this.lastActiveTimestamp = initialTimestamp;
        }

        public void send(String msg) {
            if (open) {
                receivedMessages.add(msg);
            }
        }

        public void touch(long timestamp) {
            this.lastActiveTimestamp = timestamp;
        }

        public long getLastActiveTimestamp() {
            return lastActiveTimestamp;
        }

        public boolean isOpen() {
            return open;
        }

        public void close() {
            this.open = false;
        }
    }

    /**
     * FLAW: Only stores sockets in local memory.
     * Cannot deliver messages to users connected to other nodes in the cluster.
     */
    public static class FlawedLocalWebSocketServer {
        public final String serverId;
        private final Map<String, MockSocket> localSockets = new ConcurrentHashMap<>();

        public FlawedLocalWebSocketServer(String serverId) {
            this.serverId = serverId;
        }

        public void register(MockSocket socket) {
            localSockets.put(socket.userId, socket);
        }

        public boolean sendMessageToUser(String targetUserId, String message) {
            MockSocket socket = localSockets.get(targetUserId);
            if (socket != null && socket.isOpen()) {
                socket.send(message);
                return true;
            }
            // Fails silently if user is on a different pod!
            return false;
        }
    }

    /**
     * FIX: Uses a Pub/Sub Broker (e.g. Redis Pub/Sub) backplane across all cluster nodes.
     */
    public static class PubSubBackplane {
        private final List<DistributedWebSocketServer> registeredNodes = new CopyOnWriteArrayList<>();

        public void registerNode(DistributedWebSocketServer node) {
            registeredNodes.add(node);
        }

        public void broadcast(String targetUserId, String message) {
            for (DistributedWebSocketServer node : registeredNodes) {
                node.onMessageFromBackplane(targetUserId, message);
            }
        }
    }

    public static class DistributedWebSocketServer {
        public final String serverId;
        private final PubSubBackplane backplane;
        private final Map<String, MockSocket> localSockets = new ConcurrentHashMap<>();

        public DistributedWebSocketServer(String serverId, PubSubBackplane backplane) {
            this.serverId = serverId;
            this.backplane = backplane;
            backplane.registerNode(this);
        }

        public void register(MockSocket socket) {
            localSockets.put(socket.userId, socket);
        }

        public void publishToUser(String targetUserId, String message) {
            // Forward to central backplane so the node hosting targetUserId receives it
            backplane.broadcast(targetUserId, message);
        }

        public void onMessageFromBackplane(String targetUserId, String message) {
            MockSocket localSocket = localSockets.get(targetUserId);
            if (localSocket != null && localSocket.isOpen()) {
                localSocket.send(message);
            }
        }
    }

    // =========================================================================
    // 3. Heartbeat & Zombie Connection Detector
    // =========================================================================
    public static class ZombieConnectionDetector {
        private final long maxIdleTimeMs;

        public ZombieConnectionDetector(long maxIdleTimeMs) {
            this.maxIdleTimeMs = maxIdleTimeMs;
        }

        public int pruneZombies(Collection<MockSocket> sockets, long currentTimeMs) {
            int closedCount = 0;
            for (MockSocket socket : sockets) {
                if (socket.isOpen() && (currentTimeMs - socket.getLastActiveTimestamp() > maxIdleTimeMs)) {
                    socket.close();
                    closedCount++;
                }
            }
            return closedCount;
        }
    }
}
