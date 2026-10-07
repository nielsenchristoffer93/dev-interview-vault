package com.vault.realtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeCommunicationVerificationTest {

    @Test
    @DisplayName("SSE: Formats data according to the W3C text/event-stream specification with ID and retry")
    void testSseFormatting() {
        var event = new RealtimeCommunicationLab.SseEvent("evt-101", "notification", "{\"unread\": 5}", 3000);
        String streamOutput = event.toHttpStreamFormat();

        assertThat(streamOutput).isEqualTo(
                "id: evt-101\n" +
                "event: notification\n" +
                "retry: 3000\n" +
                "data: {\"unread\": 5}\n\n"
        );
    }

    @Test
    @DisplayName("WS Cluster Trap: In-memory socket maps fail when sender and receiver are on different pods")
    void testFlawedLocalWebSocketServerClusterTrap() {
        var pod1 = new RealtimeCommunicationLab.FlawedLocalWebSocketServer("pod-1");
        var pod2 = new RealtimeCommunicationLab.FlawedLocalWebSocketServer("pod-2");

        var aliceSocket = new RealtimeCommunicationLab.MockSocket("alice", 1000L);
        var bobSocket = new RealtimeCommunicationLab.MockSocket("bob", 1000L);

        pod1.register(aliceSocket);
        pod2.register(bobSocket);

        // Alice on Pod 1 tries to send to Bob who is connected to Pod 2
        boolean delivered = pod1.sendMessageToUser("bob", "Hello Bob!");

        // Delivery fails because Pod 1 only looks in its own local memory!
        assertThat(delivered).isFalse();
        assertThat(bobSocket.receivedMessages).isEmpty();
    }

    @Test
    @DisplayName("WS Fix: Redis Pub/Sub Backplane delivers across pods regardless of where users connect")
    void testDistributedWebSocketServerWithPubSub() {
        var backplane = new RealtimeCommunicationLab.PubSubBackplane();
        var pod1 = new RealtimeCommunicationLab.DistributedWebSocketServer("pod-1", backplane);
        var pod2 = new RealtimeCommunicationLab.DistributedWebSocketServer("pod-2", backplane);

        var aliceSocket = new RealtimeCommunicationLab.MockSocket("alice", 1000L);
        var bobSocket = new RealtimeCommunicationLab.MockSocket("bob", 1000L);

        pod1.register(aliceSocket);
        pod2.register(bobSocket);

        // Alice on Pod 1 publishes message to Bob
        pod1.publishToUser("bob", "Hello Bob across the cluster!");

        // Bob on Pod 2 receives the message via backplane
        assertThat(bobSocket.receivedMessages).containsExactly("Hello Bob across the cluster!");
    }

    @Test
    @DisplayName("Heartbeat & ALB Idle Timeout: Prunes zombie connections that exceed idle threshold")
    void testZombieConnectionDetector() {
        long now = 100_000L;
        long maxIdleMs = 60_000L; // AWS ALB 60s idle timeout
        var detector = new RealtimeCommunicationLab.ZombieConnectionDetector(maxIdleMs);

        var activeSocket = new RealtimeCommunicationLab.MockSocket("active_user", now - 20_000L); // Active 20s ago
        var zombieSocket = new RealtimeCommunicationLab.MockSocket("zombie_user", now - 75_000L); // Silent for 75s

        var sockets = List.of(activeSocket, zombieSocket);

        int pruned = detector.pruneZombies(sockets, now);

        assertThat(pruned).isEqualTo(1);
        assertThat(activeSocket.isOpen()).isTrue();
        assertThat(zombieSocket.isOpen()).isFalse(); // Zombie connection closed to prevent fd leak
    }
}
