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
* **Bounded Queue & Backpressure:** Begränsad köstorlek (`ArrayBlockingQueue`) och `CallerRunsPolicy` som bromsar inkommande producenter istället för OOM (`newFixedThreadPool` med unbounded queue).
* **Synchronizers (Semaphore vs Barrier vs Latch):** `Semaphore` för samtidiga kvoter/throttling, `CyclicBarrier` för synkroniserade faser mellan trådar, och `CountDownLatch` för engångs-nedräkning.
* **CompletableFuture Async Pipelines:** Asynkrona pipelines utan trådblockering (`supplyAsync`, `thenCombine`) och feltolerans med `.exceptionally()`.
* **Producer-Consumer Pattern:** Avkopplad arbetsfördelning via `BlockingQueue` (`put`/`take` / `offer`/`poll`) utan behov av felbenägen `wait()`/`notify()`.


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

## 5. Koden & Verifiering (9 Concurrency-labbmodeller)

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
* **Topic 5 (ThreadPoolExecutor & Backpressure):**
  * Lab & Fix: [ThreadPoolLab.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/ThreadPoolLab.java) – Unbounded queue OOM-risk vs Bounded Queue med `CallerRunsPolicy` (backpressure) och `AbortPolicy`.
* **Topic 6 (Synchronizers: Semaphore & CyclicBarrier):**
  * Lab & Fix: [SynchronizationPrimitivesLab.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/SynchronizationPrimitivesLab.java) – `Semaphore` för samtidighetsbegränsning & `CyclicBarrier` för synkroniserade trådfaser.
* **Topic 7 (CompletableFuture Async Pipelines):**
  * Lab & Fix: [AsyncPipelineLab.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/AsyncPipelineLab.java) – Icke-blockerande `supplyAsync`, `thenCombine` och felsäker `.exceptionally()` fallback.
* **Topic 8 (Producer-Consumer med BlockingQueue):**
  * Lab & Fix: [ProducerConsumerLab.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/ProducerConsumerLab.java) – `ArrayBlockingQueue` med naturlig backpressure vs lågnivå `wait/notify`.
* **Spring Boot REST App:**
  * [OrderController.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/OrderController.java) & [ConcurrencyApplication.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/ConcurrencyApplication.java).
* **Verification Test:**
  * [ConcurrencyVerificationTest.java](file:///backend/02-concurrency-threads/src/test/java/com/vault/concurrency/ConcurrencyVerificationTest.java) – Full testsvit med 10 automatiserade enhetstester som täcker alla 8 koncept.


---

## 6. Så här kör du testerna och live-demonstrationen

### A. Kör enhetstesterna i JVM:en (Inget behov av server)
Kör hela sviten (alla 8 ämnen, 10 tester) med Maven:
```bash
cd backend/02-concurrency-threads
mvn clean test
```
* **Vad testet bevisar:**  
  * Visar att `UnsafeOrderService` tappar >17 000 updates under 50 000 anrop.
  * Visar att `UnsafeCache` kör DB-anropet 20 gånger parallellt, medan `computeIfAbsent` kör det exakt 1 gång.
  * Visar hur en efterföljande användare i en trådpool ärver föregående användares identitet om `remove()` glöms.
  * Visar hur `tryLock` ger upp efter timeout istället för att fastna i deadlock.
  * Visar hur `CallerRunsPolicy` tvingar anropande tråd att exekvera jobb när kön är full (backpressure), och hur `AbortPolicy` kastar `RejectedExecutionException`.
  * Visar hur `Semaphore` strikt begränsar max samtidiga anrop och hur `CyclicBarrier` samlar N trådar per fas.
  * Visar hur `CompletableFuture` slår samman två asynkrona anrop och hanterar externa fel via fallback.
  * Visar hur `ArrayBlockingQueue` hanterar mättnad och returnerar `false` vid timeout utan att låsa applikationen.


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

## 7. Senior Intervju-djupdykning: 8 Klassiska Concurrency-frågor

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

### 5. Varför anses `Executors.newFixedThreadPool` vara ett anti-pattern i produktion?
* `Executors.newFixedThreadPool(n)` använder internt en `LinkedBlockingQueue` utan kapacitetstak (`Integer.MAX_VALUE`).
* Om inflödet av uppgifter tillfälligt överstiger arbetstrådarnas kapacitet växer kön i det oändliga tills JVM kraschar med `OutOfMemoryError: Java heap space`.
* `Executors.newCachedThreadPool()` har motsatt problem: obegränsat `maxPoolSize`, vilket skapar tusentals OS-trådar tills JVM kraschar med `OutOfMemoryError: unable to create native thread`.
* **Lösning:** Konfigurera alltid en explicit `ThreadPoolExecutor` med bunden kö (t.ex. `ArrayBlockingQueue(100)`), kontrollerad poolstorlek och en `CallerRunsPolicy` som ger naturlig **backpressure** genom att tvinga anroparen att utföra jobbet när kön är full.

### 6. Vad är skillnaden mellan `CountDownLatch`, `CyclicBarrier` och `Semaphore`?
* **`Semaphore(permits)`:** Begränsar samtidighet (rate limiting / kvothantering). Trådar anropar `acquire()` och `release()`. Exempel: max 5 samtidiga anrop till ett externt legacy-API.
* **`CountDownLatch(N)`:** Engångsräknare. Trådar anropar `await()` och väntar tills N händelser har skett (`countDown()`). Kan inte nollställas eller återanvändas.
* **`CyclicBarrier(N)`:** Fas-koordinering. N trådar väntar på varandra via `await()`. När alla N trådar har anlänt till barriären släpps alla igenom samtidigt, och barriären återställs automatiskt för nästa fas.

### 7. Hur bygger man icke-blockerande pipelines med `CompletableFuture` och hanterar fel?
* Kör inte blockerande `get()` direkt på varje delsteg. Använd `supplyAsync()` för asynkrona anrop på en dedikerad trådpool (undvik delad `ForkJoinPool` för lång I/O).
* Komponera samman parallella delresultat med `thenCombine()`:
  ```java
  var userFuture = CompletableFuture.supplyAsync(() -> fetchUser(id), pool);
  var scoreFuture = CompletableFuture.supplyAsync(() -> fetchScore(id), pool);
  return userFuture.thenCombine(scoreFuture, (user, score) -> new Profile(user, score));
  ```
* Hantera fel och partiella avbrott med `.exceptionally(ex -> fallbackValue)` så att inte hela anropet havererar om en mikrotjänst felar.

### 8. Varför föredrar vi `BlockingQueue` framför `wait()` och `notify()` för Producer-Consumer?
* Lågnivå `wait()` och `notifyAll()` kräver manuell hantering av monitorlås, `synchronized`-block och loopar med villkorskontroll (`while (!ready)`). Det är lätt att drabbas av *missed signals*, *spurious wakeups* eller dödlägen.
* `BlockingQueue` (såsom `ArrayBlockingQueue`) kapslar in all synkronisering och låsning internt.
* Med `offer(item, timeout, unit)` och `poll(timeout, unit)` får man inbyggd trådsäkerhet, timeouts och backpressure på ett par rader läsbar kod.



