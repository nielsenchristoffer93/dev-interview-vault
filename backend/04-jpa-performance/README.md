# 🗄️ 04-jpa-performance: N+1 Queries, EntityGraph & Optimistic Locking

## 1. Problem: Vad kraschar eller varför bryr vi oss?
1. **N+1 Query-problemet:** En endpoint hämtar 100 kunder och itererar över deras ordrar (`customer.getOrders()`). JPA skickar 1 query för att hämta alla kunder, följt av 100 individuella SELECT-frågor för respektive kunds ordrar = **101 databasanrop**. Detta sänker databasens connection pool och orsakar extrem svarstidslatens.
2. **Lost Updates vid samtidiga ändringar:** Två användare öppnar samma kund i gränssnittet samtidigt. Båda sparar sina ändringar. Den sista som sparar skriver ovetandes över den förstas ändringar om tabellen saknar samtidighetskontroll.

---

## 2. Keyword Bingo (Facit-lingo)
* **N+1 Query Problem:** 1 SELECT för huvudtabellen + N SELECT:s för varje barnrelation vid lazy loading i en loop.
* **`JOIN FETCH` vs `@EntityGraph`:** Sättet att instruera Hibernate att utföra en SQL `LEFT OUTER JOIN` i ett enda anrop.
  * `@EntityGraph(attributePaths = {"orders"})` möjliggör dynamisk eager fetch på metoder utan hårdkodad JPQL.
* **Cartesian Product Problem (Kartesisk produkt):** Om man gör `JOIN FETCH` på flera oberoende `@OneToMany`-relationer samtidigt multipliceras antalet rader i resultatmängden (orsakar `MultipleBagFetchException` i Hibernate). Lösning: Hämta en relation via join fetch och använd batch fetching (`default_batch_fetch_size: 20`) för övriga.
* **Optimistic Locking (`@Version`):** Använder ett versionsfält (`UPDATE ... WHERE id = ? AND version = ?`). Om versionen inte matchar kastas `OptimisticLockException` och transaktionen rullas tillbaka.
* **Pessimistic Locking (`PESSIMISTIC_WRITE`):** Utför `SELECT ... FOR UPDATE` på databasnivå och låser raden tills transaktionen committas. Lämpligt för högt omstridda lager- eller saldojusteringar.

---

## 3. Verklighets-svar (30–60 sek)
> *"N+1-problemet uppstår när JPA kör en initial SELECT för att hämta entiteter och därefter en separat SELECT per entitet för varje lazy-laddad association som itereras. För 100 entiteter blir det 101 roundtrips till databasen. Vi löser det med `JOIN FETCH` i JPQL eller med Spring Datas `@EntityGraph(attributePaths = {"..."})`. Om vi behöver hämta flera samlingar undviker vi kartesisk produktexplosion genom att sätta `spring.jpa.properties.hibernate.default_batch_fetch_size: 25`. För att förhindra Lost Updates vid samtidiga ändringar lägger vi till ett `@Version`-fält för optimistisk låsning, vilket detekterar konflikter och kastar `OptimisticLockException` istället för att skriva över data."*

---

## 4. Felsökningsguide (Kommandon & Verktyg)

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

### B. Automatisera upptäckt med QuickPerf / datasource-proxy
Med `datasource-proxy` kan du assertionsätta max antal frågor i integrationstester:
```java
// I ett test: verifiera att enbart 1 query körs
assertThat(queryCount).isEqualTo(1);
```

---

## 5. Koden & Verifiering

* **The Flaw:** [CustomerLazyEntity.java](file:///backend/04-jpa-performance/src/main/java/com/vault/jpa/CustomerLazyEntity.java) & [UnversionedAccount.java](file:///backend/04-jpa-performance/src/main/java/com/vault/jpa/UnversionedAccount.java) – Lazy relation utan join fetch och kontouppdatering utan version.
* **The Fix:** [CustomerOptimizedEntity.java](file:///backend/04-jpa-performance/src/main/java/com/vault/jpa/CustomerOptimizedEntity.java) & [VersionedAccount.java](file:///backend/04-jpa-performance/src/main/java/com/vault/jpa/VersionedAccount.java) – Visar `@EntityGraph`/join fetch och `@Version` optimistiskt skydd.
* **Verification Test:** [JpaPerformanceVerificationTest.java](file:///backend/04-jpa-performance/src/test/java/com/vault/jpa/JpaPerformanceVerificationTest.java) – Bevisar query-reduktion från 1+N till 1 samt att `OptimisticLockException` fångar samtidiga skrivningar.
