# 🛡️ Security: Auth Tokens, M2M (Machine-to-Machine) & Refresh Token Rotation

> **Intervju-fokus:** OAuth2 Client Credentials Grant (M2M) vs User Grants, Refresh Token Rotation (RTR) med Reuse Detection / Token Families, samt Asymmetrisk JWT-signering (RS256/EdDSA & JWKS) vs Symmetrisk HS256-läcka.

---

## 🎯 Scenario 1: Machine-to-Machine (M2M) vs User Grant Fällan

### Problem (Vad kraschar / varför bryr vi oss)
En nattlig bakgrundsjobb-tjänst (`invoice-batch-worker`) behöver hämta alla ordrar från `order-service` och debitera via `payment-service`.
En utvecklare "löser" integrationen genom att hårdkoda en personlig användar-token eller använda Authorization Code Grant med ett fejkat användarkonto (`system-admin@company.com`).
Sex månader senare:
1. Den personliga användaren slutar och IT avaktiverar kontot i Active Directory / Okta. 
2. Alla nattliga batchjobb kraschar med `401 Unauthorized`.
3. Inga fakturor skickas och orderflödet stoppas!

Eller ännu värre: 
Batchjobbet hade fulla användarrättigheter (inklusive att ändra lösenord och radera konton) istället för minsta nödvändiga behörighet (`Least Privilege`).

---

### Keyword Bingo (Facit-lingo)
* **OAuth2 Client Credentials Grant (RFC 6749):** Den officiella standarden för Machine-to-Machine (M2M) autentisering där ett system autentiserar sig med `client_id` och `client_secret` utan någon mänsklig användare eller webbläsare inblandad.
* **M2M Scopes & Least Privilege:** Att begränsa maskinens åtkomst till enbart specifika maskinoperationer (t.ex. `scope: orders:read invoices:create`), utan tillgång till användarspecifika sessioner.
* **No Refresh Token for M2M:** Till skillnad från användarflöden utfärdas inga refresh tokens i Client Credentials grant. När access token går ut begär maskinen helt enkelt en ny token direkt med sina maskin-credentials.
* **Service Account / Machine Identity:** En dedikerad maskinidentitet i Identity Providern (t.ex. Auth0, Keycloak, AWS IAM, Azure Entra ID) avskild från fysiska personers konton.
* **mTLS (Mutual TLS) & DPoP (RFC 9449):** Avancerad säkring där tokens binds kryptografiskt till maskinens certifikat eller nyckelpar för att förhindra stöld av Bearer-tokens i transit.

---

### Verklighets-svar (30–60 sek)
> "Att använda användar-tokens eller fejkade servicekonton för systemintegrationer är en klassisk säkerhets- och driftsrisk. Användartokens är knutna till individer, kräver interaktiv inloggning och har ofta alltför breda behörigheter.
> 
> För bakgrundsjobb och mikrotjänster implementerar vi alltid **OAuth2 Client Credentials Grant (M2M)**:
> 1. Tjänsten autentiserar sig mot Identity Providern med sitt unika `client_id` och roterbara `client_secret` (lagrat i Vault eller AWS Secrets Manager).
> 2. IdP:n returnerar en kortlivad JWT access token (t.ex. 15 minuter) med strikt begränsade scopes (t.ex. `orders:read`).
> 3. Klienten cachar token lokalt och begär en ny automatisk när den närmar sig utgångsdatum – inga refresh tokens eller mänskliga sessioner behövs."

---

## 🎯 Scenario 2: Refresh Token Rotation (RTR) & Token Reuse Detection

### Problem (Vad kraschar / varför bryr vi oss)
En SPA (Single Page Application) använder JWT. 
För att slippa logga ut användaren var 15:e minut sparar appen en statisk **Refresh Token** som är giltig i 30 dagar.
En angripare lyckas stjäla refresh token via en XSS-sårbarhet eller en komprometterad laptop.
Eftersom samma refresh token kan återanvändas om och om igen kan angriparen generera nya access tokens i en hel månad utan att varken användaren eller säkerhetsteamet märker något!

---

### Keyword Bingo (Facit-lingo)
* **Refresh Token Rotation (RTR):** Säkerhetsmekanism där varje anrop till token-endpointen för att förnya en access token *ogiltigförklarar den gamla refresh token* och utfärdar en helt ny refresh token i samma svar.
* **Token Reuse Detection (Replay Protection):** Mekanismen som upptäcker om en redan förbrukad refresh token skickas in igen.
* **Token Family Invalidation:** Om återanvändning upptäcks (vilket indikerar att antingen den legitima användaren eller en tjuv sitter på en kopia) ogiltigförklarar Auth-servern omedelbart **hela token-familjen** och tvingar en fullständig ny inloggning.
* **Sliding Session Window:** Att sessionens giltighetstid förlängs dynamiskt vid aktivitet upp till ett absolut tak (`absolute_timeout`).
* **HttpOnly, Secure, SameSite Cookie:** Bästa praxis för att lagra refresh tokens i webbläsaren för att skydda dem mot XSS-exfiltrering.

---

### Verklighets-svar (30–60 sek)
> "Statiska refresh tokens är en tickande bomb. Om en token läcker har angriparen kontinuerlig access i veckor.
> 
> Lösningen är **Refresh Token Rotation (RTR) med Token Reuse Detection**:
> 1. Varje gång en refresh token används konsumeras den och bränns direkt – klienten får tillbaka en ny access token OCH en ny unik refresh token.
> 2. Vi grupperar tokens i en logisk **Token Family**. 
> 3. Om servern tar emot en refresh token som redan har konsumerats vet vi att en 'token race' eller stöld har inträffat. Servern dödar då omedelbart hela token-familjen, raderar sessionen och tvingar användaren att logga in på nytt med MFA. Detta begränsar skadan från en läckt token till maximalt ett enda anrop."

---

## 🎯 Scenario 3: Asymmetrisk Signering (RS256 / EdDSA & JWKS) vs Symmetrisk HS256-fällan

### Problem (Vad kraschar / varför bryr vi oss)
En organisation har 30 mikrotjänster som validerar inkommande JWT:er.
För att göra det enkelt använder de symmetrisk kryptering med **HS256** och en gemensam hemlig sträng:
```text
JWT_SECRET=super-secret-company-key-2024
```
Alla 30 tjänster måste ha denna hemlighet i sin konfiguration för att kunna verifiera signaturen.
En junior utvecklare råkar committa `.env`-filen för `notification-service` till ett publikt repo, eller en perifera container blir hackad.
Angriparen har nu samma hemlighet som autentiseringstjänsten!
Med den hemligheten kan angriparen **skapa och signera sina egna falska JWT:er med `role: ADMIN`** och lura samtliga 30 tjänster i hela infrastrukturen!

---

### Keyword Bingo (Facit-lingo)
* **Symmetric Signing (HS256 HMAC):** Symmetrisk signering där samma hemliga nyckel används för att både signera och verifiera tokens. Kräver att alla verifierande tjänster känner till hemligheten.
* **Asymmetric Signing (RS256 / ES256 / EdDSA):** Asymmetrisk kryptografi med ett privat/publikt nyckelpar. Enbart Auth-servern har den privata nyckeln och kan signera tokens; alla mikrotjänster behöver enbart den publika nyckeln för validering.
* **JWKS (JSON Web Key Set - RFC 7517):** Ett standardiserat HTTP-endpoint (typiskt `/.well-known/jwks.json`) där mikrotjänster hämtar publika nycklar dynamiskt.
* **Key ID (`kid` Header):** JWT-headerns fält som talar om för verifieraren exakt vilken publik nyckel i JWKS-listan som användes för signaturen.
* **Seamless Key Rotation:** Att rotera kryptografiska nycklar utan driftstopp genom att publicera en ny publik nyckel i JWKS innan Auth-servern börjar signera med den nya privata nyckeln.

---

### Verklighets-svar (30–60 sek)
> "Att använda symmetrisk HS256 i en distribuerad mikrotjänstarkitektur bryter mot grundläggande principen om separationsansvar. Den som kan verifiera en token kan också förfalska en token. En läcka i en enda perifer container komprometterar hela organisationens säkerhet.
> 
> I produktion använder vi uteslutande **Asymmetrisk signering (RS256 eller EdDSA) med JWKS**:
> 1. Auth-servern är den enda som känner till den **privata nyckeln** (förvarad i HSM eller AWS KMS) och signerar tokens med en specifik `kid` (Key ID).
> 2. Våra mikrotjänster känner enbart till den **publika nyckeln**, som de cachar automatiskt från `/.well-known/jwks.json`.
> 3. Om en mikrotjänst blir hackad finns det ingen hemlighet att stjäla – angriparen kan aldrig förfalska tokens. Dessutom möjliggör `kid` noll-downtime nyckelrotation."

---

## 🛠️ Koden & Verifiering

* **The Flaw:** [FlawedTokenService.java](file:///security/02-auth-tokens-m2m-and-rotation/FlawedTokenService.java)
  * Statiska oåterkalliga refresh tokens som tillåter replay-attacker utan upptäckt.
  * Symmetrisk HS256-validering där verifieraren kan förfalska tokens.
* **The Fix:** [ResilientTokenService.java](file:///security/02-auth-tokens-m2m-and-rotation/ResilientTokenService.java)
  * Refresh Token Rotation med Token Family & Replay/Reuse Detection (ogiltigförklarar hela familjen vid reuse).
  * OAuth2 M2M Client Credentials Token Issuer med scoped access.
  * Asymmetrisk RSA-verifiering (separat privat signeringsnyckel och publik verifieringsnyckel).
* **Testerna:** [TokenVerificationTest.java](file:///security/02-auth-tokens-m2m-and-rotation/TokenVerificationTest.java)
  * Verifierar Token Family revocation vid reuse-attack, M2M Client Credentials validering och asymmetrisk verifiering.

---

## 🧪 Så här kör du testerna

```bash
cd security/02-auth-tokens-m2m-and-rotation
mvn test
```
eller från repo-root via WSL:
```bash
wsl bash -l -c "source ~/.sdkman/bin/sdkman-init.sh && cd /mnt/c/Users/s103228/.repositories/dev-interview-vault/security/02-auth-tokens-m2m-and-rotation && mvn test"
```
