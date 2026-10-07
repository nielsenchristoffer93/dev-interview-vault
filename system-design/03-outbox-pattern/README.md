# 📬 outbox-pattern: Transactional Outbox & Debezium CDC

Denna modul täcker 4 fundamentala datakonsistens- och integrationsutmaningar i mikrotjänster och distribuerade händelsestyrda system. Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 1: The Dual-Write Problem & Asynkron Inkonsekvens

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I mikrotjänstarkitekturer måste en tjänst ofta både uppdatera sin egen databas och publicera en händelse till en meddelandemäklare (Kafka/RabbitMQ/EventBridge):
```java
orderRepository.save(order);       // 1. Skriv till SQL Databas
kafkaTemplate.send("orders", event); // 2. Skriv till Kafka
```

**Detta kallas "The Dual-Write Problem" och är dödsdömt i distribuerade system:**
* Om Kafka har ett tillfälligt avbrott eller nätverket klipper kastar rad 2 ett undantag: Ordern är committad i databasen, men lagret eller betaltjänsten får aldrig reda på det.
* Om applikationsservern kraschar eller drabbas av OOM precis mellan rad 1 och rad 2 uppstår permanent och tyst datadesynk.
* Att försöka lösa detta med distribuerade transaktioner (2PC / Two-Phase Commit / XA) är ett välkänt anti-mönster som skapar extrema prestandaförluster och sköra flaskhalsar.

#### 2. Keyword Bingo (Facit-lingo)
* **The Dual-Write Problem:** Omöjligheten att garantera atomära skrivningar över två oberoende distribuerade system utan distribuerad konsensus.
* **Silent Data Desynchronization:** När databas och meddelandemäklare hamnar i otakt utan att fel loggas.
* **Two-Phase Commit (2PC / XA):** Det blockerande anti-mönstret för distribuerade transaktioner som vi undviker i moderna mikrotjänster.
* **Eventual Consistency:** Att acceptera att systemet blir konsistent asynkront över tid istället för genom blockerande lås.

#### 3. Verklighets-svar (30–60 sek)
> *"The Dual-Write Problem uppstår när en tjänst försöker skriva till en databas och skicka till Kafka i samma kodblock. Om applikationen kraschar däremellan, eller om Kafka är nere, har vi en sparad order men inget skickat event – systemet är permanent korrumperat. Att använda 2PC-transaktioner är för långsamt och stöds inte av moderna meddelandeköer. Lösningen är Transactional Outbox Pattern, där vi garanterar att eventet sparas i samma lokala databastransaktion som affärsdatan."*

**Labb & Kod:** [FlawedDualWriteService.java](file:///system-design/outbox-pattern/FlawedDualWriteService.java) vs [TransactionalOutboxService.java](file:///system-design/outbox-pattern/TransactionalOutboxService.java)

---

### Scenario 2: Transactional Outbox Pattern & Lokal ACID-atomicitet

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Hur säkerställer vi att ett meddelande garanterat publiceras om och endast om databastransaktionen faktiskt committas, utan att blanda in nätverksanrop mot Kafka i transaktionsblocket?

#### 2. Keyword Bingo (Facit-lingo)
* **Transactional Outbox Pattern:** Mönstret där applikationen sparar affärsentiteten och ett händelsemeddelande (i en `outbox_messages`-tabell) i **samma lokala ACID-transaktion**.
* **ACID Atomicity:** Egenskapen att antingen sparas både ordern och outbox-posten, eller rullas båda tillbaka vid fel.
* **Zero Network Calls in DB Transaction:** Regeln att aldrig göra nätverks- eller Kafka-anrop inuti ett `@Transactional`-block.
* **Payload JSON Serialization:** Att spara meddelandets kompletta payload som JSONB i outbox-tabellen redo för publicering.

#### 3. Verklighets-svar (30–60 sek)
> *"Transactional Outbox Pattern utnyttjar den lokala relationsdatabasens ACID-garantier. Istället för att anropa Kafka direkt i affärskoden skapar vi en `outbox_messages`-tabell i samma databas. När vi sparar en Order infogar vi samtidigt ett händelsemeddelande i outbox-tabellen i samma transaktion. Om transaktionen lyckas är meddelandet garanterat sparat. Om transaktionen rullas tillbaka rullas även meddelandet tillbaka. Nätverksrisken med Kafka är helt bortkopplad från vår databasskrivning."*

**Labb & Kod:** [TransactionalOutboxService.java](file:///system-design/outbox-pattern/TransactionalOutboxService.java) & [OutboxVerificationTest.java](file:///system-design/outbox-pattern/OutboxVerificationTest.java)

---

### Scenario 3: Change Data Capture (CDC) med Debezium & PostgreSQL WAL

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
När miljontals rader skrivs till `outbox_messages` kan en polling-tråd som kör `SELECT` var tionde millisekund överbelasta databasens CPU, skapa indexfragmentering och dränera anslutningar.

Hur strömmar vi händelser från outbox-tabellen till Kafka i realtid med mikrosekunders latens utan att belasta databasmotorn?

#### 2. Keyword Bingo (Facit-lingo)
* **Change Data Capture (CDC):** Teknik för att avlyssna och strömma förändringar direkt från databasens interna transaktionslogg.
* **PostgreSQL Write-Ahead Log (WAL):** Den binära transaktionslogg där Postgres sekventiellt skriver alla commits innan de skrivs till diskblock.
* **Debezium:** Det branschledande open-source-verktyget för CDC byggt ovanpå Kafka Connect.
* **Logical Decoding / Replication Slots:** Mekanismen i PostgreSQL som låter externa verktyg läsa WAL-strömmen utan att missa commits.
* **Low Latency Streaming:** Händelser når Kafka inom några millisekunder efter att SQL-transaktionen committats.

#### 3. Verklighets-svar (30–60 sek)
> *"I produktionssystem med höga volymer pollar vi inte outbox-tabellen med vanliga SQL-queries eftersom det sliter på databasen. Istället använder vi Change Data Capture (CDC) med Debezium. Debezium kopplar upp sig mot PostgreSQL:s interna Write-Ahead Log (WAL) via en Logical Replication Slot. Så fort en rad committas till outbox-tabellen läser Debezium WAL-strömmen asynkront och publicerar händelsen direkt till Kafka med mikrosekunders latens och minimal CPU-belastning på databasen."*

**Labb & Kod:** Avsnitt 2A nedan

---

### Scenario 4: Polled Outbox Worker med `SKIP LOCKED`

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I system där Debezium och Kafka Connect inte är uppsatt behöver man en enklare applikationsbaserad lösning. Men om flera applikationsinstanser kör `SELECT * FROM outbox WHERE status = 'PENDING'` samtidigt uppstår låskonflikter och race conditions där flera instanser försöker skicka samma meddelande.

#### 2. Keyword Bingo (Facit-lingo)
* **Polled Outbox Worker:** En schemalagd bakgrundstråd (`@Scheduled`) som regelbundet läser och skickar osända poster.
* **`SELECT ... FOR UPDATE SKIP LOCKED`:** Den kritiska SQL-satsen som tillåter flera parallella noder att plocka olika rader utan att blockera varandra eller skapa deadlocks.
* **Batch Fetching:** Att bearbeta outbox-poster i hanterbara batcher (t.ex. `LIMIT 50`).
* **At-Least-Once Delivery Guarantee:** Medvetenheten om att konsumenten kan få dubbletter vid omstarter och därför måste vara idempotent.

#### 3. Verklighets-svar (30–60 sek)
> *"För mindre system där Debezium är för tungt bygger vi en Polled Outbox Worker i applikationen. För att kunna köra flera noder parallellt utan låskonflikter använder vi `SELECT * FROM outbox WHERE status = 'PENDING' LIMIT 50 FOR UPDATE SKIP LOCKED`. Nyckelordet `SKIP LOCKED` gör att varje nod plockar sin egen batch av rader utan att blockera de andra noderna. När meddelandet skickats till mäklaren uppdateras statusen till `SENT` eller raderas ur tabellen."*

**Labb & Kod:** [TransactionalOutboxService.java](file:///system-design/03-outbox-pattern/TransactionalOutboxService.java)

---

## 2. Felsökningsguide (Kommandon & Verktyg)

### A. Inspektera PostgreSQL Write-Ahead Log (WAL) och Replication Slots
```bash
# Anslut till Postgres
psql -h localhost -U vault_user -d interview_db

# Visa aktiva CDC replication slots
SELECT slot_name, plugin, active FROM pg_replication_slots;

# Kontrollera osparade eller fastnade meddelanden i outbox-tabellen
SELECT id, aggregate_type, event_type, created_at FROM outbox_messages ORDER BY created_at DESC LIMIT 10;
```

---

## 3. Koden & Verifiering

* **The Flaw:** [FlawedDualWriteService.java](file:///system-design/03-outbox-pattern/FlawedDualWriteService.java) – Utför oberoende skrivningar till DB och broker; lämnar föräldralös order vid broker-fel.
* **The Fix:** [TransactionalOutboxService.java](file:///system-design/03-outbox-pattern/TransactionalOutboxService.java) – Garanterar atomär ACID-commit av både affärsentitet och outbox-meddelande.
* **Verification Test:** [OutboxVerificationTest.java](file:///system-design/03-outbox-pattern/OutboxVerificationTest.java) – Bevisar att vid fel rullas både entitet och outbox-post tillbaka atomärt, vilket eliminerar spökordrar och desynk.

---

## 4. Så här kör du testerna

```bash
cd system-design/03-outbox-pattern
mvn test
```
