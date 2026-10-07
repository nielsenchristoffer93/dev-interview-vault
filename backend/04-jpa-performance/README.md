# 🗄️ 04-jpa-performance: N+1 Queries, EntityGraph & Optimistic Locking

Denna modul täcker 4 fundamentala databas- och JPA/Hibernate-prestandautmaningar i Spring Boot-applikationer. Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 1: N+1 Query-problemet & `@EntityGraph` / `JOIN FETCH`

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
En endpoint hämtar 100 kunder och itererar över deras ordrar (`customer.getOrders()`). Med standard `FetchType.LAZY` skickar Hibernate 1 initial SELECT för att hämta alla kunder, följt av 100 separata SELECT-frågor för varje enskild kunds ordrar när samlingen läses = **101 databasanrop**.

Detta sänker databasens connection pool (`HikariCP`), orsakar enorm nätverkslatens och förvandlar ett 15ms-anrop till en 3-sekunders flaskhals.

#### 2. Keyword Bingo (Facit-lingo)
* **N+1 Query Problem:** 1 SELECT för huvudtabellen + N individuella SELECT-frågor för varje relaterad barnentitet vid lazy loading i en loop.
* **`JOIN FETCH` (JPQL):** Instruktion till Hibernate att utföra en SQL `LEFT OUTER JOIN` och hydrera förälder och barn i ett enda nätverksanrop.
* **`@EntityGraph(attributePaths = {"..."})`:** Spring Data JPAs deklarativa sätt att ange eager fetch dynamiskt per repository-metod utan hårdkodade JPQL-queries.
* **HikariCP Connection Pool Exhaustion:** När hundratals parallella queries dränerar databasens anslutningspool så att trådar blockeras.
* **FetchType.LAZY vs FetchType.EAGER:**
  * *LAZY:* Standard och rekommenderat (laddar data vid behov).
  * *EAGER:* Anti-mönster i entitetsdefinitioner eftersom data alltid laddas även när den inte behövs.

#### 3. Verklighets-svar (30–60 sek)
> *"N+1-problemet uppstår när JPA kör en initial SELECT för att hämta entiteter och därefter en separat SELECT per entitet för varje lazy-laddad relation som itereras i loopen. För 100 kunder blir det 101 roundtrips till databasen, vilket sänker anslutningspoolen. Vi löser det genom att behålla `FetchType.LAZY` på entiteten men specificera dynamisk eager fetch på anropet med `JOIN FETCH` i JPQL eller `@EntityGraph(attributePaths = {"orders"})` i Spring Data. Då körs en enda SQL JOIN och all data hämtas i en enda roundtrip."*

**Labb & Kod:** [CustomerLazyEntity.java](file:///backend/04-jpa-performance/src/main/java/com/vault/jpa/CustomerLazyEntity.java) vs [CustomerOptimizedEntity.java](file:///backend/04-jpa-performance/src/main/java/com/vault/jpa/CustomerOptimizedEntity.java)

---

### Scenario 2: Kartesisk produkt (Cartesian Product Problem) & Batch Fetching

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
När en entitet har två oberoende samlingar (t.ex. `Customer` har `List<Order>` och `List<Address>`) och utvecklaren försöker lösa N+1 genom att göra `JOIN FETCH` på båda samlingarna samtidigt, multipliceras resultatmängden på databasnivå (**Kartesisk produkt**).

Om en kund har 10 ordrar och 5 adresser returnerar databasen $10 \times 5 = 50$ rader för en enda kund. Hibernate kastar ofta `org.hibernate.loader.MultipleBagFetchException`, och om `Set` används skickas enorma mängder duplicerad data över nätverket.

#### 2. Keyword Bingo (Facit-lingo)
* **Cartesian Product Problem (Kartesisk produkt):** Multiplikation av resultatrader när flera samlingar joinas samtidigt i SQL.
* **`MultipleBagFetchException`:** Hibernates inbyggda skydd som förbjuder `JOIN FETCH` på fler än en `List` (bag) samtidigt.
* **`default_batch_fetch_size: 25`:** Global Hibernate-egenskap som instruerar Hibernate att hämta lazy-laddade samlingar i batcher med `IN (?, ?, ?)` istället för en och en.
* **`@BatchSize`:** Motsvarande annotering på entitetsnivå för att styra batchstorlek per specifik relation.

#### 3. Verklighets-svar (30–60 sek)
> *"Att göra `JOIN FETCH` på flera samlingar samtidigt skapar en kartesisk produktionsexplosion där radantalet multipliceras, vilket leder till `MultipleBagFetchException` i Hibernate. Bästa praxis är att enbart join-fetcha den viktigaste relationen och lösa resten med batch fetching genom att sätta `spring.jpa.properties.hibernate.default_batch_fetch_size: 25` i `application.yml`. Då slår Hibernate ihop efterföljande lazy-hämtningar i effektiva `SELECT ... WHERE customer_id IN (?, ?, ?)`-anrop, vilket reducerar 100 frågor till 4 utan kartesisk produkt."*

**Labb & Kod:** [JpaPerformanceVerificationTest.java](file:///backend/04-jpa-performance/src/test/java/com/vault/jpa/JpaPerformanceVerificationTest.java)

---

### Scenario 3: Lost Updates & Optimistisk låsning (`@Version`)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Två användare öppnar samma kund eller konto samtidigt i webbgränssnittet. Båda ser samma saldo (1 000 kr). Användare A sparar en ändring (minskar med 200 kr). Användare B sparar en sekund senare (ökar med 500 kr).

Om entiteten saknar samtidighetskontroll skriver Användare B ovetandes över Användare A:s ändring (**Lost Update**). Kundens saldo blir 1 500 kr istället för det korrekta 1 300 kr. Ingen felkod loggas och databasen blir korrumperad.

#### 2. Keyword Bingo (Facit-lingo)
* **Lost Update Problem:** När två transaktioner läser samma data och den sista skrivningen skriver över den första utan att ta hänsyn till mellankommande ändringar.
* **Optimistic Locking:** Samtidighetskontroll baserad på antagandet att konflikter är sällsynta; validering sker vid skrivögonblicket.
* **`@Version`:** JPA-annotering på ett heltals- eller timestamp-fält som automatiskt inkrementeras vid varje `UPDATE`.
* **`OptimisticLockException`:** Undantaget som kastas när `UPDATE ... WHERE id = ? AND version = ?` returnerar 0 påverkade rader (konflikt detekterad!).
* **Stale Object State Exception:** Hibernates underliggande undantag vid versionsavvikelse.

#### 3. Verklighets-svar (30–60 sek)
> *"Lost Updates uppstår när två användare läser samma data samtidigt och den sista skrivningen skriver över den förstas arbete. Vi förhindrar detta med optimistisk låsning genom att lägga till ett `@Version`-fält på entiteten. När JPA sparar körs `UPDATE ... WHERE id = ? AND version = ?`. Om en annan transaktion hunnit ändra raden i förväg matchar inte versionsnumret, frågan påverkar 0 rader och Spring kastar `OptimisticLockException`. Transaktionen rullas tillbaka och vi kan informera användaren om konflikten istället för att tyst korrumpera datat."*

**Labb & Kod:** [UnversionedAccount.java](file:///backend/04-jpa-performance/src/main/java/com/vault/jpa/UnversionedAccount.java) vs [VersionedAccount.java](file:///backend/04-jpa-performance/src/main/java/com/vault/jpa/VersionedAccount.java)

---

### Scenario 4: Pessimistisk låsning (`PESSIMISTIC_WRITE`) för hög kontention

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Under extrem kontention (t.ex. Flash Sale med 10 000 köpare för 5 biljetter, eller intensiv saldojustering i realtid) leder optimistisk låsning till en kollaps: 99% av alla transaktioner misslyckas med `OptimisticLockException`.

Om applikationen är konfigurerad med retries hamnar systemet i en "Retry Storm" som förbrukar enorma mängder CPU och databasresurser på dömda transaktioner.

#### 2. Keyword Bingo (Facit-lingo)
* **Pessimistic Locking:** Samtidighetskontroll där raden låses direkt på databasnivå vid läsning.
* **`LockModeType.PESSIMISTIC_WRITE`:** JPA-låstyp som översätts till SQL `SELECT ... FOR UPDATE`.
* **Row-Level Lock:** Databaslås som blockerar andra transaktioner från att läsa med lås eller ändra raden tills transaktionen committas.
* **Deadlock Risk:** Risken att två transaktioner försöker ta pessimistiska lås på samma rader i omvänd ordning (kräver konsekvent låsordning eller lås-timeout).
* **`javax.persistence.lock.timeout`:** Hint som förhindrar att en tråd väntar för evigt på ett pessimistiskt lås.

#### 3. Verklighets-svar (30–60 sek)
> *"Vid mycket hög kontention, som inventariejustering under en flash sale, skapar optimistisk låsning för många undantag och retries. I de specifika fallen använder vi pessimistisk låsning via `LockModeType.PESSIMISTIC_WRITE`. Detta genererar en `SELECT ... FOR UPDATE` på databasnivå, vilket serialiserar transaktionerna och låter trådar vänta i tur och ordning vid raden. Vi sätter alltid en strikt `javax.persistence.lock.timeout` för att garantera att vi inte fastnar i databasdeadlocks."*

**Labb & Kod:** [VersionedAccount.java](file:///backend/04-jpa-performance/src/main/java/com/vault/jpa/VersionedAccount.java)

---

## 2. Felsökningsguide (Kommandon & Verktyg)

### A. Identifiera N+1 med Hibernate SQL Logging i `application.yml`
```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        generate_statistics: true # Loggar antal körda JDBC queries och sessions-tider
```

### B. Automatisera upptäckt med `datasource-proxy` / QuickPerf i tester
Med `datasource-proxy` kan du assertionsätta max antal frågor i integrationstester:
```java
// I ett integrationstest: verifiera att enbart 1 query körs
assertThat(queryCount).isEqualTo(1);
```

---

## 3. Koden & Verifiering

* **The Flaw:** [CustomerLazyEntity.java](file:///backend/04-jpa-performance/src/main/java/com/vault/jpa/CustomerLazyEntity.java) & [UnversionedAccount.java](file:///backend/04-jpa-performance/src/main/java/com/vault/jpa/UnversionedAccount.java) – Lazy relation utan join fetch och kontouppdatering utan version.
* **The Fix:** [CustomerOptimizedEntity.java](file:///backend/04-jpa-performance/src/main/java/com/vault/jpa/CustomerOptimizedEntity.java) & [VersionedAccount.java](file:///backend/04-jpa-performance/src/main/java/com/vault/jpa/VersionedAccount.java) – Visar `@EntityGraph`/join fetch och `@Version` optimistiskt skydd.
* **Verification Test:** [JpaPerformanceVerificationTest.java](file:///backend/04-jpa-performance/src/test/java/com/vault/jpa/JpaPerformanceVerificationTest.java) – Bevisar query-reduktion från 1+N till 1 samt att `OptimisticLockException` fångar samtidiga skrivningar.

---

## 4. Så här kör du testerna

Kör enhet- och integrationstesterna med Maven:
```bash
cd backend/04-jpa-performance
mvn clean test
```
* **Vad testet bevisar:**
  * Visar att ooptimerad hämtning genererar $1 + N$ queries.
  * Visar att `@EntityGraph` reducerar anropen till exakt 1 query via SQL JOIN.
  * Visar att samtidiga uppdateringar på unversioned konton resulterar i Lost Updates.
  * Visar att `VersionedAccount` kastar `OptimisticLockException` och förhindrar datakorruption.
