# 📐 06-testing-pyramid: Mockito vs @WebMvcTest vs Testcontainers

## 1. Problem: Vad kraschar eller varför bryr vi oss?
Två vanliga anti-mönster i enterprise-projekt:
1. **H2 In-Memory Illusionen:** Tester körs mot H2 i tron att det motsvarar PostgreSQL. H2 tolererar ogiltig syntax eller ignorerar Postgres-specifika datatyper (`JSONB`, `CITEXT`, window functions). Testerna är 100% gröna i CI, men vid driftsättning i produktion smäller det med `PSQLException`.
2. **Bräckliga & Långsamma `@SpringBootTest`:** Utvecklare sätter `@SpringBootTest` på minsta lilla affärslogiktest och sprider ut `@MockBean` slumpmässigt. Varje unik `@MockBean`-kombination **smutsar ner Spring ApplicationContext cache**, vilket tvingar Spring att starta om hela applikationskontexten tiotals gånger. Byggtider exploderar från 40 sekunder till 18 minuter.

---

## 2. Keyword Bingo (Facit-lingo)
* **Testing Pyramid (Testpyramiden):**
  * **Bas (Unit Tests):** Ren Java + Mockito. Exekveras på mikrosekunder. Validerar komplex affärslogik isolerat.
  * **Mitten (Slice Tests):** `@WebMvcTest` (enbart webblagret, mockar services) eller `@DataJpaTest` (enbart persistence-lagret).
  * **Topp (Integration Tests):** `@SpringBootTest` i kombination med **Testcontainers** mot en verklig produktionslik instans (t.ex. PostgreSQL/Kafka).
* **ApplicationContext Caching:** Spring återanvänder samma kontext mellan testklasser så länge konfigurationen är identisk.
* **`@DirtiesContext` Straffet:** Indikerar att kontexten har muterats och måste destrueras och återskapas, vilket kostar sekunder per test.
* **Testcontainers:** Docker-integration i JUnit 5 som spinner upp riktiga databaser/köer i containrar och garanterar dialekt-trohet utan delat fläckat tillstånd mellan byggen.

---

## 3. Verklighets-svar (30–60 sek)
> *"Vi strukturerar testerna strikt efter testpyramiden för att maximera både exekveringshastighet och tillförlitlighet. Ren affärslogik testas i enhetstester med Mockito – de startar på millisekunder och mockar bort alla I/O-beroenden. För HTTP-kontrakt och serialisering använder vi `@WebMvcTest` som bara laddar webblagret utan att starta databasen. För databastester skippar vi H2 helt på grund av dialektskillnader och kör istället Testcontainers med en riktig PostgreSQL-container. För att hålla byggtiderna korta återanvänder vi samma kontext och undviker `@DirtiesContext` och varierande `@MockBean`."*

---

## 4. Felsökningsguide (Kommandon & Verktyg)

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

## 5. Koden & Verifiering

* **The Flaw:** [FlawedH2Assumptions.java](file:///backend/06-testing-pyramid/src/test/java/com/vault/testing/FlawedH2AssumptionsTest.java) – Visar hur H2-antaganden fallerar mot Postgres-dialekt.
* **The Fix (Pyramidens 3 nivåer):**
  * **Nivå 1 (Unit):** [OrderServiceUnitTest.java](file:///backend/06-testing-pyramid/src/test/java/com/vault/testing/OrderServiceUnitTest.java) – Ren Mockito test utan Spring overhead.
  * **Nivå 2 (Slice):** [OrderControllerSliceTest.java](file:///backend/06-testing-pyramid/src/test/java/com/vault/testing/OrderControllerSliceTest.java) – WebMvc Slice test.
  * **Nivå 3 (Integration):** [PostgresTestcontainersIntegrationTest.java](file:///backend/06-testing-pyramid/src/test/java/com/vault/testing/PostgresTestcontainersIntegrationTest.java) – Riktig PostgreSQL i Testcontainers.
