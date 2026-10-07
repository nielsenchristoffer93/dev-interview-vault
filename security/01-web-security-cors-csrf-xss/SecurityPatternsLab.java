package com.vault.security;

import java.net.InetAddress;
import java.net.URI;
import java.util.*;

/**
 * SecurityPatternsLab
 *
 * Demonstrates:
 * 1. CORS validation (Flawed Wildcard vs Secure Whitelist + Max-Age)
 * 2. SSRF URL validation (Detecting AWS IMDS 169.254.169.254, RFC1918 private IPs)
 * 3. Secure Cookie Generation (HttpOnly, Secure, SameSite=Strict)
 */
public class SecurityPatternsLab {

    // =========================================================================
    // 1. CORS Headers
    // =========================================================================
    public static class CorsResult {
        public final Map<String, String> headers = new HashMap<>();

        public boolean allowsCredentials() {
            return "true".equalsIgnoreCase(headers.get("Access-Control-Allow-Credentials"));
        }

        public String getAllowedOrigin() {
            return headers.get("Access-Control-Allow-Origin");
        }

        public String getMaxAge() {
            return headers.get("Access-Control-Max-Age");
        }
    }

    public static class FlawedCorsHandler {
        // FLAW: Blindly returns wildcard with credentials, which browser rejects!
        public static CorsResult handlePreflight(String origin, boolean credentialsRequested) {
            CorsResult result = new CorsResult();
            result.headers.put("Access-Control-Allow-Origin", "*");
            if (credentialsRequested) {
                result.headers.put("Access-Control-Allow-Credentials", "true");
            }
            return result;
        }
    }

    public static class SecureCorsHandler {
        private final Set<String> allowedOrigins;
        private final int maxAgeSeconds;

        public SecureCorsHandler(Collection<String> allowedOrigins, int maxAgeSeconds) {
            this.allowedOrigins = new HashSet<>(allowedOrigins);
            this.maxAgeSeconds = maxAgeSeconds;
        }

        public CorsResult handlePreflight(String origin, boolean credentialsRequested) {
            CorsResult result = new CorsResult();
            if (origin != null && allowedOrigins.contains(origin)) {
                result.headers.put("Access-Control-Allow-Origin", origin);
                result.headers.put("Vary", "Origin");
                result.headers.put("Access-Control-Max-Age", String.valueOf(maxAgeSeconds));
                if (credentialsRequested) {
                    result.headers.put("Access-Control-Allow-Credentials", "true");
                }
            }
            // If origin not allowed, return empty headers (browser blocks request)
            return result;
        }
    }

    // =========================================================================
    // 2. SSRF Protection (Cloud Metadata & RFC1918)
    // =========================================================================
    public static class SsrfValidator {
        private static final Set<String> BLOCKED_HOSTS = Set.of(
                "169.254.169.254",          // AWS IMDSv1/v2, GCP metadata
                "metadata.google.internal",
                "127.0.0.1",
                "localhost",
                "::1"
        );

        public static boolean isSafeUrl(String rawUrl) {
            try {
                URI uri = URI.create(rawUrl);
                String scheme = uri.getScheme();
                if (scheme == null || !scheme.equalsIgnoreCase("https")) {
                    return false; // Force HTTPS
                }

                String host = uri.getHost();
                if (host == null || BLOCKED_HOSTS.contains(host.toLowerCase())) {
                    return false;
                }

                // If host is an IPv4 or IPv6 string directly
                if (isPrivateOrLocalIpString(host)) {
                    return false;
                }

                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private static boolean isPrivateOrLocalIpString(String host) {
            if (host.equals("127.0.0.1") || host.equals("::1") || host.equals("169.254.169.254")) {
                return true;
            }
            // 10.0.0.0/8
            if (host.matches("^10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) return true;
            // 172.16.0.0/12
            if (host.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\.\\d{1,3}\\.\\d{1,3}$")) return true;
            // 192.168.0.0/16
            if (host.matches("^192\\.168\\.\\d{1,3}\\.\\d{1,3}$")) return true;
            // 169.254.0.0/16 (Link-local)
            if (host.matches("^169\\.254\\.\\d{1,3}\\.\\d{1,3}$")) return true;
            return false;
        }
    }

    // =========================================================================
    // 3. Cookie Security Builder
    // =========================================================================
    public static class CookieBuilder {
        public static String buildSecureAuthCookie(String name, String value, String path) {
            return String.format("%s=%s; Path=%s; HttpOnly; Secure; SameSite=Strict", name, value, path);
        }

        public static String buildInsecureCookie(String name, String value) {
            // FLAW: Missing HttpOnly, Secure, and SameSite
            return String.format("%s=%s; Path=/", name, value);
        }
    }
}
