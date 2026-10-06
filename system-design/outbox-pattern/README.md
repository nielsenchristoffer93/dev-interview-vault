# 📬 outbox-pattern: Transactional Outbox & Debezium CDC

## 1. Problem: Vad kraschar eller varför bryr vi oss?
I distribuerade system och mikrotjänster måste en tjänst ofta både spara data i en databas och skicka ett event till en meddelandemäklare (Kafka/RabbitMQ/EventBridge).

Den naiva implementationen utför två separata I/O-skrivningar:
```java
orderRepository.save(order);       // 1. Skriv till SQL Databas
kafkaTemplate.send("orders", event); // 2. Skriv till Kafka
```
**Detta kallas "The Dual-Write Problem":**
* Om Kafka är tillfälligt nere eller nätverket klipper kastar steg 2 ett exception: Ordern finns i databasen, men lagret eller betaltjänsten får aldrig reda på det.
* Om applikationen kraschar precis mellan rad 1 och rad 2 hamnar systemet i ett permanent trasigt, desynkroniserat tillstånd.
* Att försöka lösa detta med distribuerade transaktioner (2PC / Two-Phase Commit / XA) är ett välkänt anti-mönster i mikrotjänster som skapar extrema prestandaförluster och sköra flaskhalsar.

---

## 2. Keyword Bingo (Facit-lingo)
* **The Dual-Write Problem:** Omöjligheten att atomärt garantera framgångsrika skrivningar till två oberoende distribuerade system utan distribuerad konsensus.
* **Transactional Outbox Pattern:** Mönstret där applikationen sparar affärsdatan (t.ex. `Order`) och ett händelsemeddelande (i en `outbox`-tabell) i **samma lokala ACID-databastransaktion**.
* **Change Data Capture (CDC):** En teknik för att läsa transaktionsloggar (PostgreSQL Write-Ahead Log / WAL) direkt från databasen utan att belasta applikationen.
* **Debezium & Kafka Connect:** Den branschledande lösningen för CDC som avlyssnar databasens WAL och strömmar händelser från `outbox`-tabellen direkt till Kafka topics med garanterad ordning.
* **Polled Outbox Worker:** En enklare mekanism där en schemalagd bakgrundstråd regelbundet pollar `SELECT * FROM outbox WHERE status = 'PENDING' FOR UPDATE SKIP LOCKED`.
* **Eventual Consistency (Slutlig samstämmighet):** Systemet blir konsistent asynkront utan blockerande globala lås.

---

## 3. Verklighets-svar (30–60 sek)
> *"Det klassiska Dual-Write-problemet uppstår när vi försöker skriva till en databas och publicera till Kafka i samma kodblock. Kraschar processen däremellan får vi permanent datainkonsekvens. Istället för att införa sköra och långsamma 2PC-transaktioner implementerar vi Transactional Outbox Pattern. Vi skapar en dedikerad `outbox`-tabell i databasen. När en order sparas skrivs både ordern och utgående meddelande till outbox-tabellen i en och samma lokala ACID-transaktion. Sedan låter vi ett Change Data Capture-verktyg som Debezium läsa databasens Write-Ahead Log (WAL) och asynkront strömma meddelandena till Kafka med 'at-least-once'-garanti."*

---

## 4. Felsökningsguide (Kommandon & Verktyg)

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

## 5. Koden & Verifiering

* **The Flaw:** [FlawedDualWriteService.java](file:///system-design/outbox-pattern/FlawedDualWriteService.java) – Utför oberoende skrivningar till DB och broker; lämnar föräldralös order vid broker-fel.
* **The Fix:** [TransactionalOutboxService.java](file:///system-design/outbox-pattern/TransactionalOutboxService.java) – Garanterar atomär ACID-commit av både affärsentitet och outbox-meddelande.
* **Verification Test:** [OutboxVerificationTest.java](file:///system-design/outbox-pattern/OutboxVerificationTest.java) – Bevisar att vid fel rullas både entitet och outbox-post tillbaka atomärt, vilket eliminerar spökordrar och desynk.
