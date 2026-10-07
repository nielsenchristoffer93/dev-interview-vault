# 🚀 System Design: Distributed Caching (Thundering Herd & Cache Invalidation)

> **Intervju-fokus:** Cache Stampede / Dogpiling, Single-Flight / Mutex Locking, Cache-Aside race conditions (Evict vs Update), samt HTTP `stale-while-revalidate` och ETags.

---

## 🎯 Scenario 1: Cache Stampede (Thundering Herd / Dogpiling)

### Problem (Vad kraschar / varför bryr vi oss)
En e-handelswebbplats kör Black Friday. Startsidan har en extremt het produktkatalog (`catalog:featured`) som servar 10 000 anrop/sekund från Redis med en TTL på 10 minuter.
Klockan 12:00:00 löper TTL ut och nyckeln tas bort från Redis.

**Kraschen:**
* Inom loppet av 10 millisekunder anländer 2 500 samtidiga användarförfrågningar.
* Samtliga 2 500 trådar upptäcker en **Cache Miss** i Redis.
* Samtliga 2 500 trådar skickar samtidigt den tunga SQL-frågan (`SELECT * FROM products JOIN inventory ...`) direkt till PostgreSQL-databasen för att "värma" cachen.
* PostgreSQLs CPU spikar till 100%, anslutningspoolen (`HikariCP`) töms, och databasen börjar kasta `ConnectionTimeoutException`.
* Backend-instanserna får timeouts, minnesåtgången exploderar och hela plattformen går ner i ett totalt **Cascading Failure**.

---

### Keyword Bingo (Facit-lingo)
* **Cache Stampede (Thundering Herd / Dogpiling):** Katastroftillståndet när en högfrekvent cache-nyckel upphör att gälla och tusentals samtidiga trådar samtidigt överbelastar databasen för att beräkna om samma värde.
* **Single-Flight (Request Coalescing):** Mönster där endast en enda pågående beräkning tillåts per unik nyckel; alla andra samtidiga anrop hakar på och väntar på samma framtida resultat.
* **Distributed Mutex (Redis `SET NX EX`):** Att låta den första tråden som missar cachen sätta ett distribuerat lås med kort TTL; övriga trådar väntar eller returnerar gammal data tills låset släpps.
* **Probabilistic Early Expiration (XFetch-algoritmen):** En algoritm där klienter börjar räkna om cachen *innan* den formellt löper ut baserat på beräkningstid och slumpmässighet, vilket eliminerar hårda cache-missar.
* **Cache Warm-up / Pre-warming:** Att ladda in frekvent efterfrågad data i cachen under driftsättning eller via bakgrundsjobb innan kundtrafik tillåts.
* **Negative Caching:** Att cacha "hittades inte" (`null` med kort TTL, t.ex. 30s) för att förhindra att angripare överbelastar databasen genom att fråga efter icke-existerande ID:n (**Cache Penetration**).

---

### Verklighets-svar (30–60 sek)
> "När en het cache-nyckel med 10 000 req/s plötsligt går ut drabbas systemet av en **Cache Stampede (Thundering Herd)** där tusentals trådar samtidigt försöker bygga om samma värde mot databasen, vilket sänker DB-anslutningspoolen.
> 
> Som senior förhindrar jag detta med **Single-Flight / Mutex Locking**:
> När en cache miss inträffar försöker tråden ta ett distribuerat lås i Redis med `SET key:lock uuid NX EX 5`. Endast den vinnande tråden kör den tunga databasfrågan och uppdaterar cachen. De andra trådarna väntar i 50 ms och läser det nyberäknade värdet från cachen. För ultra-kritiska läsningar kombinerar jag detta med 'stale-while-revalidate' så att användare omedelbart får den gamla datan medan en bakgrundstråd uppdaterar cachen."

---

## 🎯 Scenario 2: Cache-Aside Consistency & Race Conditions (Evict vs Update)

### Problem (Vad kraschar / varför bryr vi oss)
Ett team vill hålla cachen synkroniserad med databasen vid orderuppdateringar. En utvecklare skriver följande logik:
```java
// Anti-mönster: Uppdaterar cache direkt i applikationskoden
@Transactional
public void updatePrice(String productId, BigDecimal newPrice) {
    productRepository.updatePrice(productId, newPrice); // 1. DB-skrivning
    redisTemplate.opsForValue().set("product:" + productId, newPrice); // 2. Cache-uppdatering
}
```

**Katastrofscenarierna:**
1. **Transaktionsrollback:** Databasen kastar ett fel under `commit` (t.ex. constraint-fel), och transaktionen rullas tillbaka. Men rad 2 har redan uppdaterat Redis! Cachen visar nu ett felaktigt pris som aldrig sparades i databasen (**Permanent Dirty Read**).
2. **Race Condition vid samtidiga skrivningar:**
   * Tråd A uppdaterar databasen till pris `100 kr`.
   * Tråd B uppdaterar databasen till pris `120 kr`.
   * Tråd B hinner uppdatera cachen till `120 kr`.
   * Tråd A (som drabbades av en kort GC-paus) vaknar och skriver sitt gamla pris `100 kr` till cachen!
   * **Resultat:** Databasen har `120 kr`, men cachen visar `100 kr` i timmar framöver!

---

### Keyword Bingo (Facit-lingo)
* **Cache-Aside (Lazy Loading):** Arkitekturmönster där applikationen först läser från cachen; vid miss läses databasen och cachen fylls på för framtida anrop.
* **Cache Eviction over Cache Update:** Regeln att alltid *radera* nyckeln (`DEL`) från cachen vid ändringar snarare än att försöka uppdatera värdet, vilket eliminerar skriv-race conditions.
* **Transactional After-Commit Invalidation:** Att binda cache-raderingen till `@TransactionalEventListener(phase = AFTER_COMMIT)` så att cachen enbart rensas om databastransaktionen faktiskt lyckades committas.
* **Write-Through vs Write-Behind (Write-Back):**
  * *Write-Through:* Cachen agerar huvuddatakälla och uppdaterar databasen synkront.
  * *Write-Behind:* Cachen samlar skrivningar och uppdaterar databasen asynkront i batcher (risk för dataförlust vid cachekrasch).
* **Dual-Write Consistency Problem:** Det grundläggande problemet i distribuerade system att uppdatera två oberoende datalager (SQL + Redis) utan distribuerade transaktioner.

---

### Verklighets-svar (30–60 sek)
> "Att försöka uppdatera cachevärden direkt vid skrivningar är en klassisk buggfälla som leder till inkonsistens så fort två trådar skriver samtidigt eller om databastransaktionen rullas tillbaka.
> 
> Gyllene regeln för distribuerad cache är: **Evict, don't update – och evicta alltid AFTER COMMIT**.
> Istället för att gissa vad det nya cachevärdet ska bli raderar vi helt enkelt nyckeln med `redis.del(key)` först efter att SQL-transaktionen framgångsrikt har committats. Nästa läsare gör en ren Cache-Aside hämtning från databasen. Skulle två skrivningar krocka resulterar båda bara i raderingar, vilket garanterar att den slutgiltiga läsningen alltid hämtar sanningen från databasen."

---

## 🎯 Scenario 3: HTTP Caching & CDN Stale-While-Revalidate

### Problem (Vad kraschar / varför bryr vi oss)
Ett team bygger en publik produktkatalog. Varje gång kunden laddar sidan görs en nätverksbegäran till backend.
När trafiken når 50 000 användare slår backend i taket, trots att 99% av alla produkter inte har ändrats på flera dagar.
Teamet sätter `Cache-Control: max-age=3600` på Cloudflare/CDN.

**Det nya problemet:**
När en administratör sänker priset på en produkt ser kunderna det gamla priset i en hel timme eftersom CDN vägrar fråga backend. När teamet sänker till `max-age=0` slår databasen i taket igen.

---

### Keyword Bingo (Facit-lingo)
* **`Cache-Control: s-maxage`:** Direktiv för delade cacher (CDN, Reverse Proxies) som åsidosätter `max-age` (som enbart styr slutanvändarens privata webbläsare).
* **`stale-while-revalidate`:** HTTP-direktiv som instruerar CDN och browser att *omedelbart returnera ett utgånget (stale) svar till användaren* (0 ms latens), samtidigt som CDN asynkront skickar en bakgrundsförfrågan till ursprungsservern för att uppdatera cachen.
* **ETag (Entity Tag) & `If-None-Match`:** Valideringstoken (hash av innehållet). Klienten skickar `If-None-Match: "hash123"`; om resursen är oförändrad returnerar servern **HTTP 304 Not Modified** med en tom body (sparar 99% bandbredd och CPU).
* **`Cache-Control: no-cache` vs `no-store`:**
  * *`no-cache`:* Innehållet får cachas, men *måste alltid valideras mot servern* (via ETag) innan det visas.
  * *`no-store`:* Innehållet får *aldrig sparas på disk eller minne någonstans* (används för känsliga personuppgifter och bankdata).

---

### Verklighets-svar (30–60 sek)
> "Att välja mellan antingen lång cache-tid (gammal data) eller ingen cache alls (överbelastad backend) är ett falskt dilemma. Med moderna HTTP-headers kombinerar vi blixtsnabb prestanda med färskhet.
> 
> Genom att konfigurera `Cache-Control: public, s-maxage=600, stale-while-revalidate=60` i kombination med **ETags**:
> 1. Användaren får omedelbart svar från CDN:s edge-cache på 5 ms.
> 2. När cachen har gått ut serverar CDN fortfarande det gamla svaret utan väntetid (`stale`), medan en bakgrundstråd hämtar det nya värdet från backend.
> 3. Om datan inte har ändrats svarar backend med ett snabbt **304 Not Modified** utan body. Detta skyddar backend från trafikspikar samtidigt som användaren aldrig upplever latens."

---

## 🛠️ Koden & Verifiering

* **The Flaw & The Fix:** [DistributedCachingLab.java](file:///system-design/01-caching-thundering-herd/DistributedCachingLab.java)
  * *The Flaw:* `FlawedCacheService` tillåter alla trådar att köra den tunga databasfrågan parallellt vid cache miss (Thundering Herd); `flawedUpdate` orsakar dirty read race condition.
  * *The Fix:* `SingleFlightCacheService` samordnar samtidiga anrop så att exakt 1 tråd gör databasfrågan och övriga väntar; `safeEvictAfterCommit` raderar nyckeln efter commit istället för att uppdatera; `HttpCacheController` validerar ETag och returnerar 304 Not Modified.
* **Verification Test:** [DistributedCachingVerificationTest.java](file:///system-design/01-caching-thundering-herd/DistributedCachingVerificationTest.java)
  * Bevisar att 20 samtidiga trådar utan låsning skickar 20 queries till databasen.
  * Bevisar att Single-Flight reducerar 20 samtidiga trådar till exakt 1 databasfråga.
  * Bevisar att cache eviction förhindrar dirty reads.
  * Bevisar att HTTP ETag returnerar 304 utan svarsbody.

---

## 🧪 Så här kör du testerna

```bash
cd system-design/01-caching-thundering-herd
mvn test
```
