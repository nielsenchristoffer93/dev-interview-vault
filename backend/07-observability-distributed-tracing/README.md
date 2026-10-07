# 🔭 Backend: Distributed Tracing & Observability (OpenTelemetry & Prometheus)

> **Intervju-fokus:** W3C Trace Context (`traceparent`) propagation, High-Cardinality Prometheus OOM Trap (RED vs USE method), och MDC (Mapped Diagnostic Context) trådläckor över async/Virtual Threads.

---

## 🎯 Scenario 1: W3C Trace Context (`traceparent`) & Cross-Service Correlation

### Problem (Vad kraschar / varför bryr vi oss)
En användare i React klickar på "Slutför köp". Laddningssnurran snurrar i **4,5 sekunder** innan sidan svarar. Kunden är frustrerad.
DevOps och backend-utvecklarna undersöker loggarna i Datadog/Kibana:
* Frontend-teamet säger: *"Vårt anrop till `/api/checkout` tog 4,5 sekunder i nätverksfliken."*
* API Gateway-teamet säger: *"Vi tog emot anropet och vidarebefordrade till OrderService. Vår interna tid var 12 ms."*
* OrderService-teamet säger: *"Vi skickade ett event till Kafka och returnerade 200 OK på 85 ms."*
* PaymentService-teamet säger: *"Vi drog pengarna på 40 ms."*

Alla tjänster svär på att deras kod är blixtsnabb. **Var försvann de resterande 4,3 sekunderna?**
Eftersom varje tjänst genererade sina egna isolerade Correlation IDs eller saknade distribuerad kontext finns det inget sätt att knyta ihop förfrågans livscykel. Ingen såg att meddelandet låg i en **Kafka Consumer Lag-kö i 4,2 sekunder** innan PaymentService ens började läsa det!

---

### Keyword Bingo (Facit-lingo)
* **W3C Trace Context:** Den officiella standardiserade HTTP-headern (`traceparent`) som möjliggör universell distribuerad spårning mellan olika leverantörer och ramverk.
* **`traceparent` Header Format:** En 4-delad sträng: `version-trace_id-parent_id-trace_flags` (t.ex. `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`).
* **Trace vs Span:**
  * *Trace:* Hela resan för en förfrågan från start till mål genom hela distribuerade systemet.
  * *Span:* En enskild tidsmätt operation inom en tjänst (t.ex. ett DB-anrop, en HTTP-klientförfrågan eller en serialisering).
* **Context Propagation (Baggage & Carriers):** Processen att injicera spårningsmetadata i utgående HTTP-headers, gRPC-metadata eller Kafka-record-headers och extrahera dem hos mottagaren.
* **OpenTelemetry (OTel):** Det öppna industristandardiserade ramverket för insamling av traces, metrics och logs.
* **Distributed Waterfall Timeline:** Den visuella tidslinjen i Jaeger, Tempo eller Zipkin som omedelbart avslöjar exakt var i kedjan tiden förbrukades.

---

### Verklighets-svar (30–60 sek)
> "Att felsöka distribuerade mikrotjänster med enbart lokala loggfiler är som att leta i blindo. När en användare upplever 4 sekunders fördröjning men varje tjänst loggar 'sub-100ms' ligger fördröjningen nästan alltid i nätverksköer, Kafka consumer lag eller asynkrona thread pools.
> 
> Som senior implementerar jag **W3C Trace Context (`traceparent`) via OpenTelemetry**:
> Frontend injicerar `traceparent`-headern i sitt `fetch()`-anrop. Spring Boot extraherar den i ett filter, skapar barnspans för databasfrågor, och propagerar headern vidare i Kafka-meddelandenas metadata. När vi granskar anropet i Jaeger eller Tempo ser vi en enda sammanhängande vattenfalls-tidslinje som omedelbart pekar ut exakt vilken tjänst eller kö som orsakade fördröjningen."

---

## 🎯 Scenario 2: Prometheus High-Cardinality Trap & RED vs USE Method

### Problem (Vad kraschar / varför bryr vi oss)
En utvecklare vill övervaka API-anrop i Spring Boot med Micrometer och Prometheus. För att kunna felsöka specifika användare och beställningar lägger utvecklaren till:
```java
// KATASTROFAL KOD:
meterRegistry.counter("http.orders.processed", 
    "userId", order.getUserId(),       // 1 miljon unika användare!
    "orderId", order.getOrderId()      // 10 miljoner unika ordrar!
).increment();
```

**Kraschen:**
* Prometheus fungerar genom tidsserier som indexeras i minnet (TSDB). Varje unik kombination av mätvärdesnamn och label-dimension skapar en **helt ny tidsserie**.
* Inom ett par timmar har Prometheus skapat **10 000 000 unika tidsserier** i minnet.
* Prometheus-serverns minne exploderar från 2 GB till 64 GB, noden kastar `OutOfMemoryKilled`, alla dashboards slutar fungera och larmcentralen (Alertmanager) tystnar under pågående produktionstopp!

---

### Keyword Bingo (Facit-lingo)
* **High Cardinality (Hög kardinalitet):** När en mätvärdes-label har ett mycket stort eller oändligt antal unika värden (såsom användar-ID, order-ID, UUID eller e-post).
* **Time Series Database (TSDB) Explosion:** Katastrofen när för många unika tidsserier överbelastar minnesindexet och disken i Prometheus eller InfluxDB.
* **RED Method (Mikrotjänster):**
  * *Rate:* Antal förfrågningar per sekund (`http_requests_total`).
  * *Errors:* Antal misslyckade förfrågningar (`http_requests_total{status=~"5.."}`).
  * *Duration:* Latens för förfrågningarna (Histogram buckets).
* **USE Method (Infrastruktur):**
  * *Utilization:* Hur upptagen resursen är i % (CPU, Disk).
  * *Saturation:* Hur mycket arbete som står i kö (Run queue, Connection pool queue).
  * *Errors:* Maskinvaru- eller nätverksfel.
* **Rule of Metric Labels:** Metrics är till för aggregerade trender med låg kardinalitet (`method=POST`, `status=200`, `route=/api/orders`); detaljerad entitetsdata (`userId`, `orderId`) hör **uteslutande hemma i Spans och Loggar**.

---

### Verklighets-svar (30–60 sek)
> "Att lägga till entitets-ID:n som `userId` eller `orderId` som labels i Prometheus är ett klassiskt misstag som leder till en 'High Cardinality TSDB Explosion' och sänker övervakningsklustret med OOM. Varje unikt ID skapar en ny tidsserie i minnet.
> 
> Som senior skiljer jag strikt på tre pelare av observerbarhet:
> 1. **Metrics (Prometheus):** Använder enbart låg kardinalitet enligt **RED-metoden** (Rate, Errors, Duration) med fasta labels såsom HTTP-metod, statuskod och parametriserad route (`/api/orders/{id}`).
> 2. **Traces & Logs (OTel/Elasticsearch):** Här – och enbart här – bifogar vi högkardinalitetsdata som `orderId` och `userId` som spårningsattribut eller strukturerade loggfält där de kan sökas utan att skapa miljontals tidsserier."

---

## 🎯 Scenario 3: MDC (Mapped Diagnostic Context) Leaks & Async / Virtual Threads

### Problem (Vad kraschar / varför bryr vi oss)
Ett team vill ha spårbarhet i loggarna. I ett Spring Web Filter lägger de till:
```java
MDC.put("traceId", request.getHeader("X-Trace-Id"));
MDC.put("tenantId", request.getHeader("X-Tenant-Id"));
```
Systemet körs på en traditionell Tomcat-trådpool (eller med `CompletableFuture` och Virtual Threads).

**Katastrofen i produktion:**
1. **Läckage mellan kunder:** Tomcat återanvänder trådar. När Request 1 från "Kund A" är klar glömmer filtret att anropa `MDC.clear()`. När Request 2 från "Kund B" anländer och tilldelas samma tråd loggas "Kund B":s känsliga bankhändelser med `tenantId=Kund A`! Säkerhetsrevisionen kastar rött kort för personuppgiftsläckage (GDPR).
2. **Kontextförlust över asynkrona gränser:** När koden anropar `CompletableFuture.supplyAsync(() -> orderService.process())` körs logiken på en annan arbetstråd. Tråd B har en tom `MDC`, varpå all `traceId` försvinner mitt i anropskedjan!

---

### Keyword Bingo (Facit-lingo)
* **MDC (Mapped Diagnostic Context):** SLF4J/Logbacks mekanism för att binda kontextuell metadata (t.ex. `traceId`, `userId`, `tenantId`) till den aktuella tråden för automatisk inkludering i alla loggrader.
* **ThreadLocal Leak:** När värden i en tråds lokala lagring inte rensas i en återanvänd trådpool, vilket leder till att nästa request ärver föregående användares data.
* **`try-finally { MDC.clear(); }` Pattern:** Det obligatoriska mönstret att alltid rensa MDC i en `finally`-block i slutet av varje HTTP- eller bakgrundsförfrågan.
* **TaskDecorator / Context Propagating Executor:** En dekoratör i Spring (`TaskDecorator`) som fångar upp MDC från föräldratråden och kopierar över den till barntråden innan en asynkron uppgift exekveras.
* **Scoped Values (Java 21 JEP 429):** Den moderna ersättaren till `ThreadLocal` i Java 21 som designats för Virtual Threads; immutabla, strikt scoped till en kodblocksexekvering och omöjliga att läcka.

---

### Verklighets-svar (30–60 sek)
> "MDC i SLF4J bygger på `ThreadLocal`. I applikationsservrar som Tomcat, där trådar återanvänds i en pool, innebär utebliven rensning att 'Kund B' ärver 'Kund A':s spårnings-ID och tenant-kontext – vilket är en allvarlig säkerhetsrisk. Dessutom försvinner kontexten så fort man korsar asynkrona trådgränser med `CompletableFuture`.
> 
> Som senior säkrar jag detta genom två obligatoriska åtgärder:
> 1. Alltid kapsla in HTTP-filtret i ett strikt `try-finally`-block som anropar `MDC.clear()`.
> 2. Konfigurera en Spring **`TaskDecorator`** som automatiskt kopierar `MDC.getCopyOfContextMap()` från modertråden till arbetstråden i async-pooler. I Java 21 migrerar vi mot **Scoped Values** som garanterar immutabilitet och noll minnesläckor."

---

## 🛠️ Koden & Verifiering

* **The Flaw & The Fix:** [ObservabilityLab.java](file:///backend/07-observability-distributed-tracing/ObservabilityLab.java)
  * *Flaw:* Asynkrona arbetstrådar tappar `MDC` kontext och oskyddade meters tillåter high-cardinality tags (`userId`).
  * *Fix:* `TraceContext.createChildSpan()` propagerar traceparent deterministiskt, `MetricRegistryGuard` blockerar kardinalitetsexplosioner och `MdcPropagatingTaskDecorator` propagerar kontext över trådgränser och rensar i `finally`.
* **Verification Test:** [ObservabilityVerificationTest.java](file:///backend/07-observability-distributed-tracing/ObservabilityVerificationTest.java)
  * Testar W3C parsing och barn-span skapande.
  * Testar att metriskardinalitetsregler förhindrar Prometheus OOM.
  * Testar att MDC flyttas till asynkrona trådar och rensas rent utan trådläckor.

---

## 🧪 Så här kör du testerna

```bash
cd backend/07-observability-distributed-tracing
mvn test
```
