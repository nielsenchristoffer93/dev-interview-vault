# ⏱️ rate-limiting: Token Bucket vs Leaky Bucket & Sliding Window

## 1. Problem: Vad kraschar eller varför bryr vi oss?
Om ett API saknar hastighetsbegränsning (**Rate Limiting**) kan en enda felkonfigurerad klient, en scraper eller en DDoS-attack konsumera alla databasanslutningar och sänka tjänsten för samtliga användare (**Noisy Neighbor Problem**).

Dessutom implementerar många en naiv **Fixed Window Counter** (t.ex. max 100 requests per minut):
* Om en användare skickar 100 anrop kl `11:59:59` och ytterligare 100 anrop kl `12:00:01` passerar **200 anrop inom 2 sekunder**!
* Fönstergränsen fördubblar tillåten trafik och sänker databasen under trafikspikar (**Boundary Burst Vulnerability**).

---

## 2. Keyword Bingo (Facit-lingo)
* **Token Bucket Algoritm:**
  * En hink fylls på med *tokens* i en fast takt (t.ex. 10 tokens/sekund) upp till en maxkapacitet.
  * Varje request tar 1 token.
  * **Fördel:** Tillåter kontrollerade trafikspikar (**Burstiness**) så länge det finns tokens i hinken, samtidigt som genomsnittshastigheten hålls konstant.
* **Leaky Bucket Algoritm:**
  * Förfrågningar läggs i en kö och behandlas i en strikt konstant takt (FIFO).
  * **Fördel:** Jämnar ut trafik helt (**Traffic Smoothing**), men ökar latens för användare vid köbildning.
* **Sliding Window Counter:** Hybrid mellan fast fönster och sliding log som väger förra fönstrets aktivitet för att eliminera kantspikar vid minutskiften.
* **HTTP 429 Too Many Requests:** Standardiserad HTTP-statuskod när gränsen överskrids.
* **HTTP Headers:**
  * `X-RateLimit-Limit`: Max antal tillåtna anrop.
  * `X-RateLimit-Remaining`: Kvarvarande tokens i nuvarande fönster.
  * `Retry-After`: Antal sekunder klienten måste vänta innan nytt försök.
* **Distributed Rate Limiting:** Körs i Redis med ett atomärt **Lua Script** för att förhindra race conditions mellan flera API-noder.

---

## 3. Verklighets-svar (30–60 sek)
> *"För allmänna webb-API:er är **Token Bucket** den överlägset mest använda algoritmen eftersom den tillåter naturliga användarbeteenden med korta trafikspikar (bursts) utan att neka requests, så länge genomsnittshastigheten inte överskrids. Vi undviker enkla Fixed Window-räknare eftersom de kan släppa igenom dubbla kapaciteten över fönstergränser. I en distribuerad mikrotjänstmiljö implementerar vi Token Bucket eller Sliding Window Counter i Redis via ett atomärt Lua-script eller i API Gateway (Envoy/Kong). När en användare överskrider gränsen svarar vi med HTTP `429 Too Many Requests` och skickar med en `Retry-After`-header som talar om exakt när nästa token fylls på."*

---

## 4. Felsökningsguide (Kommandon & Verktyg)

### A. Inspektera Rate Limit Headers med curl
```bash
curl -i http://localhost:8080/api/resource
# Leta efter headers:
# HTTP/1.1 429 Too Many Requests
# Retry-After: 5
# X-RateLimit-Remaining: 0
```

### B. Benchmarka med Apache Bench (ab)
Skicka 150 anrop i snabb takt för att trigga 429:
```bash
ab -n 150 -c 10 http://localhost:8080/api/resource
```

---

## 5. Koden & Verifiering

* **The Flaw:** [FlawedFixedWindowLimiter.java](file:///system-design/rate-limiting/FlawedFixedWindowLimiter.java) – Fast fönster som tillåter dubbel trafikspik vid fönsterskiftet.
* **The Fix:** [TokenBucketRateLimiter.java](file:///system-design/rate-limiting/TokenBucketRateLimiter.java) – Matematisk Token Bucket som hanterar bursts och kontinuerlig påfyllning baserat på tidsskillnad.
* **Verification Test:** [RateLimiterVerificationTest.java](file:///system-design/rate-limiting/RateLimiterVerificationTest.java) – Bevisar att bursts upp till kapacitet tillåts men att överskridande anrop nekas med 429.
