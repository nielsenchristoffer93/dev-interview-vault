# 🧵 02-concurrency-threads: Race Conditions & Spring Singleton State

## 1. Problem: Vad kraschar eller varför bryr vi oss?
Spring Beans är som standard **Singletons** (en instans per Spring ApplicationContext). Webblager som Spring MVC (Tomcat) hanterar varje inkommande HTTP-request på en separat tråd från en trådpool (`http-nio-8080-exec-*`).

Om en utvecklare sparar föränderligt tillstånd (t.ex. `private int orderCounter` eller `private Map<String, Object> cache = new HashMap<>()`) som ett fält i en `@Service` eller `@Controller`, kommer hundratals trådar manipulera samma minnesadress samtidigt. Detta leder till:
* **Lost Updates:** `counter++` är inte atomärt utan består av 3 CPU-instruktioner: *Read, Modify, Write*.
* **Memory Visibility Issues:** Värden cachas i CPU L1/L2-cachar utan *Happens-Before*-garantier.
* **Datakorruption & Deadlocks:** Vanliga `HashMap` kan vid samtidig `put` hamna i oändliga loopar eller kasta `ConcurrentModificationException`.

---

## 2. Keyword Bingo (Facit-lingo)
* **Spring Singleton Scope:** Default livscykel för Spring beans – delas mellan alla samtidiga trådar i JVM:en.
* **Race Condition:** När programmets utfall beror på den icke-deterministiska ordningen trådar schemaläggs av operativsystemet.
* **Non-Atomic Read-Modify-Write:** Operationen är sammansatt och kan avbrytas mitt i av en trådkontextväxling.
* **Java Memory Model (JMM) & Happens-Before:** Reglerna som definierar när en tråds skrivning till minnet blir synlig för en annan tråd.
* **Compare-And-Swap (CAS):** Hårdvaruinstruktion (lock-free) som ligger till grund för `AtomicInteger`, `AtomicLong` och `AtomicReference`.
* **Thread Confinement / Stateless Services:** Den gyllene regeln i modern backend-design: Håll beans 100% tillståndslösa eller skicka tillstånd som metodargument (`Thread-local` / Stack-allokerat).

---

## 3. Verklighets-svar (30–60 sek)
> *"I Spring Boot är beans som standard singletons, medan Tomcat servar varje request i en egen tråd. Har du muterbara fält i en service delar alla trådar på det tillståndet. En enkel operation som `count++` är i själva verket tre operationer: läs, modifiera, skriv. Under hög last skriver trådar över varandras uppdateringar och data försvinner. Den bästa lösningen är att hålla services helt tillståndslösa och låta all data flöda via metodparametrar och returvärden. Om man måste räkna i minnet använder man atomära datastrukturer med CAS som `AtomicLong` eller `ConcurrentHashMap`."*

---

## 4. Felsökningsguide (Kommandon & Verktyg)

### A. Identifiera delat tillstånd vid kodgranskning (Static Analysis / Grep)
Leta efter fält i Spring-klasser som inte är `final` och inte är injectade beroenden:
```bash
# Hitta icke-final fält i Services/Controllers
git grep -E "private [A-Za-z0-9<>]+ [a-z0-9]+ = " src/main/java/
```

### B. Stress-testa med Concurrency Tools (JMeter / k6 / Hey)
Kör 50 parallella trådar och verifiera om slutsumman stämmer med antalet anrop:
```bash
hey -n 1000 -c 50 http://localhost:8080/api/orders/increment
```

---

## 5. Koden & Verifiering

* **The Flaw:** [UnsafeOrderService.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/UnsafeOrderService.java) – Använder primitiv `int` i singleton-läge; tappar räkningar under parallell last.
* **The Fix:** [SafeOrderService.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/SafeOrderService.java) – Använder `AtomicLong` med lock-free CAS-principer.
* **Verification Test:** [ConcurrencyVerificationTest.java](file:///backend/02-concurrency-threads/src/test/java/com/vault/concurrency/ConcurrencyVerificationTest.java) – Kör 100 parallella trådar med `CountDownLatch` och demonstrerar diskrepansen.
