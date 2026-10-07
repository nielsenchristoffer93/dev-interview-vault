package com.vault;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * FLAWED IMPLEMENTATION:
 * 1. Statiska Refresh Tokens utan rotation och utan Replay / Reuse Detection.
 *    En stulen token kan användas i all evighet för att generera nya access tokens.
 * 2. Symmetrisk delad hemlighet (HS256-princip): Alla mikrotjänster som verifierar
 *    känner till hemligheten och kan förfalska egna administratörstokens.
 * 3. Hårdkodade personliga användar-tokens för maskinjobb (bryter Least Privilege).
 */
public class FlawedTokenService {

    private final String sharedHmacSecret;
    // Enkel session/token-databas: Sparar statiska refresh tokens utan familje- eller status-spårning
    private final Set<String> activeStaticRefreshTokens = new HashSet<>();

    public FlawedTokenService(String sharedHmacSecret) {
        this.sharedHmacSecret = sharedHmacSecret;
    }

    /**
     * Skapar en statisk refresh token som förblir giltig för alltid
     * och aldrig roteras vid användning.
     */
    public String issueStaticRefreshToken(String userId) {
        String refreshToken = "static-rf-" + UUID.randomUUID() + "-" + userId;
        activeStaticRefreshTokens.add(refreshToken);
        return refreshToken;
    }

    /**
     * FLAW: Återanvändning tillåts obegränsat.
     * Ingen rotation (returnerar ingen ny refresh token) och ingen spårning om token stulits.
     */
    public String refreshAccessToken(String presentedRefreshToken) {
        if (!activeStaticRefreshTokens.contains(presentedRefreshToken)) {
            throw new SecurityException("Ogiltig refresh token");
        }

        // Borde ha roterats och bränts! Men här förblir samma refresh token aktiv:
        return "access-token-for-" + presentedRefreshToken + "-" + System.currentTimeMillis();
    }

    /**
     * FLAW: Symmetrisk HMAC-signatur med delad hemlighet.
     * Vem som helst med sharedHmacSecret kan signera och skapa tokens!
     */
    public String signTokenSymmetric(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((payload + ":" + sharedHmacSecret).getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return payload + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean verifyTokenSymmetric(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) return false;
            String payload = parts[0];
            String expectedSig = parts[1];

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((payload + ":" + sharedHmacSecret).getBytes(StandardCharsets.UTF_8));
            String calculatedSig = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

            return calculatedSig.equals(expectedSig);
        } catch (Exception e) {
            return false;
        }
    }

    public Set<String> getActiveStaticRefreshTokens() {
        return activeStaticRefreshTokens;
    }
}
