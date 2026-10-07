# 🧵 02-concurrency-threads: Concurrency, Thread Safety & Multithreading Patterns

Denna modul täcker 8 fundamentala multithreading-utmaningar i modern Java/Spring backend-utveckling. Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 0: Spring Singleton State & Race Conditions (Lost Updates)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Spring Beans är som standard **Singletons** (en instans per Spring `ApplicationContext`). Webblager som Spring MVC (Tomcat) hanterar varje inkommande HTTP-request på en separat tråd (`http-nio-8080-exec-*`). Om en utvecklare sparar föränderligt tillstånd (t.ex. `private int orderCounter`) som ett fält i en `@Service` eller `@Controller`, manipuleras samma minnesadress av hundratals trådar samtidigt.

Operationen `counter++` är en icke-atomär *Read-Modify-Write* som bryts ned i tre separata CPU-instruktioner. Under last sker *Lost Updates* där samtidiga skrivningar försvinner, samt minnessynlighetsproblem utan Happens-Before-garantier.

#### 2. Keyword Bingo (Facit-lingo)
* **Spring Singleton Scope:** Default livscykel för Spring beans – delas mellan alla samtidiga trådar i JVM:en.
* **Race Condition:** När programmets utfall beror på den icke-deterministiska ordningen trådar schemaläggs av operativsystemet.
* **Non-Atomic Read-Modify-Write:** Operationen är sammansatt och avbryts mitt i av en trådkontextväxling.
* **Java Memory Model (JMM) & Happens-Before:** Reglerna som definierar när en tråds skrivning till minnet blir synlig för en annan tråd.
* **Compare-And-Swap (CAS):** Hårdvaruinstruktion (lock-free) som ligger till grund för `AtomicInteger`, `AtomicLong` och `AtomicReference`.
* **Thread Confinement / Stateless Services:** Gyllene regeln i modern backend-design: Håll beans 100% tillståndslösa eller skicka tillstånd som metodargument (`Thread-local` / Stack-allokerat).

#### 3. Verklighets-svar (30–60 sek)
> *"I Spring Boot är beans som standard singletons, medan Tomcat servar varje request i en egen tråd. Har du muterbara fält i en service delar alla trådar på det tillståndet. En enkel operation som `count++` är i själva verket tre operationer: läs, modifiera, skriv. Under hög last skriver trådar över varandras uppdateringar och data försvinner. Den bästa lösningen är att hålla services helt tillståndslösa och låta all data flöda via metodparametrar och returvärden. Om man måste räkna i minnet använder man atomära datastrukturer med CAS som `AtomicLong` eller `ConcurrentHashMap`."*

**Labb & Kod:** [UnsafeOrderService.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/UnsafeOrderService.java) vs [SafeOrderService.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/SafeOrderService.java)

---

### Scenario 1: Deadlocks (Dödlägen) & Lock Ordering Inversion

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
När två eller flera trådar försöker förvärva samma uppsättning lås i omvänd ordning (t.ex. Tråd 1 tar Lås A och väntar på B; Tråd 2 tar Lås B och väntar på A) uppstår ett cykliskt beroende (*Circular Wait*).

Ingen av trådarna kan fortsätta och de blockeras för evigt. Hela applikationen eller berörda endpoints fryser och trådpoolen dräneras utan att fel loggas.

#### 2. Keyword Bingo (Facit-lingo)
* **Deadlock (Dödläge):** Två eller flera trådar blockerar varandra permanent genom cirkulärt låsberoende.
* **Lock Ordering Inversion:** Den vanligaste orsaken till dödlägen – inkonsistent ordning vid förvärv av flera lås.
* **Circular Wait (Coffman-villkor):** Ett av de fyra villkoren för dödläge där varje tråd håller ett lås som nästa behöver.
* **Global Lock Ordering:** Arkitekturmönster där lås alltid tas i en strikt deterministisk ordning (t.ex. sorterat på entitets-ID eller minnesadress).
* **`ReentrantLock.tryLock()`:** Icke-blockerande låsförvärv med timeout som möjliggör graceful back-off istället för oändlig blockering.

#### 3. Verklighets-svar (30–60 sek)
> *"Deadlocks uppstår när trådar tar lås i omvänd ordning och hamnar i ett cirkulärt beroende där båda väntar på varandra för evigt. Det primära försvaret är Global Lock Ordering – vi definierar en deterministisk låsordning, exempelvis genom att alltid sortera låsen baserat på ID innan vi låser dem. Om vi använder explicita lås föredrar vi `ReentrantLock.tryLock(timeout)`. Om låset inte kan tas inom utsatt tid släpper tråden alla sina tidigare lås, backar undan och försöker igen, vilket eliminerar risken för permanenta dödlägen."*

**Labb & Kod:** [DeadlockLab.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/DeadlockLab.java)

---

### Scenario 2: ConcurrentHashMap Check-then-Act (Cache Stampede)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Att använda `ConcurrentHashMap` garanterar enbart att de enskilda metodanropen (`containsKey`, `get`, `put`) är atomära. Om man kombinerar anropen i ett `if (!cache.containsKey(key)) { cache.put(key, expensiveDbCall(key)); }` skapas en klassisk *Check-then-Act Race Condition*.

Under samtidig belastning ser 50 trådar samtidigt att nyckeln saknas och kör alla det tunga databasanropet parallellt (**Cache Stampede**).

#### 2. Keyword Bingo (Facit-lingo)
* **Check-then-Act:** Race condition där ett beslut tas baserat på ett tillstånd som hinner ändras innan den efterföljande åtgärden utförs.
* **Cache Stampede / Thundering Herd:** Många samtidiga trådar beräknar eller hämtar samma saknade cache-värde samtidigt.
* **Metodsäkerhet vs Sammansatt Atomicitet:** Förståelsen att sammansatta anrop på en trådsäker samling inte är atomära som helhet.
* **`computeIfAbsent()`:** Atomär metod i `ConcurrentHashMap` som garanterar att beräkningsfunktionen körs exakt en gång per nyckel via bucket-nivålåsning.
* **Bucket-level / Node Synchronization:** Hur `ConcurrentHashMap` finfördelar låsningen på enskilda tabellnoder snarare än hela samlingen.

#### 3. Verklighets-svar (30–60 sek)
> *"En vanlig fälla är att tro att `ConcurrentHashMap` gör all kod trådsäker. Om du kontrollerar `containsKey` och sedan gör en `put` är det två separata operationer. Vid hög samtidighet kliver flera trådar in i if-blocket samtidigt och gör samma dyra databasanrop parallellt – en så kallad Cache Stampede. Lösningen är att använda atomära sammansatta metoder, främst `map.computeIfAbsent(key, k -> loadFromDb(k))`. Den låser enbart den specifika noden i hashmakrostrukturen och garanterar att beräkningen sker exakt en gång."*

**Labb & Kod:** [ConcurrentHashMapLab.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/ConcurrentHashMapLab.java)

---

### Scenario 3: ThreadLocal Context Leaks & Minnesläckor

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Webbcontainrar som Tomcat återanvänder trådar från en trådpool (`http-nio-*`). Om en utvecklare lagrar request-kontext (såsom `TenantContext` eller säkerhetstokens) i en `ThreadLocal` och misslyckas med att rensa den, lever värdet kvar när tråden återgår till poolen.

Nästa request från en helt annan kund som tilldelas samma tråd kommer då att läsa föregående kunds data (**Cross-Tenant Data Leak**). Dessutom förhindrar trådens interna tabell Garbage Collection av de lagrade objekten (**Minnesläcka**).

#### 2. Keyword Bingo (Facit-lingo)
* **ThreadLocal:** Trådisolerad lagring där varje tråd har sin egen oberoende kopia av variabeln.
* **Thread Pool Worker Reuse:** Trådar dör inte vid request-slut utan återanvänds för nya orelaterade förfrågningar.
* **Cross-Tenant Data Leak:** Allvarlig säkerhetsincident där konfidentiell kunddata läcker över till en annan användare på samma worker-tråd.
* **ThreadLocalMap & WeakReference:** Intern mekanism i `ThreadLocal` som ändå kan hålla starka referenser till värden om de inte rensas.
* **`finally { remove(); }` / AutoCloseable:** Det obligatoriska livscykelmönstret för att garantera upprensning oavsett om undantag inträffar.

#### 3. Verklighets-svar (30–60 sek)
> *"Eftersom webbservern återanvänder trådar från en trådpool dör inte en tråd när ett HTTP-request är klart. Om vi sätter användar- eller tenant-kontext i en `ThreadLocal` och inte rensar den, kommer nästa request på samma tråd att ärva den gamla identiteten – en allvarlig säkerhetsrisk och potentiell minnesläcka. Försvaret är att alltid kapsla in `ThreadLocal`-användning i ett `try ... finally { threadLocal.remove(); }` eller använda ett `AutoCloseable`-mönster så att rensning alltid garanteras."*

**Labb & Kod:** [ThreadLocalLab.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/ThreadLocalLab.java)

---

### Scenario 4: Java 21 Virtual Thread Pinning

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I Java 21 (Project Loom) körs miljontals Virtual Threads ovanpå ett litet antal underliggande OS-trådar (*Carrier Threads*). Vid blockerande I/O (nätverk/databas) monteras en virtuell tråd normalt av från sin carrier.

Men om den blockerande operationen sker inuti ett **`synchronized`**-block eller anropar ett native-metodanrop kan JVM:en inte avmontera den. Tråden blir "pinned" vid carrier-tråden, vilket blockerar hela OS-tråden och eliminerar all skalbarhetsvinst med virtuella trådar.

#### 2. Keyword Bingo (Facit-lingo)
* **Virtual Threads (Project Loom):** Lättviktstrådar hanterade av JVM istället för operativsystemet.
* **Carrier Threads:** Den underliggande ForkJoinPool av OS-trådar som schemalägger virtuella trådar.
* **Carrier Thread Pinning:** När en virtuell tråd inte kan monteras av vid blockering och låser sin carrier-tråd.
* **`synchronized` Monitor Pinning:** Den främsta orsaken till pinning i befintlig Java-kod.
* **`ReentrantLock`:** Den moderna ersättaren för `synchronized` i Java 21 som inte orsakar pinning vid I/O-väntan.
* **`-Djdk.tracePinnedThreads=full`:** JVM-flaggan som avslöjar exakt var i koden pinning uppstår under körning.

#### 3. Verklighets-svar (30–60 sek)
> *"Virtual Threads i Java 21 ger enorm genomströmning för I/O-intensiva appar genom att avmonteras från sina carrier-trådar vid blockering. Men om en tråd blockerar inuti ett `synchronized`-block blir den 'pinned' – carrier-tråden låses och kan inte betjäna andra virtuella trådar. Lösningen i modern Java 21 är att byta ut `synchronized` mot `ReentrantLock`, som stöder avmontering fullt ut, samt att övervaka applikationen med `-Djdk.tracePinnedThreads=full` för att fånga pinning under prestandatester."*

**Labb & Kod:** [VirtualThreadPinningLab.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/VirtualThreadPinningLab.java)

---

### Scenario 5: ThreadPoolExecutor & Unbounded Queue OOM

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Standardmetoden `Executors.newFixedThreadPool(n)` använder en obegränsad `LinkedBlockingQueue` (`Integer.MAX_VALUE`). Om inflödet av uppgifter tillfälligt överstiger arbetstrådarnas kapacitet buffras miljontals uppgifter i minnet tills JVM:en kraschar med `OutOfMemoryError: Java heap space`.

`Executors.newCachedThreadPool()` lider av motsatt fel: en obegränsad poolstorlek som skapar nya trådar obegränsat tills operativsystemet nekar nya trådar med `OutOfMemoryError: unable to create native thread`.

#### 2. Keyword Bingo (Facit-lingo)
* **`ThreadPoolExecutor`:** Kärnklassen i Java för konfigurerbar trådpoolshantering.
* **Unbounded Queue (`LinkedBlockingQueue`):** Kö utan maxgräns som döljer kapacitetsproblem tills minnet tar slut.
* **Bounded Queue (`ArrayBlockingQueue`):** Kö med fast kapacitet som sätter ett absolut tak för minnesanvändning.
* **Backpressure (Mottryck):** Mekanism som tvingar producenten att sakta ner när konsumenten inte hinner med.
* **`CallerRunsPolicy`:** Avvisningspolicy där den anropande tråden själv tvingas köra jobbet vid full kö, vilket ger naturligt mottryck.
* **`AbortPolicy`:** Standardpolicy som kastar `RejectedExecutionException` vid mättnad.

#### 3. Verklighets-svar (30–60 sek)
> *"Fabriksmetoderna i `Executors`, såsom `newFixedThreadPool`, är anti-mönster i produktion eftersom de använder en obegränsad kö. Vid en trafikspik växer kön tills minnet tar slut och JVM:en kraschar med OOM. I stället konfigurerar vi alltid en explicit `ThreadPoolExecutor` med en bunden kö, t.ex. `ArrayBlockingQueue(100)`, och sätter en `CallerRunsPolicy`. Då uppnår vi naturlig backpressure: när poolen är full tvingas anroparen själv exekvera uppgiften, vilket automatiskt saktar ner inkommande trafik och skyddar minnet."*

**Labb & Kod:** [ThreadPoolLab.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/ThreadPoolLab.java)

---

### Scenario 6: Synchronizers (Semaphore vs CyclicBarrier vs CountDownLatch)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I komplexa mikrotjänster måste vi samordna trådar: antingen begränsa hur många som samtidigt anropar en skör extern integration (undvika att sänka partnern med rate limits), samla ett antal beräkningstrådar innan vi går vidare till nästa analysfas, eller vänta på att en uppsättning bakgrundsjobb slutförts.

Att göra detta med manuella flaggor eller sömnloopar leder till race conditions och CPU-slöseri.

#### 2. Keyword Bingo (Facit-lingo)
* **`Semaphore(permits)`:** Samtidighetsbegränsare (rate limiter / kvothanterare) med `acquire()` och `release()`.
* **`CountDownLatch(count)`:** Engångsräknare där trådar väntar via `await()` tills N händelser signalerat `countDown()`.
* **`CyclicBarrier(parties)`:** Återanvändbar barriär där N samarbetande trådar möts i gemensamma faser via `await()`.
* **Throttling / Concurrency Limit:** Skydd mot överbelastning av externa resurser.
* **One-shot vs Reusable:** Skillnaden i livscykel mellan CountDownLatch (engångs) och CyclicBarrier (återanvändbar i cykler).

#### 3. Verklighets-svar (30–60 sek)
> *"De tre synchronizerna löser olika samordningsproblem: En `Semaphore` begränsar hur många trådar som får göra något samtidigt – perfekt för att throttla anrop mot en extern legacy-partner till exempelvis max 5 samtidiga anrop. En `CountDownLatch` är en engångsräknare där en tråd väntar på att ett antal uppgifter ska bli klara innan den går vidare. En `CyclicBarrier` samlar istället en grupp samarbetande trådar vid en gemensam mötesplats; när alla nått barriären släpps de vidare till nästa fas, och barriären återställs automatiskt för nästa runda."*

**Labb & Kod:** [SynchronizationPrimitivesLab.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/SynchronizationPrimitivesLab.java)

---

### Scenario 7: CompletableFuture Non-blocking Async Pipelines

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
När en tjänst måste aggregera data från flera mikrotjänster (t.ex. Användardata, Kreditbetyg och Rekommendationer) innebär sekventiella anrop att latensen adderas (200ms + 300ms + 150ms = 650ms).

Att köra trådar med traditionell `Future.get()` blockerar anropstråden under väntetiden, vilket dränerar trådpoolen. Om dessutom ett anrop fallerar riskerar hela aggregeringen att krascha med 500 Internal Server Error om felhantering saknas.

#### 2. Keyword Bingo (Facit-lingo)
* **`CompletableFuture`:** Javas moderna monadiska abstraktion för asynkron och icke-blockerande programmering.
* **`supplyAsync()`:** Startar asynkron uppgift på angiven trådpool.
* **`thenCombine()`:** Slår samman resultaten från två oberoende parallella futures utan att blockera.
* **Dedicated Executor vs ForkJoinPool:** Best practice att styra I/O-anrop till en egen pool för att inte svälta Javas delade `commonPool`.
* **`.exceptionally()` Fallback:** Mjuk feldegradering som returnerar ett standardvärde vid fel istället för att avbryta pipelinen.

#### 3. Verklighets-svar (30–60 sek)
> *"För att minimera latens kör vi oberoende hämtningar parallellt med `CompletableFuture.supplyAsync()` på en dedikerad trådpool för att inte blockera Tomcats trådar eller svälta Common ForkJoinPool. Vi slår samman resultaten icke-blockerande med `thenCombine()`. För att säkerställa hög tillgänglighet lägger vi på `.exceptionally()` som fångar fel mjukt och returnerar en degraderad fallback, så att ett externt avbrott i rekommendationstjänsten inte sänker hela användarsidan."*

**Labb & Kod:** [AsyncPipelineLab.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/AsyncPipelineLab.java)

---

### Scenario 8: Producer-Consumer med BlockingQueue

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Att koppla samman producenter och konsumenter med manuella monitorlås (`synchronized`, `wait()` och `notifyAll()`) är extremt riskabelt. Utvecklare missar ofta att `wait()` måste ligga i en `while`-loop för att hantera *spurious wakeups*, eller råkar använda `notify()` istället för `notifyAll()` vilket leder till *missed signals* och permanenta trådhängningar.

Dessutom saknas inbyggt stöd för timeouts och kontrollerad backpressure.

#### 2. Keyword Bingo (Facit-lingo)
* **Producer-Consumer Pattern:** Avkopplat mönster där producenter skapar jobb i en kö och konsumenter bearbetar dem asynkront.
* **`BlockingQueue` (`ArrayBlockingQueue`):** Trådsäker kö med inbyggd låsning och villkorsvariabler.
* **`offer()` & `poll()` med Timeout:** Icke-blockerande eller tidsbegränsade anrop som förhindrar att trådar fastnar för evigt.
* **Spurious Wakeups:** Fenomenet där en tråd kan vakna från `wait()` utan att villkoret uppfyllts.
* **Natural Backpressure:** När kön är full blockeras eller nekas producenten automatiskt tills konsumenten hunnit ikapp.

#### 3. Verklighets-svar (30–60 sek)
> *"Vi implementerar aldrig Producer-Consumer med lågnivå `wait()` och `notify()` eftersom det kräver manuella monitorlås och är extremt sårbart för 'missed signals' och 'spurious wakeups'. Istället använder vi alltid `java.util.concurrent.BlockingQueue`, oftast `ArrayBlockingQueue`. Metoderna `offer(timeout)` och `poll(timeout)` hanterar all intern synkronisering, sätter ett absolut tak för minnet och skapar en naturlig backpressure där producenter automatiskt bromsas när konsumenterna är överbelastade."*

**Labb & Kod:** [ProducerConsumerLab.java](file:///backend/02-concurrency-threads/src/main/java/com/vault/concurrency/ProducerConsumerLab.java)

---

## 2. Felsökningsguide (Kommandon & Verktyg)

### A. Identifiera delat tillstånd vid kodgranskning (Static Analysis / Grep)
Leta efter fält i Spring-klasser som inte är `final` och inte är injectade beroenden:
```bash
# Hitta icke-final fält i Services/Controllers
git grep -E "private [A-Za-z0-9<>]+ [a-z0-9]+ = " src/main/java/
```

### B. Stress-testa med Concurrency Tools (Hey / k6 / JMeter)
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

## 3. Koden & Verifiering (9 Concurrency-labbmodeller)

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

## 4. Så här kör du testerna och live-demonstrationen

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

## 5. Senior Intervju-djupdykning: 8 Klassiska Concurrency-frågor

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
