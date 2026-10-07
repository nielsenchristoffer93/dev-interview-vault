package com.vault.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservabilityVerificationTest {

    @Test
    @DisplayName("W3C Trace Context: Validates traceparent format and generates linked child span")
    void testW3cTraceContext() {
        String incomingHeader = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        var context = ObservabilityLab.TraceContext.parse(incomingHeader);

        assertThat(context.version()).isEqualTo("00");
        assertThat(context.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(context.parentSpanId()).isEqualTo("00f067aa0ba902b7");
        assertThat(context.traceFlags()).isEqualTo("01");

        // Generate child span for downstream HTTP/Kafka call
        var child = context.createChildSpan("1234567890abcdef");
        assertThat(child.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736"); // Trace ID remains identical
        assertThat(child.parentSpanId()).isEqualTo("1234567890abcdef");
        assertThat(child.toHeaderValue()).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-1234567890abcdef-01");
    }

    @Test
    @DisplayName("Prometheus Cardinality Guard: Rejects high cardinality userId/orderId to prevent TSDB OOM")
    void testHighCardinalityGuard() {
        var guard = new ObservabilityLab.MetricRegistryGuard();

        // Safe RED dimensions: allowed
        guard.registerMetric("http_requests_total", Map.of("method", "POST", "status", "200", "route", "/api/orders"));
        assertThat(guard.activeSeries).hasSize(1);

        // High cardinality violation: rejected!
        assertThatThrownBy(() -> guard.registerMetric("http_requests_total", Map.of("method", "POST", "userId", "user-99999")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("High Cardinality Violation");
    }

    @Test
    @DisplayName("MDC Async Propagation: TaskDecorator transfers context to child thread and cleans up safely")
    void testMdcPropagationWithTaskDecorator() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        var decorator = new ObservabilityLab.MdcPropagatingTaskDecorator();

        // 1. Without decorator: child thread loses MDC
        MDC.put("traceId", "abc-123");
        AtomicReference<String> unDecoratedMdc = new AtomicReference<>();
        executor.submit(() -> unDecoratedMdc.set(MDC.get("traceId"))).get();
        assertThat(unDecoratedMdc.get()).isNull(); // Context lost across thread boundary!

        // 2. With TaskDecorator: child thread receives traceId
        AtomicReference<String> decoratedMdc = new AtomicReference<>();
        Runnable task = decorator.decorate(() -> decoratedMdc.set(MDC.get("traceId")));
        executor.submit(task).get();
        assertThat(decoratedMdc.get()).isEqualTo("abc-123"); // Context successfully transferred!

        // 3. Worker thread is cleaned up after execution (no thread pool leak)
        AtomicReference<String> subsequentTaskMdc = new AtomicReference<>();
        executor.submit(() -> subsequentTaskMdc.set(MDC.get("traceId"))).get();
        assertThat(subsequentTaskMdc.get()).isNull(); // Cleaned up!

        MDC.clear();
        executor.shutdown();
    }
}
