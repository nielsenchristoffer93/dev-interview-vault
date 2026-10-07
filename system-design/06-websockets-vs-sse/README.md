# ⚡ System Design: Real-Time Communication (WebSockets vs SSE vs Long-Polling)

> **Intervju-fokus:** Valet mellan duplex och simplex streaming, horisontell klusterskalning (Redis Pub/Sub backplane), AWS ALB idle timeouts (60s) och socket resource leaks.

---

## 🎯 Arkitektur-jämförelse: Snabbmatris

| Egenskap | Server-Sent Events (SSE) | WebSockets (WS) | Long-Polling |
| :--- | :--- | :--- | :--- |
| **Riktning** | Envägs: Server -> Klient (Simplex) | Tvåvägs: Server <-> Klient (Full Duplex) | Envägs per HTTP-request (Simulerad) |
| **Protokoll** | Standard HTTP/1.1 eller HTTP/2 (`text/event-stream`) | Uppgradering från HTTP till WebSocket (`ws://` / `wss://`) | Standard HTTP (`GET`/`POST`) |
| **Återanslutning** | Inbyggt i webbläsaren (`EventSource` med `Last-Event-ID`) | Manuell logik krävs i klienten | Manuell ny request vid timeout/svar |
| **HTTP/2 Multiplexing** | Ja (flera strömmar delar 1 TCP-anslutning) | Nej (varje WS kräver en dedikerad TCP-anslutning) | Ja |
| **Brandväggar & Proxies** | Fungerar sömlöst genom alla standard HTTP-proxies | Kräver explicit stöd för HTTP `Upgrade` header | Fungerar sömlöst |
| **Bästa use-cases** | LLM/AI-tokens, aktiekurser, notiser, live score, dashboards | Chatt, multiplayer-spel, samredigering (Google Docs) | Legacy-system utan stöd för streaming |

---

## 🎯 Scenario 1: SSE vs WebSockets för 1-vägs streaming (AI & Notiser)

### Problem (Vad kraschar / varför bryr vi oss)
Ett team ska bygga streaming av svar från en språkmodell (LLM / ChatGPT-liknande UI) samt realtidsnotiser till webben.
En utvecklare väljer WebSockets eftersom *"realtid betyder alltid WebSockets"*.

**Konsekvenserna i produktion:**
1. **Header & Auth-kaos:** Webbläsarens inbyggda `WebSocket`-API tillåter inte anpassade headers (såsom `Authorization: Bearer <token>`). Teamet tvingas skicka tokens som query-parametrar i URL:en (vilket läcker i serverloggar och CDN-cacher) eller uppfinna ett eget handskakningsprotokoll över socketen.
2. **TCP-utmattning:** På HTTP/1.1 eller HTTP/2 kräver varje WebSocket en hel, dedikerad TCP-socket. När 20 000 användare öppnar sajten tar serverns file descriptors slut.
3. **Nätverksbortfall:** När användaren åker genom en tunnel och tappar täckning kraschar WebSocketen. Eftersom WebSockets saknar inbyggd reconnect måste teamet skriva hundratals rader komplex JavaScript för backoff och synkronisering.

Hade teamet valt **Server-Sent Events (SSE)** hade webbläsaren hanterat reconnect automatiskt, skickat `Last-Event-ID` så servern kan återuppta där den slutade, och delat befintlig HTTP/2-uppkoppling.

---

### Keyword Bingo (Facit-lingo)
* **Server-Sent Events (SSE):** W3C-standard över vanlig HTTP där servern håller en persistent anslutning öppen med MIME-typen `text/event-stream`.
* **Full-Duplex vs Half-Duplex / Simplex:** Full-duplex tillåter samtidig dubbelriktad överföring (WebSockets); simplex är enkelriktat flöde (SSE).
* **`Last-Event-ID` Header:** SSE-specifikationens inbyggda mekanism där klienten automatiskt skickar senast mottagna ID vid återanslutning, vilket möjliggör sömlös replay.
* **HTTP/2 Connection Multiplexing:** Möjligheten i HTTP/2 att köra hundratals logiska SSE-strömmar över en enda delad underliggande TCP-anslutning.
* **Upgrade: websocket:** HTTP 101 Switching Protocols-handskakningen som transformerar en HTTP-anslutning till en rå binär TCP-ström.
* **Firewall / Corporate Proxy Traversal:** Faktumet att strikta företagsbrandväggar ofta blockerar eller klipper okända protokoll och WS-handskakningar, medan SSE passerar som vanlig HTTP.

---

### Verklighets-svar (30–60 sek)
> "En vanlig fälla hos juniora utvecklare är att välja WebSockets så fort ordet 'realtid' nämns. Men om trafiken primärt rör sig från servern till klienten – såsom LLM-tokenstreaming, dashboard-uppdateringar eller pushnotiser – är **Server-Sent Events (SSE)** arkitektoniskt överlägset.
> 
> SSE körs över standard HTTP, drar full nytta av HTTP/2-multiplexing, stöder standard HTTP-headers och cookies, och webbläsarens `EventSource`-API sköter återanslutning och händelse-replay med `Last-Event-ID` helt automatiskt. WebSockets reserverar jag strikt för scenarier som kräver högfrekvent dubbelriktad kommunikation med minimal overhead, exempelvis multiplayer-spel eller samredigering."

---

## 🎯 Scenario 2: The WebSocket Cluster Trap & Redis Pub/Sub Backplane

### Problem (Vad kraschar / varför bryr vi oss)
Ett startup bygger en chattapplikation. I utvecklingsmiljön med en enda instans fungerar allt perfekt.
När trafiken ökar skalar DevOps upp till 5 poddar bakom en AWS Application Load Balancer (ALB).

**Kraschen:**
* Användare Alice ansluter och lastbalanseras till **Pod 1**.
* Användare Bob ansluter och lastbalanseras till **Pod 3**.
* Alice skickar ett meddelande till Bob. Pod 1 letar i sin lokala minneskarta (`Map<UserId, WebSocketSession>`) efter Bobs session, men hittar ingenting!
* **Resultat:** Bobs meddelande tappas helt! Alice ser att meddelandet skickades, men Bob får det aldrig såvida de inte råkar hamna på exakt samma pod.

Teamet försöker först med "Sticky Sessions" (Session Affinity) i lastbalanseraren, men det löser inte problemet eftersom Alice och Bob är två olika klienter med olika sessioner!

---

### Keyword Bingo (Facit-lingo)
* **WebSocket In-Memory Trap:** Att anta att alla uppkopplade klienter finns tillgängliga i den lokala serverns minneskatalog.
* **Cluster Backplane / Message Broker:** Ett distribuerat meddelandelager (vanligtvis **Redis Pub/Sub** eller RabbitMQ) som binder samman alla isolerade serverinstanser.
* **Fan-out via Pub/Sub:** Mönstret där Pod 1 publicerar meddelandet på ett Redis-topic (`chat:room:99`); alla poddar som prenumererar tar emot meddelandet och Pod 3 pushar det vidare till Bobs lokala socket.
* **Sticky Sessions (Session Affinity):** Lastbalanseringsstrategi som dirigerar samma klient till samma server; löser noll problem för inter-klient-kommunikation.
* **Socket.io Redis Adapter / Spring WebSocket Stomp BrokerRelay:** Färdiga biblioteksarkitekturer som automatiskt sköter kluster-fanout över en central broker.

---

### Verklighets-svar (30–60 sek)
> "WebSockets är till sin natur tillståndskänsliga (stateful). Så fort du har mer än en serverinstans uppstår problemet att Klient A och Klient B är anslutna till två olika poddar. Den lokala instansen har ingen aning om vilka sockets som lever på grannpoddarna.
> 
> Lösningen är att frikoppla socket-anslutningen från distributionslogiken genom en **Pub/Sub Backplane**, exempelvis Redis Pub/Sub eller en RabbitMQ topic exchange. När Pod A tar emot ett meddelande från Alice publiceras det till Redis-kanalen. Samtliga poddar prenumererar på kanalen, och Pod B, som håller Bobs socket aktiv, vidarebefordrar meddelandet direkt till honom."

---

## 🎯 Scenario 3: Connection Leaks, Heartbeats & AWS ALB Timeouts

### Problem (Vad kraschar / varför bryr vi oss)
Produktionssystemet kör WebSockets bakom en AWS Application Load Balancer. Plötsligt rapporterar användare att anslutningen "fryser" efter exakt 60 sekunders inaktivitet.
Samtidigt slår serverns minnes- och file descriptor-grafer i taket, och Linux-kärnan kastar:
```text
java.io.IOException: Too many open files
```

**Vad hände?**
1. **ALB Idle Timeout (60s):** AWS ALB har en standard idle timeout på 60 sekunder. Om ingen data skickas klipper lastbalanseraren tyst TCP-anslutningen utan att alltid skicka en `TCP FIN` eller `RST` till servern.
2. **Zombie Connections:** Servern tror fortfarande att klienten är ansluten och håller TCP-socketen och sessionsobjektet i minnet.
3. **OS File Descriptor Leak:** Varje öppen TCP-socket tar en fil-deskriptor (`ulimit -n`). När tusentals döda sockets ackumuleras kan servern till slut inte ens acceptera nya HTTP-anrop eller öppna databasanslutningar!

---

### Keyword Bingo (Facit-lingo)
* **Application-Level Heartbeats (Ping/Pong Frames):** WebSocket-protokollets inbyggda kontrollramar (`0x9 Ping` och `0xA Pong`) som skickas periodiskt (t.ex. var 25:e sekund) för att hålla anslutningen vid liv genom brandväggar och proxys.
* **TCP Keep-Alive vs WebSocket Ping/Pong:** TCP Keep-Alive opererar på OS-nivå och fångas inte alltid av HTTP-reverse-proxies; WebSocket Ping/Pong körs på applikationslagret och garanterar proxy-transparens.
* **ALB / Nginx Idle Timeout:** Timeout-gränser i intermediära nätverkskomponenter som bryter tysta anslutningar.
* **Zombie Socket / Half-Open Connection:** En nätverksanslutning där ena parten kopplat ifrån utan formell handskakning, medan den andra parten fortfarande allokerar minne och resurser.
* **File Descriptor Exhaustion (`ulimit`):** När operativsystemets gräns för maximalt antal öppna filer/sockets per process överskrids.

---

### Verklighets-svar (30–60 sek)
> "Alla molnlastbalanserare som AWS ALB har en strikt idle-timeout, vanligtvis 60 sekunder. Om ingen datatrafik passerar klipper lastbalanseraren förbindelsen. Utan applikationsheartbeats leder detta till 'zombie sockets' där servern håller tusentals döda förbindelser i minnet tills servern kraschar med 'Too many open files'.
> 
> Lösningen är tvådelad: För det första måste servern eller klienten skicka periodiska **Ping/Pong-ramar** (t.ex. var 25:e sekund) vilket nollställer ALBs idle-timer. För det andra konfigureras en strikt **Read/Write Timeout** på servern: om ingen Pong mottagits inom två intervall stängs socketen explicit, resurserna frigörs och minneskartan rensas."

---

### Senior Intervju-djupdykning
* **Varför hjälper inte TCP Keepalive mot AWS ALB idle timeout?**
  TCP Keepalive skickas på L4 (transportlagret) av operativsystemet och har ofta standardintervall på 2 timmar i Linux (`tcp_keepalive_time = 7200`). Dessutom terminerar ALB:n TCP-anslutningen och agerar L7-reverse proxy. Därför måste keepalive ske som L7 WebSocket Ping/Pong på applikationsnivå.
* **Hur hanteras reconnect-stormar (Thundering Herd) när ett WebSocket-kluster startas om?**
  Om 100 000 klienter kopplas ner samtidigt och alla omedelbart försöker återansluta överbelastas autentiseringsservern. Klienten måste implementera **Exponential Backoff med Full Jitter** (`baseDelay * 2^attempt + randomJitter`) för att sprida återanslutningarna jämnt över tid.

---

## 🛠️ Koden & Verifiering

* **The Flaw & The Fix:** [RealtimeCommunicationLab.java](file:///system-design/06-websockets-vs-sse/RealtimeCommunicationLab.java)
  * *Flaw:* `FlawedLocalWebSocketServer` sparar sessioner enbart i lokalt minne (pod-trap där Bob på Pod 2 inte nås av Alice på Pod 1); oövervakade sockets orsakar zombie connection leaks.
  * *Fix:* `DistributedWebSocketServer` använder `PubSubBackplane` (Redis Pub/Sub) för kluster-fanout; `ZombieConnectionDetector` rensar inaktiva sockets som överskrider idle timeout (t.ex. AWS ALB 60s); `SseEvent` formaterar text/event-stream enligt W3C.
* **Verification Test:** [RealtimeCommunicationVerificationTest.java](file:///system-design/06-websockets-vs-sse/RealtimeCommunicationVerificationTest.java)
  * Bevisar att lokala minnesmappar misslyckas i kluster.
  * Bevisar att Pub/Sub Backplane levererar sömlöst mellan poddar.
  * Bevisar att zombiesockets rensas automatiskt efter idle timeout.

---

## 🧪 Så här kör du testerna

```bash
cd system-design/06-websockets-vs-sse
mvn test
```
