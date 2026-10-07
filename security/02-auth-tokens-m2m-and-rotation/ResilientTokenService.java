package com.vault;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RESILIENT SECURITY IMPLEMENTATION:
 * 1. Refresh Token Rotation (RTR) med Token Families och Automatic Reuse Detection.
 * 2. OAuth2 Client Credentials Grant (M2M) med strikt Least-Privilege Scopes.
 * 3. Asymmetrisk RSA-signering (RS256-princip) med Key ID (kid) för säker JWKS-verifiering.
 */
public class ResilientTokenService {

    // --- REFRESH TOKEN ROTATION & REUSE DETECTION ---

    public record RefreshTokenRecord(
            String token,
            String familyId,
            String userId,
            boolean isConsumed,
            long createdAt
    ) {}

    public record RotationResult(
            String newAccessToken,
            String newRefreshToken
    ) {}

    public static class TokenReuseException extends SecurityException {
        public TokenReuseException(String message) {
            super(message);
        }
    }

    // Lagring av tokens och familjer
    private final Map<String, RefreshTokenRecord> tokenStore = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> familyTokens = new ConcurrentHashMap<>();
    private final Set<String> revokedFamilies = ConcurrentHashMap.newKeySet();

    // --- M2M CLIENT CREDENTIALS REGISTRY ---
    public record M2MClient(
            String clientId,
            String clientSecretHash,
            Set<String> allowedScopes
    ) {}

    private final Map<String, M2MClient> clientRegistry = new ConcurrentHashMap<>();

    // --- ASYMMETRIC SIGNING (RSA / RS256) ---
    private final KeyPair rsaKeyPair;
    private final String currentKeyId = "vault-key-2026-v1";

    public ResilientTokenService() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            this.rsaKeyPair = keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Kunde inte initialisera RSA KeyPair", e);
        }
    }

    // =========================================================================
    // 1. REFRESH TOKEN ROTATION (RTR) & REUSE DETECTION
    // =========================================================================

    public String issueInitialRefreshToken(String userId) {
        String familyId = "fam-" + UUID.randomUUID();
        String token = "rtr-" + UUID.randomUUID();

        RefreshTokenRecord record = new RefreshTokenRecord(token, familyId, userId, false, System.currentTimeMillis());
        tokenStore.put(token, record);

        familyTokens.computeIfAbsent(familyId, k -> ConcurrentHashMap.newKeySet()).add(token);
        return token;
    }

    /**
     * Roterar refresh token enligt RFC 6749 Best Current Practice (BCP).
     * Vid återanvändning av en redan förbrukad token ogiltigförklaras hela familjen omedelbart!
     */
    public RotationResult rotateRefreshToken(String presentedToken) {
        RefreshTokenRecord record = tokenStore.get(presentedToken);

        if (record == null) {
            throw new SecurityException("Okänd refresh token");
        }

        String familyId = record.familyId();

        // Kontrollera om hela familjen redan är spärrad
        if (revokedFamilies.contains(familyId)) {
            throw new SecurityException("Sessionen är avslutad. Token-familjen är spärrad.");
        }

        // 🚨 TOKEN REUSE DETECTION (Replay Attack upptäckt!)
        if (record.isConsumed()) {
            // En tidigare använd token har presenterats igen -> Angripare har stulit en kopia!
            // ÅTGÄRD: Spärra omedelbart HELA familjen av tokens för att skydda användaren.
            revokedFamilies.add(familyId);
            throw new TokenReuseException(
                    "Token reuse upptäckt för familj " + familyId + "! Hela token-familjen har spärrats."
            );
        }

        // Konsumera och bränn den presenterade tokenen (den kan aldrig användas igen)
        tokenStore.put(presentedToken, new RefreshTokenRecord(
                record.token(),
                record.familyId(),
                record.userId(),
                true, // Markeras som förbrukad!
                record.createdAt()
        ));

        // Utfärda en helt NY refresh token i samma familj
        String newRefreshToken = "rtr-" + UUID.randomUUID();
        RefreshTokenRecord newRecord = new RefreshTokenRecord(
                newRefreshToken,
                familyId,
                record.userId(),
                false,
                System.currentTimeMillis()
        );
        tokenStore.put(newRefreshToken, newRecord);
        familyTokens.get(familyId).add(newRefreshToken);

        String newAccessToken = "at-" + UUID.randomUUID() + "-user-" + record.userId();
        return new RotationResult(newAccessToken, newRefreshToken);
    }

    // =========================================================================
    // 2. OAUTH2 CLIENT CREDENTIALS (M2M) MED LEAST PRIVILEGE
    // =========================================================================

    public void registerM2MClient(String clientId, String clientSecret, Set<String> allowedScopes) {
        String secretHash = hashSecret(clientSecret);
        clientRegistry.put(clientId, new M2MClient(clientId, secretHash, allowedScopes));
    }

    public String issueM2MToken(String clientId, String clientSecret, Set<String> requestedScopes) {
        M2MClient client = clientRegistry.get(clientId);
        if (client == null || !client.clientSecretHash().equals(hashSecret(clientSecret))) {
            throw new SecurityException("Ogiltiga M2M client credentials");
        }

        // Validera att efterfrågade scopes ryms inom klientens tillåtna scopes
        for (String scope : requestedScopes) {
            if (!client.allowedScopes().contains(scope)) {
                throw new SecurityException("Nekad: Klienten saknar behörighet för scope: " + scope);
            }
        }

        String grantedScopesString = String.join(" ", requestedScopes);
        String payload = "m2m:" + clientId + ":scopes=[" + grantedScopesString + "]";
        return signTokenAsymmetric(payload);
    }

    // =========================================================================
    // 3. ASYMMETRISK SIGNERING (RS256 / SHA256withRSA)
    // =========================================================================

    public String signTokenAsymmetric(String payload) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(rsaKeyPair.getPrivate());
            signer.update(payload.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = signer.sign();

            String sigEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
            return currentKeyId + "." + payload + "." + sigEncoded;
        } catch (Exception e) {
            throw new RuntimeException("Kunde inte signera asymmetriskt", e);
        }
    }

    /**
     * Nedströms mikrotjänster behöver enbart den PUBLIKA nyckeln för att verifiera!
     * De kan aldrig förfalska tokens eftersom de saknar den privata nyckeln.
     */
    public static boolean verifyTokenAsymmetric(String token, PublicKey publicKey) {
        try {
            String[] parts = token.split("\\.", 3);
            if (parts.length != 3) return false;

            String keyId = parts[0];
            String payload = parts[1];
            String signatureEncoded = parts[2];

            byte[] signatureBytes = Base64.getUrlDecoder().decode(signatureEncoded);

            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }

    public PublicKey getPublicKey() {
        return rsaKeyPair.getPublic();
    }

    public boolean isFamilyRevoked(String familyId) {
        return revokedFamilies.contains(familyId);
    }

    private String hashSecret(String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(secret.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
