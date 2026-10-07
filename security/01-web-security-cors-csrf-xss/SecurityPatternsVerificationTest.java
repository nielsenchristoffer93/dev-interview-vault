package com.vault.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPatternsVerificationTest {

    @Test
    @DisplayName("CORS Flaw: Wildcard with credentials is an invalid configuration rejected by browsers")
    void testFlawedCorsConfiguration() {
        var result = SecurityPatternsLab.FlawedCorsHandler.handlePreflight("https://evil.com", true);

        // Demonstrates the critical flaw
        assertThat(result.getAllowedOrigin()).isEqualTo("*");
        assertThat(result.allowsCredentials()).isTrue();
    }

    @Test
    @DisplayName("CORS Fix: Whitelisted origin is reflected, max-age is set, unlisted origin is dropped")
    void testSecureCorsConfiguration() {
        var secureHandler = new SecurityPatternsLab.SecureCorsHandler(
                List.of("https://app.mycompany.com", "https://admin.mycompany.com"),
                86400
        );

        // 1. Allowed Origin
        var valid = secureHandler.handlePreflight("https://app.mycompany.com", true);
        assertThat(valid.getAllowedOrigin()).isEqualTo("https://app.mycompany.com");
        assertThat(valid.allowsCredentials()).isTrue();
        assertThat(valid.getMaxAge()).isEqualTo("86400");
        assertThat(valid.headers.get("Vary")).isEqualTo("Origin");

        // 2. Malicious Origin
        var invalid = secureHandler.handlePreflight("https://hacker.com", true);
        assertThat(invalid.getAllowedOrigin()).isNull();
        assertThat(invalid.allowsCredentials()).isFalse();
    }

    @Test
    @DisplayName("SSRF Flaw & Fix: Rejects AWS metadata (169.254.169.254) and private RFC1918 IPs")
    void testSsrfProtection() {
        // Blocks AWS IMDS
        assertThat(SecurityPatternsLab.SsrfValidator.isSafeUrl("http://169.254.169.254/latest/meta-data/")).isFalse();
        assertThat(SecurityPatternsLab.SsrfValidator.isSafeUrl("https://169.254.169.254/latest/meta-data/")).isFalse();

        // Blocks Localhost / Loopback
        assertThat(SecurityPatternsLab.SsrfValidator.isSafeUrl("https://localhost:8080/actuator")).isFalse();
        assertThat(SecurityPatternsLab.SsrfValidator.isSafeUrl("https://127.0.0.1:8080/actuator")).isFalse();

        // Blocks Private Subnets (RFC 1918)
        assertThat(SecurityPatternsLab.SsrfValidator.isSafeUrl("https://10.0.1.50/keys")).isFalse();
        assertThat(SecurityPatternsLab.SsrfValidator.isSafeUrl("https://192.168.1.1/admin")).isFalse();

        // Allows safe public HTTPS
        assertThat(SecurityPatternsLab.SsrfValidator.isSafeUrl("https://cdn.example.com/avatar.png")).isTrue();
    }

    @Test
    @DisplayName("Cookie Security: Verifies HttpOnly, Secure, and SameSite=Strict flags")
    void testCookieFlags() {
        String secureCookie = SecurityPatternsLab.CookieBuilder.buildSecureAuthCookie("refresh_token", "secret999", "/api/auth");
        assertThat(secureCookie).contains("HttpOnly");
        assertThat(secureCookie).contains("Secure");
        assertThat(secureCookie).contains("SameSite=Strict");
        assertThat(secureCookie).contains("Path=/api/auth");

        String insecureCookie = SecurityPatternsLab.CookieBuilder.buildInsecureCookie("session", "secret999");
        assertThat(insecureCookie).doesNotContain("HttpOnly");
        assertThat(insecureCookie).doesNotContain("Secure");
        assertThat(insecureCookie).doesNotContain("SameSite");
    }
}
