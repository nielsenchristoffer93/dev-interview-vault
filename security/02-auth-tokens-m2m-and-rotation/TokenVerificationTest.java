package com.vault;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Security 02: Auth Tokens, M2M & Refresh Token Rotation Verification")
class TokenVerificationTest {

    // =========================================================================
    // SCENARIO 1: THE FLAW (STATISKA REFRESH TOKENS & SYMMETRISK LÄCKA)
    // =========================================================================
    @Nested
    @DisplayName("The Flaw: Statiska tokens och symmetriska förfalskningar")
    class FlawedTokenTests {

        @Test
        @DisplayName("The Flaw: Samma statiska refresh token kan återanvändas för evigt utan rotation")
        void testFlawedStaticTokenReplay() {
            FlawedTokenService flawedService = new FlawedTokenService("shared-secret-123");
            String staticToken = flawedService.issueStaticRefreshToken("user-alice");

            // Första anropet lyckas
            String access1 = flawedService.refreshAccessToken(staticToken);
            assertThat(access1).isNotNull();

            // Samma token skickas in igen (eller av en angripare som stulit den)
            String access2 = flawedService.refreshAccessToken(staticToken);
            assertThat(access2).isNotNull();

            // Bevis på felet: Inget fel kastas, ingen rotation sker, token förblir aktiv!
            assertThat(flawedService.getActiveStaticRefreshTokens()).contains(staticToken);
        }

        @Test
        @DisplayName("The Flaw: Med symmetrisk HS256 kan en läckt mikrotjänst förfalska egna admin-tokens")
        void testFlawedSymmetricKeyForging() {
            String sharedSecret = "company-global-jwt-secret";
            FlawedTokenService authServer = new FlawedTokenService(sharedSecret);

            // En perifer mikrotjänst med tillgång till sharedSecret förfalskar en administratörstoken:
            FlawedTokenService rogueMicroservice = new FlawedTokenService(sharedSecret);
            String forgedAdminToken = rogueMicroservice.signTokenSymmetric("user=evil,role=SUPER_ADMIN");

            // Auth servern och alla andra tjänster tror att token är legitim!
            boolean isValid = authServer.verifyTokenSymmetric(forgedAdminToken);
            assertThat(isValid).isTrue();
        }
    }

    // =========================================================================
    // SCENARIO 2: THE FIX (REFRESH TOKEN ROTATION & REUSE DETECTION)
    // =========================================================================
    @Nested
    @DisplayName("The Fix: RTR och Token Family Revocation vid Replay Attack")
    class ResilientRotationTests {

        @Test
        @DisplayName("The Fix: Refresh token roteras vid normal användning")
        void testSuccessfulRotation() {
            ResilientTokenService service = new ResilientTokenService();
            String initialToken = service.issueInitialRefreshToken("user-bob");

            // Legitim rotation:
            ResilientTokenService.RotationResult result1 = service.rotateRefreshToken(initialToken);
            assertThat(result1.newAccessToken()).isNotBlank();
            assertThat(result1.newRefreshToken()).isNotEqualTo(initialToken);

            // Nästa legitima anrop använder den nya tokenen:
            ResilientTokenService.RotationResult result2 = service.rotateRefreshToken(result1.newRefreshToken());
            assertThat(result2.newRefreshToken()).isNotEqualTo(result1.newRefreshToken());
        }

        @Test
        @DisplayName("The Fix: Replay av förbrukad refresh token triggar Reuse Detection och spärrar hela familjen")
        void testTokenReuseDetectionAndFamilyRevocation() {
            ResilientTokenService service = new ResilientTokenService();
            String tokenV1 = service.issueInitialRefreshToken("user-charlie");

            // 1. Legitim klient roterar tokenV1 -> får tokenV2
            ResilientTokenService.RotationResult legitimateResult = service.rotateRefreshToken(tokenV1);
            String tokenV2 = legitimateResult.newRefreshToken();

            // 2. Angripare (som stulit tokenV1) försöker använda samma tokenV1 igen!
            assertThatThrownBy(() -> service.rotateRefreshToken(tokenV1))
                    .isInstanceOf(ResilientTokenService.TokenReuseException.class)
                    .hasMessageContaining("Token reuse upptäckt");

            // 3. Bevis på fixen: Hela familjen spärras! Även den legitima tokenV2 nekas nu:
            assertThatThrownBy(() -> service.rotateRefreshToken(tokenV2))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Sessionen är avslutad. Token-familjen är spärrad.");
        }
    }

    // =========================================================================
    // SCENARIO 3: THE FIX (M2M CLIENT CREDENTIALS & ASYMMETRISK SIGNERING)
    // =========================================================================
    @Nested
    @DisplayName("The Fix: M2M Scoping och Asymmetrisk RS256-validering")
    class ResilientM2MAndAsymmetricTests {

        @Test
        @DisplayName("The Fix: M2M Client Credentials beviljar enbart godkända scopes (Least Privilege)")
        void testM2MScopedAccess() {
            ResilientTokenService service = new ResilientTokenService();
            service.registerM2MClient(
                    "invoice-batch-worker",
                    "worker-secret-456",
                    Set.of("orders:read", "invoices:create")
            );

            // Godkända scopes utfärdas framgångsrikt
            String validToken = service.issueM2MToken(
                    "invoice-batch-worker",
                    "worker-secret-456",
                    Set.of("orders:read")
            );
            assertThat(validToken).contains("m2m:invoice-batch-worker:scopes=[orders:read]");

            // Otillåtet scope nekas direkt
            assertThatThrownBy(() -> service.issueM2MToken(
                    "invoice-batch-worker",
                    "worker-secret-456",
                    Set.of("admin:delete-users")
            )).isInstanceOf(SecurityException.class)
              .hasMessageContaining("Nekad: Klienten saknar behörighet");
        }

        @Test
        @DisplayName("The Fix: Asymmetrisk signering tillåter verifiering med publik nyckel utan förfalskningsrisk")
        void testAsymmetricVerificationPreventsForging() throws Exception {
            ResilientTokenService authServer = new ResilientTokenService();
            PublicKey authPublicKey = authServer.getPublicKey();

            // Legitim token signeras av Auth-servern med privat nyckel
            String legitimateToken = authServer.signTokenAsymmetric("sub=alice,role=USER");

            // Nedströms mikrotjänst kan validera med publik nyckel:
            boolean isValid = ResilientTokenService.verifyTokenAsymmetric(legitimateToken, authPublicKey);
            assertThat(isValid).isTrue();

            // Om en angripare eller mikrotjänst försöker manipulera payloaden:
            String tamperedToken = legitimateToken.replace("role=USER", "role=ADMIN");
            boolean isTamperedValid = ResilientTokenService.verifyTokenAsymmetric(tamperedToken, authPublicKey);
            assertThat(isTamperedValid).isFalse();

            // Om angriparen försöker signera med sitt eget genererade RSA-nyckelpar:
            KeyPairGenerator rogueGen = KeyPairGenerator.getInstance("RSA");
            rogueGen.initialize(2048);
            var rogueKeys = rogueGen.generateKeyPair();

            java.security.Signature rogueSigner = java.security.Signature.getInstance("SHA256withRSA");
            rogueSigner.initSign(rogueKeys.getPrivate());
            rogueSigner.update("sub=attacker,role=ADMIN".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] rogueSig = rogueSigner.sign();
            String forgedToken = "rogue-kid.sub=attacker,role=ADMIN." +
                    java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(rogueSig);

            // Validering mot den officiella publika nyckeln misslyckas direkt!
            boolean isForgedValid = ResilientTokenService.verifyTokenAsymmetric(forgedToken, authPublicKey);
            assertThat(isForgedValid).isFalse();
        }
    }
}
