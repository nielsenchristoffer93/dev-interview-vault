# 📐 06-testing-pyramid: Mockito vs @WebMvcTest vs Testcontainers

Denna modul täcker 3 fundamentala testarkitektur- och byggtidsproblem i moderna Spring Boot-applikationer. Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 1: H2 In-Memory Illusionen vs Testcontainers (PostgreSQL)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Att köra integrationstester mot in-memory H2 under förevändningen att det "motsvarar PostgreSQL" är ett allvarligt anti-mönster. H2 ignorerar Postgres-specifika SQL-funktioner, accepterar ogiltiga kolumndefinitioner och stödjer inte moderna datatyper som `JSONB` eller Postgres-specifika index.

Tester är 100% gröna i CI-pipelinen, men vid driftsättning i produktion smäller applikationen omedelbart med `org.postgresql.util.PSQLException`.

#### 2. Keyword Bingo (Facit-lingo)
* **H2 In-Memory Illusionen:** Det falska självförtroendet av gröna tester mot en in-memory-databas som inte delar produktionsdatabasens dialekt eller låsningssemantik.
* **Dialekt-trohet (Production Parity):** Principen att testmiljön måste köra exakt samma databasmotor och version som produktion.
* **Testcontainers:** Java-bibliotek som startar riktiga Docker-containrar direkt från JUnit 5-livscykeln.
* **`@Container` & `@Testcontainers`:** JUnit 5-annoteringar som hanterar livscykeln för tillfälliga containrar.
* **Ephemeral Database Instances:** Rena, isolerade databasinstanser som försvinner efter testerna utan delat smutsigt tillstånd.

#### 3. Verklighets-svar (30–60 sek)
> *"Vi har helt slutat använda H2 för databastester på grund av dialektskillnader och falsk trygghet. H2 missar Postgres-specifik syntax, låsningsbeteenden och typer som JSONB, vilket gör att buggar smiter ut i produktion. Istället kör vi Testcontainers med en riktig PostgreSQL-container direkt i testsviten. Testerna körs mot en identisk databasversion som i produktion, vilket garanterar 100% pålitlighet utan att utvecklare behöver installera en lokal databas manuellt."*

**Labb & Kod:** [FlawedH2AssumptionsTest.java](file:///backend/06-testing-pyramid/src/test/java/com/vault/testing/FlawedH2AssumptionsTest.java) vs [PostgresTestcontainersIntegrationTest.java](file:///backend/06-testing-pyramid/src/test/java/com/vault/testing/PostgresTestcontainersIntegrationTest.java)

---

### Scenario 2: Spring ApplicationContext Cache-invalidering (`@MockBean` & `@DirtiesContext`)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
När utvecklare sätter `@SpringBootTest` på minsta lilla test och sprider ut `@MockBean` slumpartat exploderar byggtiderna i CI från 45 sekunder till 15–20 minuter.

Orsaken är att Springs testramverk cachar `ApplicationContext` baserat på konfigurationen. Varje unik kombination av `@MockBean` betraktas som en ny miljö. Spring tvingas destruera och bygga upp hela Spring-kontexten (starta DataSource, flyway, beans) tiotals gånger under samma byggkörning.

#### 2. Keyword Bingo (Facit-lingo)
* **ApplicationContext Caching:** Springs mekanism för att återanvända samma startade applikationskontext mellan testklasser.
* **Context Cache Pollution:** När tester med varierande mockade bönor gör det omöjligt för Spring att återanvända kontexten.
* **`@DirtiesContext`:** Annotering som tvingar Spring att slänga kontexten efter ett test – ett kostsamt straff som kostar flera sekunder per test.
* **Delad abstrakt testbasklass:** Mönstret att samla alla integrationstest-konfigurationer i en gemensam basklass så att kontexten startar exakt en gång.

#### 3. Verklighets-svar (30–60 sek)
> *"Om testbyggtider drar iväg beror det nästan alltid på att Spring ApplicationContext startas om i onödan. Spring återanvänder kontexten så länge konfigurationen är identisk. Men varje unik `@MockBean` skapar en ny kontextvariant och tvingar fram en full omstart. Vi löser det genom att testa affärslogik i rena Mockito-enhetstester utan Spring, och för de integrationstester som kräver kontext ärver vi från en gemensam abstrakt basklass med en enhetlig uppsättning beans så att kontexten startas exakt en gång för hela sviten."*

**Labb & Kod:** [OrderServiceUnitTest.java](file:///backend/06-testing-pyramid/src/test/java/com/vault/testing/OrderServiceUnitTest.java)

---

### Scenario 3: Testpyramidens 3 nivåer (Unit vs Slice vs Integration)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Ett vanligt misstag är den "inverterade teststruten" (Ice Cream Cone Anti-pattern): teamet skriver nästan inga enhetstester, utan verifierar all validering och beräkning via tunga `@SpringBootTest`-anrop.

Detta resulterar i en långsam testsvit som tar evigheter att köra lokalt, vilket gör att utvecklare slutar köra tester innan de pushar kod.

#### 2. Keyword Bingo (Facit-lingo)
* **Testing Pyramid (Testpyramiden):**
  * *Nivå 1 – Unit Tests:* Ren Java + Mockito. Exekveras på mikrosekunder. Testar affärslogik, beräkningar och edge cases.
  * *Nivå 2 – Slice Tests:* `@WebMvcTest` (enbart webblagret) eller `@DataJpaTest` (enbart queries/entiteter). Laddar 10% av kontexten.
  * *Nivå 3 – Integration Tests:* `@SpringBootTest` + Testcontainers. Verifierar att hela pipelinen hänger ihop.
* **MockMvc:** Verktyg för att testa HTTP-routing, JSON-serialisering och HTTP-statuskoder utan att starta en riktig webbserver.
* **Feedback Loop:** Hastigheten med vilken en utvecklare vet om en kodändring fungerar (millisekunder vs minuter).

#### 3. Verklighets-svar (30–60 sek)
> *"Vi strukturerar vår testsvit strikt efter testpyramiden för att optimera både feedbackhastighet och driftsäkerhet. Basen utgörs av rena enhetstester med Mockito – de körs på millisekunder och täcker alla förgreningar i affärslogiken. För HTTP-kontrakt och validering använder vi `@WebMvcTest`-slices med MockMvc utan att blanda in databasen. För den översta toppen kör vi ett fåtal end-to-end integrationstester med `@SpringBootTest` och Testcontainers mot en riktig PostgreSQL. Det ger maximal kodkvalitet med en byggtid under minuten."*

**Labb & Kod:** [OrderServiceUnitTest.java](file:///backend/06-testing-pyramid/src/test/java/com/vault/testing/OrderServiceUnitTest.java) (Unit) vs [OrderControllerSliceTest.java](file:///backend/06-testing-pyramid/src/test/java/com/vault/testing/OrderControllerSliceTest.java) (Slice) vs [PostgresTestcontainersIntegrationTest.java](file:///backend/06-testing-pyramid/src/test/java/com/vault/testing/PostgresTestcontainersIntegrationTest.java) (Integration)

---

## 2. Felsökningsguide (Kommandon & Verktyg)

### A. Upptäck kontextomstarter i Spring Test Loggar
Leta efter rader som indikerar att en ny kontext skapas under körning:
```bash
mvn test | grep "Initializing Spring TestContext"
```
Om detta sker mer än 1–2 gånger under en testkörning har du problem med testcache-invalidering orsakad av olika `@MockBean`-konfigurationer!

### B. Kör isolerade tester snabbt
```bash
# Kör enbart blixtsnabba enhetstester (utan container)
mvn test -Dtest=*UnitTest

# Kör integrationstester mot Testcontainers
mvn test -Dtest=*IntegrationTest
```

---

## 3. Koden & Verifiering

* **The Flaw:** [FlawedH2AssumptionsTest.java](file:///backend/06-testing-pyramid/src/test/java/com/vault/testing/FlawedH2AssumptionsTest.java) – Visar hur H2-antaganden fallerar mot Postgres-dialekt.
* **The Fix (Pyramidens 3 nivåer):**
  * **Nivå 1 (Unit):** [OrderServiceUnitTest.java](file:///backend/06-testing-pyramid/src/test/java/com/vault/testing/OrderServiceUnitTest.java) – Ren Mockito-test utan Spring-overhead.
  * **Nivå 2 (Slice):** [OrderControllerSliceTest.java](file:///backend/06-testing-pyramid/src/test/java/com/vault/testing/OrderControllerSliceTest.java) – WebMvc Slice test med MockMvc.
  * **Nivå 3 (Integration):** [PostgresTestcontainersIntegrationTest.java](file:///backend/06-testing-pyramid/src/test/java/com/vault/testing/PostgresTestcontainersIntegrationTest.java) – Riktig PostgreSQL i Testcontainers.

---

## 4. Så här kör du testerna

Kör testsviten med Maven:
```bash
cd backend/06-testing-pyramid
mvn clean test
```
* **Vad testet bevisar:**
  * Visar hur enhetstester med Mockito körs blixtsnabbt på millisekunder.
  * Visar hur `@WebMvcTest` validerar HTTP-status, parametervalidering och responsstrukturer utan att starta databasen.
  * Kör integrationstestet mot PostgreSQL (om Docker är igång) och bevisar dialekttrohet.
