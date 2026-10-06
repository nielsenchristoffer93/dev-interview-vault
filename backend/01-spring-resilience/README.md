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
