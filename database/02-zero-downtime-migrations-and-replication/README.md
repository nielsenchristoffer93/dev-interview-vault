# 🗄️ Database: Zero-Downtime Migrations & Replication Lag

> **Intervju-fokus:** Expand/Contract (Parallel Run) migrationsmönster, Read-Replicas & Replication Lag (Read-Your-Own-Writes konsistens), samt Keyset (Cursor) Pagination vs Offset/Limit.

---

## 🎯 Scenario 1: Expand & Contract Pattern (Zero-Downtime Schema Evolution)

### Problem (Vad kraschar / varför bryr vi oss)
En order-tabell i produktion har 50 miljoner rader. Teamet vill byta namn på en kolumn från `customer_phone` till `mobile_number` eller ändra datatyp från `VARCHAR(20)` till ett strukturerat JSONB-format.
En junior utvecklare skriver en Flyway/Liquibase-migrering:
```sql
ALTER TABLE orders RENAME COLUMN customer_phone TO mobile_number;
```
Driftsättningen rullas ut med en standard Kubernetes Rolling Update.

**Katastrofen:**
1. Under de 5–10 minuter som utrullningen pågår körs *både* den gamla versionen av poddarna (v1) och den nya (v2) samtidigt.
2. Så fort SQL-migreringen körs kraschar v1 omedelbart med:
   ```text
   org.postgresql.util.PSQLException: ERROR: column "customer_phone" of relation "orders" does not exist
   ```
3. Om man rullar tillbaka driftsättningen kraschar istället v2! Systemet är fast i ett låst läge med massiva 500-fel för kunderna.

---

### Keyword Bingo (Facit-lingo)
* **Expand & Contract Pattern (Parallel Run):** En flerstegsstrategi för databasmigrering där datamodellen först utökas för att stödja både gammal och ny kod, och dras ihop först efter full driftsättning.
* **Dual-Write Phase:** Fasen där applikationen skriver till *både* det gamla och det nya fältet samtidigt så att båda versionerna av tjänsten har giltig data.
* **Batched Backfill (Chunky Updates):** Att migrera historiska rader i mindre transaktionsbatcher (t.ex. 1 000 rader i taget med `WHERE id BETWEEN :start AND :end`) för att undvika långvariga tabellås och replikeringsköer.
* **Backward & Forward Compatibility:** Kravet att applikationskod alltid måste kunna köra mot databas-schemat N-1 och N+1 under en rullande uppdatering.
* **View / Trigger Bridge:** Ett alternativt mönster där en databas-trigger eller updatable view synkroniserar fälten i databasen under övergången utan att belasta applikationskoden.

---

### Verklighets-svar (30–60 sek)
> "Att döpa om en kolumn eller ändra en typ med ett enda `ALTER TABLE`-kommando är omöjligt utan driftstopp i en miljö med rullande driftsättningar, eftersom v1 och v2 av applikationen körs parallellt under releasen.
> 
> Som senior genomför jag detta i fem säkra faser enligt **Expand & Contract**:
> 1. **Expand:** Lägg till den nya kolumnen (`mobile_number`) som nullable.
> 2. **Dual-Write:** Driftsätt applikationskod som läser från gamla kolumnen men skriver till *båda*.
> 3. **Backfill:** Kör ett bakgrundsjobb som uppdaterar historiska rader i små batcher (1 000 rader/transaktion).
> 4. **Switch:** Driftsätt kod som nu läser och skriver enbart till den nya kolumnen.
> 5. **Contract:** När inga gamla instanser längre refererar den gamla kolumnen körs `ALTER TABLE DROP COLUMN customer_phone`."

---

## 🎯 Scenario 2: Read-Replicas & Replication Lag (Read-Your-Own-Writes)

### Problem (Vad kraschar / varför bryr vi oss)
För att skala läsprestanda har teamet lagt till 3 PostgreSQL Read-Replicas bakom en läspool (`@Transactional(readOnly = true)`).
En användare går in på sin profil, uppdaterar sin leveransadress och klickar på "Spara".
Frontend gör en `POST /api/user/profile` och omdirigerar direkt användaren till `/profile`.
Webbläsaren gör en `GET /api/user/profile`.

**Kraschen:**
* `POST`-anropet skrevs till **Primary (Writer)**.
* `GET`-anropet dirigerades av Spring Boot till en **Read-Replica (Reader)**.
* Replikeringen över nätverket har en **Replication Lag på 150 ms**.
* Replikan har ännu inte tagit emot skrivningen. Användaren ser fortfarande sin *gamla* adress!
* Användaren blir förvirrad, tror att uppdateringen misslyckades och klickar på "Spara" 5 gånger till, vilket skapar onödig last och supportärenden.

---

### Keyword Bingo (Facit-lingo)
* **Replication Lag:** Tidsfördröjningen mellan att en transaktion committas på primärdatabasen och att ändringen har spelats upp via WAL (Write-Ahead Log) på en sekundär läsreplika.
* **Read-Your-Own-Writes Consistency:** Garantin att en användare alltid garanterat ser sina egna senaste uppdateringar, även om andra användare tillfälligt kan se något fördröjd data.
* **Sticky Primary Session Window:** Strategin att tvinga alla läsningar från en specifik användarsession till primärdatabasen under ett kort tidsfönster (t.ex. 3–5 sekunder) efter att användaren utfört en skrivning.
* **Write-Ahead Log (WAL) LSN (Log Sequence Number):** Den exakta bytesekvenspositionen i PostgreSQLs transaktionslogg; används för att kontrollera om en replika har hunnit ikapp skrivningen.
* **Asynchronous vs Synchronous Replication:**
  * *Asynchronous:* Blixtsnabb skrivning på primär; risk för replikeringslagg och dataförlust vid primärkrasch.
  * *Synchronous:* Primären väntar på bekräftelse från replikan; noll lagg men kraftigt sänkt skrivprestanda och sårbar för nätverkshickor.

---

### Verklighets-svar (30–60 sek)
> "I arkitekturer med asynkrona läsrepliker är replikeringslagg oundvikligt. När en användare sparar och omedelbart omdirigeras läser de ofta från en replika som ligger 100 ms efter, vilket skapar illusionen av att deras ändring försvann.
> 
> Lösningen är att implementera **Read-Your-Own-Writes-konsistens** via en dynamisk routing-datasource:
> Så fort en användare utför en skrivande mutation sätter vi en tidsstämpel i användarens session eller i en cookie (t.ex. `last_write_timestamp = now()`). Under de efterföljande 3–5 sekunderna dirigeras alla läsanrop för just den användaren explicit till **Primary**, medan all övrig bakgrunds- och anonym lästrafik fortsätter avlastas till läsreplikerna."

---

## 🎯 Scenario 3: Keyset (Cursor) Pagination vs Offset/Limit Traps

### Problem (Vad kraschar / varför bryr vi oss)
En tabell med revisionsloggar (`audit_events`) har vuxit till 20 miljoner rader.
Frontend har en oändlig scroll eller sidbläddring:
```sql
-- Sida 1 (snabb, 2 ms):
SELECT * FROM audit_events ORDER BY id DESC LIMIT 50 OFFSET 0;

-- Sida 10 000 (tar 6 sekunder och slukar CPU!):
SELECT * FROM audit_events ORDER BY id DESC LIMIT 50 OFFSET 500000;
```

**Varför det kraschar:**
1. **O(N) Prestandakollaps:** Databasmotorn kan inte "hoppa" direkt till rad 500 000. Den måste läsa in och kasta bort samtliga 500 000 rader från indexet/disken innan den returnerar de 50 raderna!
2. **Data Drift (Missade eller duplicerade rader):** Om 10 nya rader infogas i tabellen medan användaren klickar från sida 1 till sida 2 förskjuts hela offsetsystemet. Användaren ser samma loggrad två gånger eller missar rader helt.

---

### Keyword Bingo (Facit-lingo)
* **Offset Pagination:** Standardmetoden (`LIMIT x OFFSET y`) som räknar rader från början; degenererar till $O(N)$ prestanda ju djupare användaren bläddrar.
* **Keyset Pagination (Cursor-based Pagination):** Mönstret att använda den sista sedda radens unika nyckel som filter (`WHERE id < :last_seen_id ORDER BY id DESC LIMIT 50`), vilket ger $O(1)$ söktid via index-bisektion.
* **Tie-Breaker Column:** När man sorterar på icke-unika kolumner (såsom `created_at`) måste en unik kolumn (såsom `id`) läggas till som tie-breaker: `WHERE (created_at, id) < (:last_created_at, :last_id)`.
* **Stable Pagination:** Egenskapen hos keyset-paginering att nya rader som infogas överst i tabellen aldrig förskjuter eller duplicerar rader för en användare som bläddrar nedåt.
* **Seek Method:** Databasens förmåga att utföra ett direkt B-Tree Seek till exakt rätt minnesblock istället för en linjär scanning.

---

### Verklighets-svar (30–60 sek)
> "`LIMIT / OFFSET` är en tickande bomb för stora tabeller. Vid `OFFSET 500000` tvingas PostgreSQL läsa och kasta 500 000 rader bara för att visa de sista 50, vilket skapar linjär prestandadegradering ($O(N)$) och I/O-spikar. Dessutom leder parallella inserts till duplicerade eller missade poster mellan sidbyten.
> 
> Som senior använder jag alltid **Keyset (Cursor-based) pagination** för stora datamängder:
> Istället för ett offsetvärde skickar klienten med pekaren för den senast visade posten (`cursor = last_id`). SQL-frågan blir `WHERE id < :cursor ORDER BY id DESC LIMIT 50`. Detta är en $O(1)$-operation eftersom databasen omedelbart hoppar direkt till rätt nod i B-Tree-indexet, oavsett om tabellen har 50 rader eller 50 miljoner rader."

---

### Felsökningsguide (CLI & psql)
Jämför exekveringsplanen för Offset mot Keyset i PostgreSQL:
```sql
-- Offset-metoden (Visar hög kostnad och scanning):
EXPLAIN ANALYZE
SELECT * FROM audit_events ORDER BY id DESC LIMIT 50 OFFSET 500000;

-- Keyset-metoden (Visar Index Condition seek och mikrosekunder):
EXPLAIN ANALYZE
SELECT * FROM audit_events WHERE id < 19500000 ORDER BY id DESC LIMIT 50;
```

---

## 🛠️ Koden & Verifiering

* **The Flaw & The Fix:** [MigrationAndReplicationLab.java](file:///database/02-zero-downtime-migrations-and-replication/MigrationAndReplicationLab.java)
  * *The Flaw:* Direktläsning från lagging replica visar förlegad profil; direkt kolumnbyte bryter v1-klienter.
  * *The Fix:* `DualWriteOrderService` stöder v1 och v2 samtidigt under rollout; `ProfileDatabaseCluster` dirigerar nyligen skrivande användare till Primary via Sticky Window; `KeysetPaginationService` garanterar $O(1)$ stabil sökning.
* **Verification Test:** [MigrationAndReplicationVerificationTest.java](file:///database/02-zero-downtime-migrations-and-replication/MigrationAndReplicationVerificationTest.java)
  * Bevisar att dual-write håller både gamla och nya klienter gröna.
  * Bevisar att replikeringslagg uppstår och att sticky primary window löser det.
  * Bevisar stabil bläddring utan duplicerade rader under pågående inserts.

---

## 🧪 Så här kör du testerna

```bash
cd database/02-zero-downtime-migrations-and-replication
mvn test
```
