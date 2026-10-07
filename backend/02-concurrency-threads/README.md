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
* **Deadlock (Dödläge):** När två trådar blockerar varandra för evigt genom cykliskt låsberoende (Circular Wait).
* **Check-then-Act Race Condition:** När separata atomära anrop (`containsKey` + `put`) utförs icke-atomärt och leder till Cache Stampede.
* **ThreadLocal Leak:** När data inte rensas i en trådpool och läcker över till nästa request på samma worker-tråd.
* **Carrier Thread Pinning:** När en Virtual Thread (Java 21) blockerar i ett `synchronized`-block och låser den underliggande OS-tråden.

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
Kör 50 parallella anrop och verifiera om slutsumman stämmer med antalet anrop:
```bash
# 1. Stressa det oskyddade singleton-fältet (tappar uppdateringar!):
hey -n 1000 -c 50 http://localhost:8080/api/orders/unsafe/increment

# 2. Kontrollera räknaren:
curl http://localhost:8080/api/orders/unsafe/count
# Utfall: Räknaren visar ~850-950 istället för 1000 (Lost Updates)!

# 3. Stressa den atomära lösningen (håller 100%):
hey -n 1000 -c 50 http://localhost:8080/api/orders/safe/increment
curl http://localhost:8080/api/orders/safe/count
# Utfall: Räknaren visar exakt 1000!
```

### C. Inspektera och hitta Deadlocks med `jcmd`
Om trådar har hängt sig tar du en tråddump:
```bash
jcmd <PID> Thread.print | grep -A 20 "Found one Java-level deadlock"
```

---

## 5. Koden & Verifiering (5 Concurrency-labbmodeller)

* **Topic 0 (Lost Updates & CAS):**
  * The Flaw: [UnsafeOrderService.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/UnsafeOrderService.java) – Primitiv `int` i singleton-läge; tappar räkningar.
  * The Fix: [SafeOrderService.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/SafeOrderService.java) – Lock-free `AtomicLong` med CAS.
* **Topic 1 (Deadlocks & Lock Ordering):**
  * Lab & Fix: [DeadlockLab.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/DeadlockLab.java) – Inverterade monitorlås vs Global Lock Ordering och `ReentrantLock.tryLock()`.
* **Topic 2 (ConcurrentHashMap Check-then-act):**
  * Lab & Fix: [ConcurrentHashMapLab.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/ConcurrentHashMapLab.java) – `containsKey` + `put` (Cache Stampede) vs atomär `computeIfAbsent()`.
* **Topic 3 (ThreadLocal Context Leaks):**
  * Lab & Fix: [ThreadLocalLab.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/ThreadLocalLab.java) – Förorening av worker threads i trådpool vs `try-with-resources / finally { remove(); }`.
* **Topic 4 (Virtual Thread Pinning):**
  * Lab & Fix: [VirtualThreadPinningLab.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/VirtualThreadPinningLab.java) – `synchronized` (Carrier Pinning) vs `ReentrantLock`.
* **Spring Boot REST App:**
  * [OrderController.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/OrderController.java) & [ConcurrencyApplication.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/ConcurrencyApplication.java).
* **Verification Test:**
  * [ConcurrencyVerificationTest.java](file:///backend/02-concurrency-threads/src/test/java/com/vault/concurrency/ConcurrencyVerificationTest.java) – Full testsvit med 6 automatiserade enhetstester.

---

## 6. Så här kör du testerna och live-demonstrationen

### A. Kör enhetstesterna i JVM:en (Inget behov av server)
Kör hela sviten (alla 5 ämnen) med Maven:
```bash
cd backend/02-concurrency-threads
mvn clean test
```
* **Vad testet bevisar:**  
  * Visar att `UnsafeOrderService` tappar >17 000 updates under 50 000 anrop.
  * Visar att `UnsafeCache` kör DB-anropet 20 gånger parallellt, medan `computeIfAbsent` kör det exakt 1 gång.
  * Visar hur en efterföljande användare i en trådpool ärver föregående användares identitet om `remove()` glöms.
  * Visar hur `tryLock` ger upp efter timeout istället för att fastna i deadlock.

---

### B. Starta Spring Boot-servern lokalt
För att kunna stress-testa över faktiska HTTP-requests startar du servern:
```bash
cd backend/02-concurrency-threads
mvn spring-boot:run
```
Servern startar på `http://localhost:8080`.

---

### C. Kör det färdiga demoskriptet (`demo-race-condition.sh`)
I en separat terminal kör du det interaktiva skriptet:
```bash
cd backend/02-concurrency-threads
./demo-race-condition.sh
```

---

## 7. Senior Intervju-djupdykning: 4 Klassiska Concurrency-frågor

### 1. Hur uppstår Deadlocks och hur förhindrar du dem?
* **Orsak (Lock Ordering Inversion):** Tråd 1 tar Lås A och väntar på B. Tråd 2 tar Lås B och väntar på A. Båda fryser för evigt.
* **Lösning A (Global Lock Ordering):** Sortera alltid låsen (t.ex. efter databas-ID eller `System.identityHashCode`). Båda trådarna tvingas då alltid ta A först och B därefter.
* **Lösning B (`ReentrantLock.tryLock`):** Ange en timeout: `lock.tryLock(500, TimeUnit.MILLISECONDS)`. Om låset inte kan tas, släpp alla egna lås och backa undan.

### 2. Varför skyddar inte `ConcurrentHashMap` mot alla race conditions?
* `ConcurrentHashMap` garanterar enbart att enskilda metodanrop (`get`, `put`) är trådsäkra.
* Om du skriver:
  ```java
  if (!map.containsKey(key)) {
      map.put(key, expensiveDbCall(key)); // CHECK-THEN-ACT FÄLLA!
  }
  ```
  kan 50 trådar se att nyckeln saknas och göra `expensiveDbCall` samtidigt (**Cache Stampede**).  
  **Rätt lösning:** `map.computeIfAbsent(key, k -> expensiveDbCall(k))`, som låser på bucket-/nod-nivå och garanterar exakt 1 exekvering.

### 3. Hur leder `ThreadLocal` till säkerhetshål och minnesläckor i Tomcat?
* Tomcat återanvänder trådar från en trådpool.
* Om du sätter `TenantContext.setCurrentTenant("kund-A")` och glömmer `remove()`, kommer nästa inkommande request från "kund-B" som tilldelas samma tråd att läsa "kund-A"s data!
* Dessutom kan objekt som hålls av trådens interna tabell inte Garbage Collectas -> **Minnesläcka**.
* **Regel:** Alltid använda `try ... finally { context.remove(); }` eller AutoCloseable try-with-resources.

### 4. Vad är "Carrier Thread Pinning" i Java 21 Virtual Threads?
* Virtual Threads körs ovanpå ett fåtal OS-trådar (*Carrier Threads*). Vid blockerande I/O (nätverk/databas) monteras de normalt av.
* **Men:** Om tråden blockerar inuti ett **`synchronized`**-block blir den "pinned" vid sin carrier-tråd och blockerar hela OS-tråden.
* **Lösning:** Använd **`ReentrantLock`** istället för `synchronized` i modern Java 21-kod.


