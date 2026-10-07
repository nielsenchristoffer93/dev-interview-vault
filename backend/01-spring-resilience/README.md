# 🛡️ 01-spring-resilience: Circuit Breakers, Retries & Timeouts

Denna modul täcker 5 verksamhetskritiska feltolerans- och motståndskraftsmönster (Resilience) i distribuerade Spring Boot-system. Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 1: Thread Pool Starvation & Cascading Failures (Hängande anrop)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Ett anrop mot en extern nedströmstjänst (t.ex. en betalnings-gateway) svarar inte eller drabbas av extrem latens (>30s). Eftersom HTTP-klienten saknar strikta timeouts låser varje inkommande förfrågan en Tomcat-worker-tråd (`http-nio-8080-exec-*`). 

Inom loppet av sekunder är Tomcats trådpool helt tömd (**Thread Pool Starvation**). Hela mikrotjänsten fryser och kan inte ens ta emot orelaterade förfrågningar, vilket sprider krisen uppströms i ett kaskadfel (**Cascading Failure**).

#### 2. Keyword Bingo (Facit-lingo)
* **Thread Pool Starvation:** När samtliga trådar i en exekveringspool blockeras av långsamma I/O-anrop och inga nya requests kan schemaläggas.
* **Cascading Failure (Kaskadfel):** När ett fel i en enskild nedströmstjänst drar med sig uppströms mikrotjänster i en dominoeffekt.
* **Socket / Read Timeout:** Den maximala tiden att vänta på nätverkspaket från servern innan anslutningen klipps.
* **TimeLimiter:** Resilience4j-abstraktionen som klipper anropet asynkront och frigör anropstråden omedelbart vid tidsgräns.
* **Fail-Fast:** Att avbryta dödsdömda operationer på millisekunder snarare än att låta trådar vänta i tiotals sekunder.

#### 3. Verklighets-svar (30–60 sek)
> *"Om vi anropar en extern tjänst utan strikta timeouts kan en seg nedströmstjänst sänka hela vår mikrotjänst. Eftersom webbservern allokerar en tråd per request kommer hängande anrop att dränera trådpoolen inom några sekunder – så kallad Thread Pool Starvation. Vårt första försvar är alltid en TimeLimiter och strikta socket-timeouts som klipper anropet och omedelbart frigör tråden till andra användare, kombinerat med en fail-fast fallback."*

**Labb & Kod:** [UnprotectedPaymentClient.java](file:///backend/01-spring-resilience/src/main/java/com/vault/resilience/UnprotectedPaymentClient.java) (`/unsafe-hang`) vs [ResilientPaymentClient.java](file:///backend/01-spring-resilience/src/main/java/com/vault/resilience/ResilientPaymentClient.java) (`/safe-timeout`)

---

### Scenario 2: Circuit Breaker Lifecycle & Fail-Fast

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
När en extern mikrotjänst havererar (t.ex. svarar med 500 Internal Server Error) fortsätter traditionella klienter att hamra tjänsten med full trafik. Detta slösar CPU- och nätverksresurser hos oss och förhindrar att den fallerande tjänsten någonsin hinner återhämta sig.

Utan kretsbrytare drabbas varje enskilt request av onödig fördröjning och felmeddelanden sprids okontrollerat.

#### 2. Keyword Bingo (Facit-lingo)
* **Circuit Breaker States:**
  * `CLOSED`: Normal drift. Trafik passerar obehindrat och felkvot registreras i ett glidande fönster (Sliding Window).
  * `OPEN`: Kretsen är bruten. Samtliga anrop nekas omedelbart med `CallNotPermittedException` utan något nätverksanrop (**Fail-fast**).
  * `HALF-OPEN`: Efter en karenstid (`waitDurationInOpenState`) släpps ett begränsat provurval av anrop igenom för att testa beroendets hälsa.
* **Sliding Window (Count-based vs Time-based):** Mätfönstret (t.ex. senaste 100 anropen) som avgör när `failureRateThreshold` (t.ex. 50%) överskrids.
* **`CallNotPermittedException`:** Undantaget som kastas i `OPEN`-läge på mikrosekunder för att spara nätverksresurser.

#### 3. Verklighets-svar (30–60 sek)
> *"En Circuit Breaker skyddar både oss och våra beroenden. I normalläget (CLOSED) mäter den felkvoten över ett glidande fönster. Om felprocenten överskrider tröskelvärdet slår kretsen om till OPEN och nekar alla efterföljande anrop direkt via fail-fast utan nätverks-overhead. Efter en konfigurerad karenstid övergår den till HALF-OPEN för att testa ett fåtal anrop. Om de lyckas återgår kretsen till CLOSED, annars förblir den OPEN. Detta ger det fallerande systemet andrum att återhämta sig."*

**Labb & Kod:** [PaymentController.java](file:///backend/01-spring-resilience/src/main/java/com/vault/resilience/PaymentController.java) (`/fail-fast`) och [ResilientPaymentClient.java](file:///backend/01-spring-resilience/src/main/java/com/vault/resilience/ResilientPaymentClient.java)

---

### Scenario 3: Retry Storms & Exponential Backoff med Jitter

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Om 10 000 klienter omedelbart och samtidigt försöker igen med fasta intervall när en databas eller gateway drabbas av ett mikrosekundsavbrott, genereras en massiv **Retry Storm** (**Thundering Herd Problem**). Den överbelastade tjänsten kraschar på nytt varje gång vågen av retries slår till.

Dessutom riskerar retries på icke-idempotenta metoder (t.ex. `POST /payments`) att debitera kunden flera gånger vid partiella nätverksfel.

#### 2. Keyword Bingo (Facit-lingo)
* **Retry Storm / Thundering Herd:** När tusentals samtidiga klienter gör synkroniserade återförsök och sänker en återhämtande tjänst.
* **Exponential Backoff:** Att vänta progressivt längre mellan varje försök ($t, 2t, 4t, \dots$) för att lätta på trycket.
* **Full Jitter (Slumpmässig variation):** Att sprida ut väntetiden med en slumpfaktor för att bryta synkroniserade anropsvågor.
* **Idempotens:** Kravet att endast återförsöka säkra eller idempotenta anrop (`GET`, `PUT`, eller transaktioner med unik `Idempotency-Key`).
* **Transient vs Permanent Failure:** Att enbart göra retry på tillfälliga nätverksavbrott eller 503 Service Unavailable, aldrig på 400 Bad Request eller 401 Unauthorized.

#### 3. Verklighets-svar (30–60 sek)
> *"Att göra retries rakt av är ett farligt anti-mönster. Om tusentals trådar gör återförsök samtidigt skapas en Retry Storm som sänker systemet permanent. Lösningen är Exponential Backoff med Full Jitter: vi ökar väntetiden exponentiellt och lägger till en slumpmässig fördröjning så att inte alla klienter slår till i samma våg. Dessutom tillämpar vi retries enbart på transienta nätverksfel och strikt på idempotenta operationer för att aldrig riskera dubbeldebitering."*

**Labb & Kod:** [ResilienceConfig.java](file:///backend/01-spring-resilience/src/main/java/com/vault/resilience/ResilienceConfig.java) & [ResilienceVerificationTest.java](file:///backend/01-spring-resilience/src/test/java/com/vault/resilience/ResilienceVerificationTest.java)

---

### Scenario 4: Fallback Degradation & Graceful Degradation

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
När ett anrop fallerar på grund av timeout eller bruten kretsbrytare möts slutanvändaren av ett fult 500 Internal Server Error om undantaget bubblar upp. Hela webbapplikationen uppfattas som kraschad, trots att kärnflödet hade kunnat fungera med en degraderad respons.

#### 2. Keyword Bingo (Facit-lingo)
* **Graceful Degradation (Värdig degradering):** Att systemet fortsätter leverera affärsvärde med reducerad funktionalitet vid partiella fel.
* **Fallback Method:** En alternativ metod som anropas automatiskt när huvudmetoden kastar undantag eller nekas av Circuit Breaker.
* **Asynchronous Enqueueing:** Att spara uppdraget i en lokal kö eller dead-letter-logg för senare asynkron bearbetning (`FALLBACK_QUEUED`).
* **Stale-While-Revalidate / Cached Fallback:** Att servera ett äldre cachat svar istället för ett felmeddelande.

#### 3. Verklighets-svar (30–60 sek)
> *"Graceful Degradation innebär att vi designar systemet för att överleva partiella fel utan att slutanvändaren märker ett totalt haveri. När vår Circuit Breaker eller TimeLimiter löser ut fångas undantaget av en `@Fallback`-metod. Beroende på användningsfall returnerar vi cachad data, ett offline-svar, eller köar transaktionen asynkront för bakgrundsbearbetning istället för att kasta ett 500-fel i ansiktet på kunden."*

**Labb & Kod:** [ResilientPaymentClient.java](file:///backend/01-spring-resilience/src/main/java/com/vault/resilience/ResilientPaymentClient.java) (`FALLBACK_QUEUED`)

---

### Scenario 5: Kubernetes Death Spiral & Management Port-isolering (Port 8081)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I en standard-Spring Boot-applikation delar kundtrafik och Actuator hälso-endpoints samma port (`8080`). Om alla Tomcats 200 trådar blir låsta av en hängande betalningsgateway hamnar Kubelets Liveness Probe i kö bakom de hängande kundförfrågningarna och timear ut (> 3s).

Kubernetes antar felaktigt att Podden är död och dödar den med `SIGKILL`. All trafik flyttas omedelbart över till kvarvarande poddar, som omedelbart drabbas av samma trådtömning och dödas. Hela Kubernetes-klustret hamnar i en ond kraschloop (**The Kubernetes Death Spiral**) med timslång nertid.

#### 2. Keyword Bingo (Facit-lingo)
* **The Kubernetes Death Spiral:** När Liveness Probes misslyckas på grund av trådtömning och skapar en kaskad av pod-omstarter i klustret.
* **Separate Management Port (`management.server.port: 8081`):** Konfigurationen som tilldelar Actuator en helt fristående Tomcat Connector med egen trådpool.
* **Liveness vs Readiness Probes:**
  * **Liveness:** Ska podden startas om? (Får ALDRIG anropa externa databaser/gateways!).
  * **Readiness:** Ska podden ta emot trafik?
* **Security Surface Isolation:** Att förhindra att känsliga endpoints som `/actuator/env` och `/actuator/heapdump` exponeras mot internet via Ingress.

#### 3. Verklighets-svar (30–60 sek)
> *"Att dela port mellan affärstrafik och Actuator är en klassisk orsak till kaskadkrascher i Kubernetes. Om Tomcats trådpool svälts ut på port 8080 kan inte Liveness Probe svara i tid. Kubelet dödar då podden, trafiken väller över till nästa podd, och hela klustret faller i en Death Spiral. Genom att sätta `management.server.port: 8081` får Actuator en egen fristående trådpool. Även om port 8080 är 100% blockerad svarar hälsochecken på under 2 millisekunder. Dessutom isolerar det känsliga endpoints som `/actuator/heapdump` från internet."*

**Labb & Kod:** `src/main/resources/application.yml` (`management.server.port: 8081`) & [demo-thread-starvation.sh](file:///backend/01-spring-resilience/demo-thread-starvation.sh)

---

## 2. Felsökningsguide (Kommandon & Verktyg)

### A. Inspektera tråddump vid tråd-tömning
Hämta tråddump från en körande JVM för att se trådar som fastnat i `WAITING` eller `TIMED_WAITING`:
```bash
# Hitta PID
jcmd

# Ta tråddump och räkna HTTP-worker-trådar i vänteläge
jcmd <PID> Thread.print | grep -A 5 "http-nio-"
```

### B. Metriker via Actuator & Prometheus
Resilience4j exponerar metriker till Prometheus som avslöjar tillståndet:
```bash
curl -s http://localhost:8081/actuator/metrics/resilience4j.circuitbreaker.state
# Kika på state: closed, open, half_open

curl -s http://localhost:8081/actuator/metrics/resilience4j.circuitbreaker.failure.rate
```

---

## 3. Koden & Verifiering

* **The Flaw:** [UnprotectedPaymentClient.java](file:///backend/01-spring-resilience/src/main/java/com/vault/resilience/UnprotectedPaymentClient.java) – Anropar hängande tjänst utan timeouts, konsumerar trådar tills systemet kraschar.
* **The Fix:** [ResilientPaymentClient.java](file:///backend/01-spring-resilience/src/main/java/com/vault/resilience/ResilientPaymentClient.java) – Konfigurerad med CircuitBreaker, TimeLimiter och Fallback.
* **Verification Test:** [ResilienceVerificationTest.java](file:///backend/01-spring-resilience/src/test/java/com/vault/resilience/ResilienceVerificationTest.java) – Bevisar att kretsen öppnas vid fel och returnerar fallback omedelbart.

---

## 4. Så här kör du Spring Boot-applikationen & testerna

### A. Kör enhet- och integrationstesterna
```bash
cd backend/01-spring-resilience
mvn clean test
```

### B. Starta Spring Boot-servern lokalt
Starta applikationen med Spring Boot Maven-pluginet:
```bash
cd backend/01-spring-resilience
mvn spring-boot:run
```
Applikationen startar på:
* **Kundtrafik HTTP:** `http://localhost:8080`
* **Actuator & Prometheus:** `http://localhost:8081`

### C. Testa endpoints & se Circuit Breaker agera i realtid
Öppna en ny terminal eller kör curl:

1. **Normalt lyckat betalningsanrop:**
   ```bash
   curl "http://localhost:8080/api/payment/safe?tx=ORD-100"
   # Svar: SUCCESS: Payment ORD-100 processed
   ```

2. **Simulera fallerande nedströmstjänst (öppnar kretsbrytaren & fail-fast fallback):**
   ```bash
   # Gör 3 snabba anrop för att överskrida tröskeln
   curl "http://localhost:8080/api/payment/fail-fast?tx=TX-1"
   curl "http://localhost:8080/api/payment/fail-fast?tx=TX-2"
   curl "http://localhost:8080/api/payment/fail-fast?tx=TX-3"
   
   # 4:e anropet: Kretsen är OPEN! Svarar direkt med fail-fast fallback
   curl "http://localhost:8080/api/payment/fail-fast?tx=TX-4"
   ```

3. **Inspektera Actuator hälsotillstånd & Circuit Breaker status:**
   ```bash
   # Hälsostatus och probes
   curl -s http://localhost:8081/actuator/health | jq .

   # Circuit Breaker tillstånd (CLOSED / OPEN / HALF_OPEN)
   curl -s http://localhost:8081/actuator/metrics/resilience4j.circuitbreaker.state | jq .

   # Prometheus rådata
   curl -s http://localhost:8081/actuator/prometheus | grep resilience4j
   ```

---

## 5. Matris över alla 5 kombinationer, effekter och utfall

| Endpoint | Typ | Skyddsmekanism | Responskod | Trådeffekt i Tomcat | Utfall & Slutsats |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`/api/payment/safe`** | Normal | Circuit Breaker (CLOSED) | `200 OK` | Frigörs inom ~10 ms | Normaldrift. Svar: `SUCCESS: Payment ORD-100 processed`. |
| **`/api/payment/fail-fast`** | Transient fel | Circuit Breaker + Retry + Fallback | `200 OK` | Frigörs omedelbart (< 1 ms vid OPEN) | Öppnar kretsen vid 3 fel. Därefter *fail-fast* fallback utan nätverksanrop. |
| **`/api/payment/unsafe`** | Snabbt fel *(The Flaw)* | ❌ Inget | `500 Error` | Frigörs snabbt men skräpar ner logg | Kraschar direkt med rå Java-stacktrace (`RuntimeException`). Ingen fallback. |
| **`/api/payment/unsafe-hang`** | Hängande tjänst *(The Flaw)* | ❌ Inget | Hänger i 10s | 🔒 **Låser tråden i 10 sekunder!** | Vid 5 samtidiga anrop inträffar **Thread Pool Starvation**! Port 8080 fryser helt. |
| **`/api/payment/safe-timeout`** | Hängande tjänst *(The Fix)* | ✅ TimeLimiter (1s) + Circuit Breaker | `200 OK` | Frigörs efter max 1000 ms | Klipper anropet efter 1s, sparar Tomcat-tråden och returnerar timeout-fallback. |

---

## 6. Live-demonstrationsskript

Två kompletta automatiserade skript finns färdiga att köra med färgkodade terminalutskrifter:

1. **Kretsbrytarens livscykel (CLOSED -> OPEN -> HALF_OPEN -> CLOSED):**
   ```bash
   cd backend/01-spring-resilience
   ./demo-circuit-breaker.sh
   ```
2. **Thread Pool Starvation & Management Port Isolering:**
   ```bash
   cd backend/01-spring-resilience
   ./demo-thread-starvation.sh
   ```
