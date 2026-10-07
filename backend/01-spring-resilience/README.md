# 🛡️ 01-spring-resilience: Circuit Breakers, Retries & Timeouts

## 1. Problem: Vad kraschar eller varför bryr vi oss?
Ett anrop mot en extern betalnings-gateway hänger eller svarar med hög latens (>30s). Eftersom klienten saknar strikta timeouts och skyddsmekanismer låser varje inkommande HTTP-förfrågan en Tomcat-worker-tråd. Inom loppet av sekunder är Tomcats trådpool (`max-threads: 200`) helt tömd (**Thread Pool Starvation**). 

Hela mikrotjänsten slutar svara på hälsoanrop (`/actuator/health`), Kubernetes liveness probe misslyckas och Podden startas om i en ond cirkel (**Cascading Failure**).

---

## 2. Keyword Bingo (Facit-lingo)
* **Cascading Failure (Kaskadfel):** När ett fel i en nedströmstjänst drar med sig uppströmstjänster i en dominoeffekt.
* **Thread Pool Starvation:** När alla trådar i en exekveringspool blockeras av hängande anrop och inga nya requests kan hanteras.
* **Circuit Breaker States:**
  * `CLOSED`: Trafik släpps igenom normalt. Felkvot mäts.
  * `OPEN`: Kretsen är bruten. Alla anrop nekas omedelbart (`CallNotPermittedException`) utan nätverks-overhead (**Fail-fast**).
  * `HALF-OPEN`: Efter en `waitDurationInOpenState` släpps ett begränsat antal prov-anrop igenom för att testa om beroendet återhämtat sig.
* **Exponential Backoff & Jitter:** Fördröjning mellan retries som växer exponentiellt och tillför slumpmässig variation (jitter) för att undvika **Thundering Herd Problem** / Retry Storms mot en redan ansträngd databas/tjänst.
* **Fallback Degradation:** Tillhandahålla en degraderad respons (t.ex. läsa från cache, svara med offline-meddelande) istället för 500 Internal Server Error.

---

## 3. Verklighets-svar (30–60 sek)
> *"Om vi anropar en extern tjänst utan timeout och circuit breaker riskerar vi att sänka hela vår egen applikation. Om tredjepartstjänsten hänger blir våra trådar hängande, vilket leder till thread starvation i webbservern. Lösningen är att alltid sätta en strikt TimeLimiter som avbryter långsamma anrop, kapsla in anropet i en Circuit Breaker som bryter kretsen så fort felkvoten överskrider ett tröskelvärde, och kombinera med Retry med exponential backoff och jitter enbart på idempotenta och transienta felkoder."*

---

## 4. Felsökningsguide (Kommandon & Verktyg)

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

## 5. Koden & Verifiering

* **The Flaw:** [UnprotectedPaymentClient.java](file:///backend/01-spring-resilience/src/main/java/com/vault/resilience/UnprotectedPaymentClient.java) – Anropar hängande tjänst utan timeouts, konsumerar trådar tills systemet kraschar.
* **The Fix:** [ResilientPaymentClient.java](file:///backend/01-spring-resilience/src/main/java/com/vault/resilience/ResilientPaymentClient.java) – Konfigurerad med CircuitBreaker, TimeLimiter och Fallback.
* **Verification Test:** [ResilienceVerificationTest.java](file:///backend/01-spring-resilience/src/test/java/com/vault/resilience/ResilienceVerificationTest.java) – Bevisar att kretsen öppnas vid fel och returnerar fallback omedelbart.

---

## 6. Så här kör du Spring Boot-applikationen & testerna (WSL / Linux)

### A. Kör enhet- och integrationstesterna
Från modulkatalogen i WSL:
```bash
cd backend/01-spring-resilience
mvn clean test
```
Eller direkt från Windows PowerShell (via WSL):
```powershell
wsl bash -l -c "cd /mnt/c/Users/s103228/.repositories/dev-interview-vault/backend/01-spring-resilience && mvn test"
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

### D. Kör det automatiserade live-demoskriptet
Ett färdigt interaktivt bash-skript som automatiskt kör hela livscykeln (CLOSED -> OPEN -> Fail-Fast mätning -> Vänta 5s -> HALF_OPEN -> Återhämtning till CLOSED) med färgkodade utskrifter:
```bash
# Inifrån WSL:
cd backend/01-spring-resilience
./demo-circuit-breaker.sh

# Eller direkt från Windows PowerShell:
wsl bash -l -c "cd /mnt/c/Users/s103228/.repositories/dev-interview-vault/backend/01-spring-resilience && ./demo-circuit-breaker.sh"
```

---

---

## 7. Matris över alla 5 kombinationer, effekter och utfall

I modulen kan du reproducera och demonstrera fem distinkta fel- och skyddsmönster:

| Endpoint | Typ | Skyddsmekanism | Responskod | Trådeffekt i Tomcat | Utfall & Slutsats |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`/api/payment/safe`** | Normal | Circuit Breaker (CLOSED) | `200 OK` | Frigörs inom ~10 ms | Normaldrift. Svar: `SUCCESS: Payment ORD-100 processed`. |
| **`/api/payment/fail-fast`** | Transient fel | Circuit Breaker + Retry + Fallback | `200 OK` | Frigörs omedelbart (< 1 ms vid OPEN) | Öppnar kretsen vid 3 fel. Därefter *fail-fast* fallback utan nätverksanrop. |
| **`/api/payment/unsafe`** | Snabbt fel *(The Flaw)* | ❌ Inget | `500 Error` | Frigörs snabbt men skräpar ner logg | Kraschar direkt med rå Java-stacktrace (`RuntimeException`). Ingen fallback. |
| **`/api/payment/unsafe-hang`** | Hängande tjänst *(The Flaw)* | ❌ Inget | Hänger i 10s | 🔒 **Låser tråden i 10 sekunder!** | Vid 5 samtidiga anrop inträffar **Thread Pool Starvation**! Port 8080 fryser helt. |
| **`/api/payment/safe-timeout`** | Hängande tjänst *(The Fix)* | ✅ TimeLimiter (1s) + Circuit Breaker | `200 OK` | Frigörs efter max 1000 ms | Klipper anropet efter 1s, sparar Tomcat-tråden och returnerar timeout-fallback. |

---

## 8. Så här testar du de olika utfallen manuellt & automatiserat

### Test 1: Snabbt fel utan skydd (`/unsafe`) vs med skydd (`/fail-fast`)
```bash
# 1. Oskyddat: Ger 500 Internal Server Error med fel-stacktrace
curl -i "http://localhost:8080/api/payment/unsafe?tx=RAW-1"

# 2. Skyddat: Ger 200 OK med strukturerad fallback
curl -i "http://localhost:8080/api/payment/fail-fast?tx=FAIL-1"
```

---

### Test 2: Framkalla Thread Pool Starvation med 5 anrop (`/unsafe-hang`)
I `application.yml` är Tomcats max-trådar satta till **5** (`server.tomcat.threads.max: 5`):

1. **Skicka 5 samtidiga hängande anrop i bakgrunden:**
   ```bash
   for i in {1..5}; do curl -s "http://localhost:8080/api/payment/unsafe-hang?tx=HANG-${i}" & done
   ```
2. **Försök genast göra ett 6:e anrop på port 8080 (Kundtrafik):**
   ```bash
   curl -i --max-time 3 "http://localhost:8080/api/payment/safe?tx=TEST-6"
   # UTFALL: TIMEOUT! Alla Tomcats 5 worker-trådar är låsta i vänteläge.
   ```
3. **Anropa Actuator på port 8081 (Separat Management Port):**
   ```bash
   curl -i "http://localhost:8081/actuator/health"
   # UTFALL: SVARAR DIREKT (200 UP)!
   ```
   > **Intervju-poäng:** Detta bevisar varför Actuator *måste* ligga på en separat management-port (`8081`). Hade Actuator legat på port 8080 hade Kubernetes Liveness Probe timeat ut och startat om podden i en ond **Death Spiral**.

---

### Test 3: Lösningen på Thread Starvation – Timeout-skydd (`/safe-timeout`)
```bash
# Den underliggande tjänsten sover i 10 sekunder, men TimeLimiter klipper efter 1000 ms:
curl -i "http://localhost:8080/api/payment/safe-timeout?tx=PROT-1"
# UTFALL: Svarar efter exakt 1 sekund med FALLBACK_QUEUED och frigör tråden!
```

---

### 🤖 Färdiga automatiserade demonstrationsskript

Två kompletta skript finns färdiga att köra med färger och förklaringar:

1. **Kretsbrytarens livscykel (CLOSED -> OPEN -> HALF_OPEN -> CLOSED):**
   ```bash
   ./demo-circuit-breaker.sh
   ```
2. **Thread Pool Starvation & Management Port Isolering:**
   ```bash
   ./demo-thread-starvation.sh
   ```




