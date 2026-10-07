# ⏱️ rate-limiting: Token Bucket vs Leaky Bucket & Sliding Window

Denna modul täcker 4 fundamentala hastighetsbegränsnings- och skyddsmönster (Rate Limiting) för webb-API:er och mikrotjänster. Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 1: Fixed Window Counter & Boundary Burst Vulnerability

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Om ett API saknar rate limiting kan en felkonfigurerad klient, scraper eller DoS-attack sänka databasen för samtliga användare (**Noisy Neighbor Problem**).

Den enklaste implementationen är ofta en **Fixed Window Counter** (t.ex. max 100 anrop per minut, nollställs varje hel minut). Men denna lösning lider av en allvarlig sårbarhet: **Boundary Burst Vulnerability**.
Om en illasinnad eller burstig klient skickar 100 anrop kl `11:59:59` och ytterligare 100 anrop kl `12:00:01` passerar **200 anrop inom 2 sekunder**! Systemet släpper igenom dubbla tillåtna kapaciteten över fönstergränsen och databasen kraschar.

#### 2. Keyword Bingo (Facit-lingo)
* **Fixed Window Counter:** Enkel räknare bunden till fasta klockintervall (t.ex. per kalenderminut).
* **Boundary Burst Vulnerability:** Sårbarheten där dubbla maxkapaciteten släpps igenom om anrop koncentreras kring ett minutskifte.
* **Noisy Neighbor Problem:** När en enda hyperaktiv användare förbrukar alla delade resurser på bekostnad av andra kunder.
* **HTTP 429 Too Many Requests:** Den officiella HTTP-statuskoden som ska returneras när gränsen nås.

#### 3. Verklighets-svar (30–60 sek)
> *"Fixed Window är en lockande men trasig algoritm på grund av Boundary Burst-problemet. Om en klient skickar maxkvoten precis före minutskiftet och maxkvoten igen precis efter, släpper systemet igenom dubbla kapaciteten på bara ett par sekunder. Det kan sänka databasen under trafiktoppar. Därför använder vi aldrig rena fasta fönster för produktions-API:er, utan går över till Token Bucket eller Sliding Window Counter som garanterar att gränserna hålls över alla rullande tidsintervall."*

**Labb & Kod:** [FlawedFixedWindowLimiter.java](file:///system-design/rate-limiting/FlawedFixedWindowLimiter.java) vs [TokenBucketRateLimiter.java](file:///system-design/rate-limiting/TokenBucketRateLimiter.java)

---

### Scenario 2: Token Bucket Algoritmen & Bursts vs Steady State

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Verklig användartrafik är inte jämnt utspridd per millisekund: när en användare öppnar en webbsida gör webbläsaren ofta 10–15 parallella anrop för att ladda data, följt av 30 sekunders inaktivitet.

En för rigid rate limiter som nekar alla anrop som överstiger 1 per sekund ger användarna en usel upplevelse med onödiga 429-fel under normal sidladdning.

#### 2. Keyword Bingo (Facit-lingo)
* **Token Bucket Algoritm:**
  * En virtuell hink fylls kontinuerligt på med tokens i en jämn takt (`refillRate`, t.ex. 10 tokens/sekund) upp till en maxkapacitet (`bucketCapacity`, t.ex. 50 tokens).
  * Varje inkommande request förbrukar 1 token.
* **Burstiness (Trafikspikar):** Algoritmens unika fördel: tillåter legitima korta trafikspikar så länge det finns ackumulerade tokens i hinken, samtidigt som långsiktig genomsnittshastighet kontrolleras strikt.
* **Continuous Refill via Timestamp:** Matematisk beräkning `tokensToAdd = (now - lastRefillTime) * refillRate` utan behov av bakgrundstrådar.

#### 3. Verklighets-svar (30–60 sek)
> *"För allmänna webb-API:er är **Token Bucket** industristandarden eftersom den stödjer naturligt användarbeteende med korta trafikspikar (bursts). Hinken fylls på med tokens i en konstant takt upp till en maxgräns. När en användare öppnar en vy och skickar 10 snabba anrop tillåts detta direkt från den ackumulerade hinken. Men om anstormningen fortsätter och hinken töms nekas efterföljande anrop med 429. Vi implementerar påfyllningen rent matematiskt baserat på tidsdifferensen sedan förra anropet, vilket gör den extremt snabb och resurssnål."*

**Labb & Kod:** [TokenBucketRateLimiter.java](file:///system-design/rate-limiting/TokenBucketRateLimiter.java) & [RateLimiterVerificationTest.java](file:///system-design/rate-limiting/RateLimiterVerificationTest.java)

---

### Scenario 3: Leaky Bucket vs Sliding Window Counter (Traffic Smoothing)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Vissa nedströmstjänster (såsom äldre betalväxlar, externa SMS-gateways eller långsamma skrivdiskar) tål **inga spikar alls**. Även en 1-sekunds burst på 20 anrop kraschar partnerns API.

Här är Token Bucket fel val eftersom den tillåter bursts. Hur tvingar vi trafiken att flöda med en 100% jämn och konstant takt?

#### 2. Keyword Bingo (Facit-lingo)
* **Leaky Bucket Algoritm:**
  * Inkommande förfrågningar placeras i en kö (hinken).
  * Hinken läcker ut förfrågningar i en strikt konstant hastighet (FIFO).
  * Om kön blir full droppas överskjutande anrop omedelbart.
* **Traffic Smoothing / Traffic Shaping:** Att helt eliminera spikar och tvinga fram en jämn, kontrollerad utströmning mot sköra bakomliggande system.
* **Sliding Window Counter:** En minnessnål hybrid som väger förra minutens anrop med nuvarande minutens förflutna tid för att eliminera kantspikar utan att kräva köer.

#### 3. Verklighets-svar (30–60 sek)
> *"Skillnaden mellan Token Bucket och Leaky Bucket är hur de hanterar trafikspikar. Token Bucket tillåter bursts av inkommande anrop men begränsar genomsnittet. Leaky Bucket agerar som en kö och läcker ut anrop i en strikt konstant takt (Traffic Shaping). Om vi skyddar ett eget API använder vi Token Bucket för bäst användarupplevelse. Men om vi anropar en skör extern partner som kraschar vid minsta spik placerar vi en Leaky Bucket framför anropen för att garantera att vi aldrig skickar mer än exakt N anrop per sekund mot partnern."*

**Labb & Kod:** [TokenBucketRateLimiter.java](file:///system-design/rate-limiting/TokenBucketRateLimiter.java)

---

### Scenario 4: Distributed Rate Limiting i Redis med atomära Lua Scripts

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I en modern molnmiljö körs applikationen på 10 horisontellt skalade instanser bakom en Load Balancer. Om varje instans har en lokal minnesbaserad rate limiter kan en angripare skicka $10 \times 100 = 1 000$ anrop per minut bara genom att fördela trafiken över de olika noderna.

Att synkronisera via Redis med separata `GET` och `SET` skapar dessutom en klassisk *Check-then-Act Race Condition* där två instanser läser samma tokenräknare samtidigt.

#### 2. Keyword Bingo (Facit-lingo)
* **Distributed Rate Limiting:** En centraliserad räknare i Redis som delas av alla API-servrar i klustret.
* **Redis Lua Scripting:** Att köra rate limit-logiken (hämta tid, beräkna påfyllning, dra token, sätt TTL) inuti ett atomärt Lua-skript i Redis motor, vilket eliminerar alla race conditions.
* **HTTP Headers:**
  * `X-RateLimit-Limit`: Maximalt tillåten kvot.
  * `X-RateLimit-Remaining`: Kvarvarande tokens.
  * `Retry-After: 5`: Antal sekunder klienten måste pausa innan nästa token blir tillgänglig.
* **API Gateway Integration:** Att köra rate limiting direkt i Envoy, NGINX eller Kong före applikationslagret.

#### 3. Verklighets-svar (30–60 sek)
> *"I en distribuerad mikrotjänstmiljö måste rate limiting delas centralt mellan alla instanser. Vi placerar begränsningen i Redis och exekverar den via ett atomärt Lua-script. Eftersom Redis kör skriptet i en enda atomär operation elimineras alla race conditions mellan våra noder. När ett anrop nekas returnerar vi HTTP 429 Too Many Requests och skickar alltid med `X-RateLimit-Remaining` samt en `Retry-After`-header som talar om för klienten exakt hur många sekunder den måste vänta innan ett nytt token fylls på."*

**Labb & Kod:** Avsnitt 2A nedan

---

## 2. Felsökningsguide (Kommandon & Verktyg)

### A. Inspektera Rate Limit Headers med curl
```bash
curl -i http://localhost:8080/api/resource
# Leta efter headers:
# HTTP/1.1 429 Too Many Requests
# Retry-After: 5
# X-RateLimit-Remaining: 0
```

### B. Benchmarka med Apache Bench (ab)
Skicka 150 anrop i snabb takt för att verifiera att gränsen bryts:
```bash
ab -n 150 -c 10 http://localhost:8080/api/resource
```

---

## 3. Koden & Verifiering

* **The Flaw:** [FlawedFixedWindowLimiter.java](file:///system-design/04-rate-limiting/FlawedFixedWindowLimiter.java) – Fast fönster som tillåter dubbel trafikspik vid fönsterskiftet.
* **The Fix:** [TokenBucketRateLimiter.java](file:///system-design/04-rate-limiting/TokenBucketRateLimiter.java) – Matematisk Token Bucket som hanterar bursts och kontinuerlig påfyllning baserat på tidsskillnad.
* **Verification Test:** [RateLimiterVerificationTest.java](file:///system-design/04-rate-limiting/RateLimiterVerificationTest.java) – Bevisar att bursts upp till kapacitet tillåts men att överskridande anrop nekas med 429.

---

## 4. Så här kör du testerna

```bash
cd system-design/04-rate-limiting
mvn test
```
