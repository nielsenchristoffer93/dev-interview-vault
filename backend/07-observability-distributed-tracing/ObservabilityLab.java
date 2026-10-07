package com.vault.observability;

import org.slf4j.MDC;

import java.util.*;
import java.util.concurrent.*;

/**
 * ObservabilityLab
 *
 * Demonstrates:
 * 1. W3C Trace Context (traceparent) generation, parsing and child span derivation
 * 2. High Cardinality Metric Guard (RED method enforcement)
 * 3. MDC Context Propagation across async threads using TaskDecorator
 */
public class ObservabilityLab {

    // =========================================================================
    // 1. W3C Trace Context (traceparent)
    // =========================================================================

    public record TraceContext(String version, String traceId, String parentSpanId, String traceFlags) {
        public static TraceContext parse(String traceparent) {
            if (traceparent == null) return null;
            String[] parts = traceparent.trim().split("-");
            if (parts.length != 4 || !parts[0].equals("00") || parts[1].length() != 32 || parts[2].length() != 16) {
                throw new IllegalArgumentException("Invalid W3C traceparent header format: " + traceparent);
            }
            return new TraceContext(parts[0], parts[1], parts[2], parts[3]);
        }

        public TraceContext createChildSpan(String newSpanId) {
            // Child span preserves original traceId, but sets parentSpanId to current newSpanId
            return new TraceContext(version, traceId, newSpanId, traceFlags);
        }

        public String toHeaderValue() {
            return String.join("-", version, traceId, parentSpanId, traceFlags);
        }
    }

    // =========================================================================
    // 2. High Cardinality Guard for Metrics
    // =========================================================================

    public static class MetricRegistryGuard {
        private static final Set<String> ALLOWED_DIMENSIONS = Set.of("method", "status", "route");
        public final Set<String> activeSeries = new HashSet<>();

        public void registerMetric(String metricName, Map<String, String> tags) {
            for (String tagKey : tags.keySet()) {
                if (!ALLOWED_DIMENSIONS.contains(tagKey.toLowerCase())) {
                    throw new IllegalArgumentException(
                            "High Cardinality Violation! Tag '" + tagKey + "' rejected. " +
                            "Use Traces/Spans or Logs for entity IDs, not Prometheus dimensions."
                    );
                }
            }
            // Form series key
            String seriesKey = metricName + tags.toString();
            activeSeries.add(seriesKey);
        }
    }

    // =========================================================================
    // 3. MDC Async TaskDecorator
    // =========================================================================

    public interface TaskDecorator {
        Runnable decorate(Runnable runnable);
    }

    public static class MdcPropagatingTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable originalRunnable) {
            // Capture parent thread's MDC context
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    } else {
                        MDC.clear();
                    }
                    originalRunnable.run();
                } finally {
                    // Restore previous or clear to prevent thread pool leak!
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        }
    }
}
