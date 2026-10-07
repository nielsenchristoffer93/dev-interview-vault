# 🗄️ 01-indexes-locks-and-plans: B-Tree Index, DDL Locks & Transaction Isolation

> **Fokus:** Djupdykning i relationsdatabaser (PostgreSQL / ANSI SQL) på produktionsnivå. Förstå hur B-Tree-index navigeras via "Leftmost Prefix Rule", varför schemamigrationer sänker applikationer genom `ACCESS EXCLUSIVE LOCK`-köer, och hur "Write Skew"-anomalier krossar affärsinvarianter trots `READ COMMITTED` och `REPEATABLE READ`.

---

## 🧭 Översikt

I seniora systemdesign- och backendintervjuer räcker det inte att kunna skriva en `SELECT`-fråga eller känna till grundläggande ACID. Det som skiljer seniora ingenjörer från mängden är förståelsen för **databasens interna exekveringsmotor**:
1. Hur exekveringsplanerare (`EXPLAIN ANALYZE`) väljer mellan `Seq Scan`, `Index Scan` och `Bitmap Index Scan`.
2. Hur låsköer i PostgreSQL prioriteras vid DDL och hur man migrerar utan driftstopp.
3. Varför Snapshot Isolation i moderna relationsdatabaser tillåter Write Skew och hur det åtgärdas.

---

## 🎯 Scenario 1: B-Tree Composite Index & Leftmost Prefix Rule

### Problem (Vad kraschar / varför bryr vi oss)
En order-tabell har 10 miljoner rader och ett sammansatt index på `(status, created_at)`. Utvecklaren skriver en query för att hämta ordrar skapade under den senaste veckan:
```sql
SELECT * FROM orders WHERE created_at >= NOW() - INTERVAL '7 days';
```
Trots att `created_at` ingår i indexet väljer PostgreSQL en full **Sequential Scan** över alla 10 miljoner rader! 
CPU-förbrukningen slår i 100%, disk-I/O exploderar, frågan tar 12 sekunder istället för 2 ms, och databasen börjar droppa inkommande anslutningar.

Dessutom inträffar en klassisk **Range Trap**: Om indexet vore `(created_at, status)` och frågan är `WHERE created_at >= '2026-01-01' AND status = 'COMPLETED'`, kan databasen bara använda indexet för intervallet på `created_at`. Eftersom den första kolumnen är ett intervall (`>=`) kan inte B-Tree hoppa direkt till noderna med `status = 'COMPLETED'`, utan måste skanna alla rader inom datumintervallet.

### Keyword Bingo (Facit-lingo)
* **B-Tree Index:** Standardindexet i relationsdatabaser, organiserat som ett balanserat sökträd sorterat på indexerade kolumner från rot till lövnoder.
* **Composite Index (Sammansatt index):** Ett index som spänner över flera kolumner `(col1, col2)` i en strikt definierad ordning.
* **Leftmost Prefix Rule:** Regeln att en query måste filtrera på den ledande (vänstraste) kolumnen för att B-Tree-strukturen ska kunna navigeras effektivt.
* **Sequential Scan (Seq Scan):** När databasen tvingas läsa varje enskild rad i hela tabellen från disk/minne eftersom inget lämpligt index kunde användas.
* **Index Scan vs Bitmap Index Scan:**
  * *Index Scan:* Navigerar indexet och läser motsvarande tabellrader direkt, en i taget (bäst vid mycket hög selektivitet).
  * *Bitmap Index Scan:* Skapar en bitkarta i minnet över matchande sidor och läser dem i fysisk blockordning (optimerar disk-I/O vid medelhög selektivitet).
* **Cardinality & Index Selectivity:**
  * *Kardinalitet:* Antalet unika värden i en kolumn.
  * *Selektivitet:* Andelen av tabellens rader som matchas; ju högre selektivitet (färre matchande rader), desto mer benägen är motorn att använda index.
* **Range Query Trap:** När ett intervallfilter (`>`, `<`, `BETWEEN`) på en kolumn förhindrar att efterföljande kolumner i samma index används för direkt B-Tree-bisektion.
* **Covering Index (`INCLUDE`):** Ett index som innehåller extra nyttolastkolumner så att databasen kan svara helt via **Index Only Scan** utan att behöva slå upp huvudtabellens heapsidor.
* **`EXPLAIN (ANALYZE, BUFFERS)`:** Det ultimata analyskommandot i PostgreSQL som visar faktisk exekveringstid, planerad kostnad och exakt antal lästa minnes- och diskbuffertar.

### Verklighets-svar (30–60 sek)
> "Ett sammansatt B-Tree-index fungerar som en telefonkatalog sorterad på (Efternamn, Förnamn). Om du söker enbart på förnamn kan du inte använda alfabetssorteringen – databasen tvingas göra en full Sequential Scan över hela tabellen. För att ett sammansatt index ska användas måste frågan matcha från vänster enligt 'Leftmost Prefix Rule'.
> 
> Vidare gäller att så fort en kolumn i indexet filtreras med ett intervall (`>`, `<`, `BETWEEN`) kan efterföljande kolumner i samma index inte användas för direkt B-Tree-bisektion. Som senior placerar jag alltid kolumner med hög selektivitet och exakt likhet (`=`) först i indexet, följt av range-kolumner, och använder Covering Indexes med `INCLUDE` för att möjliggöra Index Only Scans utan tabell-lookups."

### Felsökningsguide (CLI & psql)
Inspektera frågeplanen med faktisk körtid och diskbuffertar:
```sql
EXPLAIN (ANALYZE, BUFFERS) 
SELECT * FROM orders 
WHERE created_at >= NOW() - INTERVAL '7 days';
```
Leta efter:
* `Seq Scan on orders`: Varning! Databasen läser hela tabellen från disk/cache.
* `Rows Removed by Filter`: Antal rader som lästes i onödan och kastades bort.
* `Buffers: shared read=...`: Faktiska diskblock som lästes in.

### Körguide
* **The Flaw:** Granska [flawed-queries.sql](file:///database/01-indexes-locks-and-plans/flawed-queries.sql).
* **The Fix:** Granska [optimized-queries.sql](file:///database/01-indexes-locks-and-plans/optimized-queries.sql).
* **Live Test:** Kör `./verify-database.sh` mot lokal PostgreSQL.

---

## 🎯 Scenario 2: Zero-Downtime Migrations & DDL Lock Queues

### Problem (Vad kraschar / varför bryr vi oss)
En utvecklare kör en schemamigration mitt på dagen:
```sql
ALTER TABLE orders ADD COLUMN is_priority BOOLEAN DEFAULT false NOT NULL;
```
Eller skapar ett nytt index med vanlig `CREATE INDEX`.

Kommandot kräver ett **`ACCESS EXCLUSIVE LOCK`** på tabellen. Även om själva ändringen tar 5 ms fastnar DDL-kommandot i PostgreSQL:s låskö om det finns en enda långsam läsfråga (t.ex. en rapportfråga på 15 sekunder) som körs mot tabellen.

**Katastrofen:** PostgreSQL prioriterar exklusiva lås i kön för att undvika svält. Det innebär att **ALLA nya `SELECT`- och `INSERT`-frågor som anländer ställs i kö BAKOM DDL-transaktionen.** Inom 3 sekunder är applikationens HikariCP-anslutningspool tömd, HTTP-anrop börjar tajma ut med 504 Gateway Timeout, K8s readiness probes fallerar, och hela produktionsmiljön slås ut.

### Keyword Bingo (Facit-lingo)
* **`ACCESS EXCLUSIVE LOCK`:** Det mest restriktiva låset i PostgreSQL, krävs för DDL (`ALTER TABLE`, `DROP`, standard `CREATE INDEX`) och förbjuder ALL simultan läsning och skrivning.
* **Lock Queue Contention:** När en blockerande DDL-transaktion ställer sig i låskön och tvingar ALLA efterföljande `SELECT` och `INSERT` att vänta bakom den.
* **`lock_timeout`:** Inställning (`SET lock_timeout = '2s'`) som avbryter DDL-transaktionen om låset inte kan tas omedelbart, vilket skyddar applikationens anslutningspool från kollaps.
* **`statement_timeout`:** Säkerhetsspärr som automatiskt avbryter queries som körs längre än en specificerad maxgräns.
* **`CREATE INDEX CONCURRENTLY`:** PostgreSQL-kommando som bygger indexet i bakgrunden utan att ta exklusivt lås, vilket håller tabellen fullt läs- och skrivbar under hela byggtiden.
* **Expand and Contract Pattern (Parallel Run):** Migrationsmönster där kolumnändringar görs i multipla bakåtkompatibla steg (lägg till ny kolumn, dubbelskriv, migrera data, ta bort gammal).
* **`pg_locks` & `pg_stat_activity`:** Systemvyerna som används för att i realtid lokalisera blockerande sessioner och låsköer i databasen.
* **Connection Pool Exhaustion:** När HikariCP töms på tillgängliga anslutningar på grund av att trådar blockeras av väntande databaslås.

### Verklighets-svar (30–60 sek)
> "I PostgreSQL kräver DDL och vanliga index ett `ACCESS EXCLUSIVE LOCK`. Om det finns en pågående långsam läsning blockeras DDL-kommandot, och eftersom PostgreSQL prioriterar låskön blockeras ALLA efterföljande läsningar och skrivningar bakom DDL-transaktionen. Det dränerar applikationens anslutningspool på några sekunder och orsakar totalt driftstopp.
> 
> För att garantera zero-downtime tillämpar jag tre gyllene regler:
> 1. Sätt alltid en aggressiv `SET lock_timeout = '2s'` före DDL – om låset inte kan tas direkt avbryts migrationen kontrollerat istället för att köa upp trafiken.
> 2. Skapa alltid produktionsindex med `CREATE INDEX CONCURRENTLY` så att tabellen förblir fullt läs- och skrivbar under byggtiden.
> 3. Tillämpa Expand-and-Contract-mönstret för kolumnändringar i flera isolerade steg."

---

## 🎯 Scenario 3: Phantom Reads, Write Skew & Transaktionsisolering

### Problem (Vad kraschar / varför bryr vi oss)
I ett sjukhus- och joursystem är affärsregeln: *"Det måste alltid finnas minst en läkare i aktiv jour"*.
Just nu har Dr. Alice och Dr. Bob jour (totalt 2 läkare).
Båda två begär ledigt exakt samtidigt via två parallella HTTP-anrop:
1. Tråd A (Alice) läser: `SELECT COUNT(*) FROM on_call_doctors WHERE is_on_call = true;` -> Returnerar 2.
2. Tråd B (Bob) läser: `SELECT COUNT(*) FROM on_call_doctors WHERE is_on_call = true;` -> Returnerar 2.
3. Tråd A kör: `UPDATE on_call_doctors SET is_on_call = false WHERE id = 1;` och gör `COMMIT`.
4. Tråd B kör: `UPDATE on_call_doctors SET is_on_call = false WHERE id = 2;` och gör `COMMIT`.

**Resultat:** Båda uppdateringarna lyckas! Båda ändrade olika rader, så det fanns ingen radkollision. Databasen har nu **0 aktiva jourläkare kvar**. Affärsinvarianten är bruten.

Detta fel kallas **Write Skew**. Många utvecklare tror felaktigt att `REPEATABLE READ` löser detta, men det gör det **inte** i motorer med Snapshot Isolation (PostgreSQL/Oracle/MySQL InnoDB) eftersom de uppdaterade raderna är disjunkta.

### Keyword Bingo (Facit-lingo)
* **Transaction Isolation Levels (ANSI SQL):** De fyra nivåerna `Read Uncommitted`, `Read Committed`, `Repeatable Read` och `Serializable`.
* **Write Skew Anomaly:** När två transaktioner läser överlappande data och uppdaterar olika (disjunkta) rader så att en övergripande systeminvariant bryts.
* **Non-Repeatable Read vs Phantom Read:**
  * *Non-Repeatable Read:* Samma rad ändras eller raderas av en annan transaktion mellan två läsningar.
  * *Phantom Read:* Nya rader som matchar ett sökvillkor infogas av en annan transaktion mellan två läsningar.
* **Snapshot Isolation (MVCC):** Mekanismen där transaktioner läser en konsekvent ögonblicksbild av databasen, vilket eliminerar lås på läsningar men tillåter Write Skew.
* **Serializable Snapshot Isolation (SSI):** PostgreSQL:s avancerade algoritm i `SERIALIZABLE` som spårar rw-antidependencies och kastar serialiseringsfel vid misstänkt anomalirisk.
* **`SELECT ... FOR UPDATE` (Pessimistic Row Lock):** Explicit radlåsning som tvingar samtidiga transaktioner att vänta i kö, vilket förhindrar Write Skew under `READ COMMITTED`.
* **Invariant Integrity:** Säkerställandet att affärsregler (t.ex. "minst 1 jourläkare") hålls intakta under extrem samtidig belastning.

### Verklighets-svar (30–60 sek)
> "Write Skew uppstår när två samtidiga transaktioner läser överlappande datamängder, fattar ett beslut baserat på datan, och därefter uppdaterar olika (disjunkta) rader så att en övergripande systeminvariant bryts.
> 
> Många utvecklare tror felaktigt att `REPEATABLE READ` skyddar mot detta, men det skyddar bara mot att samma rad muteras av någon annan. Eftersom läkarna muterar olika rader krockar inte MVCC-versionerna. Lösningen är antingen att använda pessimistisk låsning med `SELECT ... FOR UPDATE` (vilket tvingar transaktion två att vänta och se den uppdaterade datan), eller att höja isoleringsnivån till `SERIALIZABLE`, där PostgreSQL:s SSI-algoritm (Serializable Snapshot Isolation) känner av rw-antidependencies och kastar ett serialiseringsfel."

---

## 📋 Snabbreferens för Intervjun

| Koncept | The Flaw (Varför det kraschar i prod) | The Fix (Senior Lösning) |
| :--- | :--- | :--- |
| **B-Tree Index** | Query saknar ledande kolumn -> Full Seq Scan | Leftmost Prefix Rule (`=`, sedan range `>`, `<`) |
| **Schema Migration** | DDL tar `ACCESS EXCLUSIVE LOCK` och blockerar hela kön | `SET lock_timeout = '2s'` + `CREATE INDEX CONCURRENTLY` |
| **Write Skew** | Samtidiga läsningar uppdaterar olika rader -> Invariant krossas | `SELECT ... FOR UPDATE` eller `SERIALIZABLE` isolering |
