# 🧠 03-jvm-memory-gc: OOM, Heap Dumps, GC-pauser & K8s Limits

Denna modul täcker 4 fundamentala minnes- och Garbage Collection-utmaningar i molnbaserade JVM-applikationer. Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 1: JVM Heap Minnesläckor & Dominatorträd i MAT

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I Java uppstår minnesläckor inte genom oallokerat C-minne, utan genom **oavsiktliga starka referenser** som hålls vid liv av en aktiv *GC Root* (t.ex. statiska listor, applikationskontexter, glömda event listeners eller cacher utan storlekstak).

Allteftersom nya objekt skapas fylls Young och Old Generation. Garbage Collector kämpar förgäves för att rensa objekten tills JVM:en kraschar med `java.lang.OutOfMemoryError: Java heap space`.

#### 2. Keyword Bingo (Facit-lingo)
* **Heap Memory Leak:** När oanvända objekt inte kan återvinnas av Garbage Collector eftersom starka referenser kvarstår från en aktiv GC Root.
* **Shallow Size vs Retained Size:**
  * *Shallow Size:* Minnet som själva objektet upptar i sig.
  * *Retained Size:* Det totala minnet som skulle frigöras om detta objekt och alla dess underliggande referenser togs bort (nyckeln för att hitta roten till läckan!).
* **Dominator Tree:** En analysgraf i Eclipse MAT som rangordnar objekt efter hur mycket minne de dominerar och förhindrar från att samlas in.
* **Heap Dump (`.hprof`):** En komplett binär ögonblicksbild av alla allokerade objekt och referensträd vid kraschögonblicket.
* **Bounded LRU Cache:** Lösningen på obegränsade minnessamlingar – en cache som sätter ett absolut tak och evicerar äldsta poster vid full kapacitet.

#### 3. Verklighets-svar (30–60 sek)
> *"En minnesläcka i Java innebär att oanvända objekt inte kan Garbage Collectas för att de fortfarande hålls av en aktiv referens – oftast en statisk samling eller en cache utan eviction policy. För att identifiera läckan kör vi alltid med flaggan `-XX:+HeapDumpOnOutOfMemoryError`. Vid en krasch öppnar vi `.hprof`-filen i Eclipse Memory Analyzer (MAT) och inspekterar Dominator Tree. Vi tittar inte på Shallow Size utan på Retained Size för att omedelbart identifiera vilket specifikt objekt eller array som håller fast merparten av heapen."*

**Labb & Kod:** [MemoryLeakSimulation.java](file:///backend/03-jvm-memory-gc/src/main/java/com/vault/memory/MemoryLeakSimulation.java) vs [BoundedMemoryCache.java](file:///backend/03-jvm-memory-gc/src/main/java/com/vault/memory/BoundedMemoryCache.java)

---

### Scenario 2: K8s cgroups OOMKilled (Exit Code 137) vs Off-Heap / Native Memory

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I Kubernetes dör containrar plötsligt med status **`OOMKilled`** och **`Exit Code: 137`** utan att Spring Boot hinner skriva ett enda felmeddelande eller stacktrace i loggarna.

Detta beror ofta på att utvecklare sätter Kubernetes container memory limit till t.ex. `1Gi` och startar JVM med `-Xmx1g`. Men JVM allokerar inte bara heap: den allokerar även **Off-Heap / Native Memory** (Metaspace för klassmetadata, trådstackar med ~1MB per tråd, Direct ByteBuffers för nätverks-I/O och JIT-kompilering). När hela OS-processens minne passerar cgroup-gränsen på 1Gi skickar Linux kernel cgroups ett skoningslöst `SIGKILL (137)`.

#### 2. Keyword Bingo (Facit-lingo)
* **Exit Code 137 / OOMKilled:** Linux kernel cgroups avslutar containern omedelbart med `SIGKILL` när cgroup `memory.max` överskrids.
* **Off-Heap / Native Memory:** Minne som JVM allokerar utanför Java-heapen.
* **Metaspace:** Native memory-området där klassmetadata lagras (ersatte PermGen i Java 8).
* **Thread Stack Size (`-Xss`):** Minnet reserverat för metodanrop och lokala variabler per tråd (default ~1MB per OS-tråd).
* **`-XX:MaxRAMPercentage=75.0`:** Det rekommenderade sättet att konfigurera containrar. JVM läser cgroup-gränsen dynamiskt och sparar 25% av minnet till Metaspace, trådstackar och OS.

#### 3. Verklighets-svar (30–60 sek)
> *"Om en podd dör med Exit Code 137 har Linux OOM-killer skjutit ner den för att cgroup-gränsen överskreds. Ett vanligt misstag är att sätta container limit till 1GB och `-Xmx1g`. JVM:ens totala minne är Heap PLUS Metaspace, trådstackar (1MB per tråd) och native buffers. Lösningen är att undvika hårdkodade `-Xmx` och istället använda container-awareness via `-XX:MaxRAMPercentage=75.0`. Då reserveras 25% av containergränsen till off-heap och OS, vilket förhindrar att Linux cgroups dödar processen."*

**Labb & Kod:** [Dockerfile](file:///backend/03-jvm-memory-gc/Dockerfile) & [k8s-oom-demo.yaml](file:///backend/03-jvm-memory-gc/k8s-oom-demo.yaml)

---

### Scenario 3: GC Thrashing & Stop-The-World (STW) Pauser

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Innan applikationen kraschar med OOM inträffar ofta en dramatisk prestandaförsämring: Garbage Collector kör oavbrutet och försöker panikartat städa minnet.

Eftersom minnet är fullt med levande objekt lyckas inte GC:n frigöra något. Alla applikationstrådar pausas upprepade gånger (**Stop-The-World**), svarstider spikar till tiotals sekunder, och till slut kastar JVM felet:
`java.lang.OutOfMemoryError: GC overhead limit exceeded` (när mer än 98% av CPU-tiden går till GC men mindre än 2% minne frigörs).

#### 2. Keyword Bingo (Facit-lingo)
* **Stop-The-World (STW):** Pauser där alla applikationstrådar fryses medan GC flyttar och markerar objekt.
* **GC Thrashing:** Tillstånd där JVM spenderar nästan 100% av all CPU på att köra Garbage Collection utan framgång.
* **`GC overhead limit exceeded`:** JVM:s inbyggda skyddsmekanism som kraschar appen för att förhindra att maskinen fastnar i en evig STW-frysning.
* **P99 / Tail Latency Spikes:** Extrema fördröjningar i svarstider orsakade av STW-pauser.
* **Generational Garbage Collection:** Uppdelningen i Young Generation (korta livslängder) och Old Generation (långlivade objekt).

#### 3. Verklighets-svar (30–60 sek)
> *"När en JVM närmar sig sitt minnestak inträffar GC Thrashing. Garbage Collectorn tvingas köra Full GC upprepade gånger, vilket skapar långa Stop-The-World-pauser där alla anrop hänger och P99-latensen exploderar. Om JVM:en märker att den spenderar över 98% av tiden på GC men återvinner mindre än 2% av heapen kastar den `GC overhead limit exceeded`. Vi övervakar detta via JVM-metriker i Prometheus för att larma på GC-paustider långt innan kraschen sker."*

**Labb & Kod:** [demo-local-oom.sh](file:///backend/03-jvm-memory-gc/demo-local-oom.sh)

---

### Scenario 4: "The Dead Pod Dilemma" & Automatiserad Crash Dump Pipeline

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
När en container dör i Kubernetes på grund av OOM är det för sent att köra `kubectl exec` eller `kubectl cp`. Kubelet vägrar köra kommandon med felet:
`error: cannot exec into a container in a completed pod; current phase is Failed`.

Tekniker står maktlösa utan tillgång till kraschdumpen eftersom containerns lokala filsystem inte längre är åtkomligt.

#### 2. Keyword Bingo (Facit-lingo)
* **The Dead Pod Limitation:** Kubelets princip att `exec` och `cp` enbart är tillåtna på containrar i tillståndet `Running`.
* **Automated Crash Dump Pipeline:** Automatisk strömning av dumpen direkt till Object Storage (S3 / GCS / Azure Blob) vid krasch.
* **PersistentVolumeClaim (PVC):** Montering av delad nätverkslagring på `/dumps` så att data överlever poddens död.
* **Sidecar Container:** En hjälpcontainer som övervakar dump-volymen och larmar/laddar upp filen.
* **Ephemeral Debug Containers:** Kubernetes-funktion (`kubectl debug`) för att fästa en felsökningscontainer vid noder eller poddar.

#### 3. Verklighets-svar (30–60 sek)
> *"I Kubernetes kan du inte köra `kubectl cp` på en död podd eftersom `cp` kräver en körande container och `tar`-binären. I produktion förlitar vi oss därför aldrig på manuella kommandon. Istället bygger vi en automatiserad dump-pipeline: vi sätter `-XX:+HeapDumpOnOutOfMemoryError` till en volym, och ett startskript eller en sidecar-container laddar omedelbart upp `.hprof`-filen till en S3-bucket så fort processen avslutas. Podden kan då starta om direkt medan teamet analyserar dumpen från S3."*

**Labb & Kod:** [Dockerfile](file:///backend/03-jvm-memory-gc/Dockerfile) & Avsnitt 7 nedan

---

## 2. Felsökningsguide (Kommandon & Verktyg)

### A. Diagnostisera OOMKilled i Kubernetes
```bash
# Kontrollera om podden dog av OOMKilled och exit code
kubectl get pods
kubectl describe pod <pod-name> | grep -E "(OOMKilled|Exit Code: 137|Last State)"

# Kontrollera cgroup limits för den körande containern
kubectl exec -it <pod-name> -- cat /sys/fs/cgroup/memory.max
```

### B. Tvinga och extrahera Heap Dump med `jcmd`
```bash
# Identifiera PID
jcmd

# Skapa manuell heap dump i körande container
jcmd <PID> GC.heap_dump /tmp/heapdump.hprof

# Kopiera ut filen för analys i Eclipse MAT eller VisualVM
kubectl cp <pod-name>:/tmp/heapdump.hprof ./heapdump.hprof
```

### C. JVM-flaggor för Dockerfile
```dockerfile
ENTRYPOINT ["java", \
  "-XX:InitialRAMPercentage=75.0", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-XX:HeapDumpPath=/var/log/dumps/oom.hprof", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "app.jar"]
```

---

## 3. Koden & Verifiering

* **The Flaw:** [MemoryLeakSimulation.java](file:///backend/03-jvm-memory-gc/src/main/java/com/vault/memory/MemoryLeakSimulation.java) – Obegränsad statisk cache som samlar data utan TTL eller storleksgräns.
* **The Fix:** [BoundedMemoryCache.java](file:///backend/03-jvm-memory-gc/src/main/java/com/vault/memory/BoundedMemoryCache.java) – Bounded LRU-cache som evicerar äldsta poster och sätter tak för minnesanvändning.
* **Verification Test:** [MemoryVerificationTest.java](file:///backend/03-jvm-memory-gc/src/test/java/com/vault/memory/MemoryVerificationTest.java) – Bevisar att minnet hålls konstant trots tusentals inmatningar.

---

## 4. Så här testar du lokalt (Ingen Kubernetes krävs)

Hela modulen kan köras och verifieras lokalt på din maskin:

### A. Kör enhetstesterna med Maven
```bash
cd backend/03-jvm-memory-gc
mvn clean test
```
* **Vad testet bevisar:** 
  * `testTheFlaw_unboundedGrowth`: Visar hur objekt ackumuleras i minnet och aldrig släpps för GC.
  * `testTheFix_boundedCacheEvictsEldest`: Visar hur LRU-cachen klipper referenserna till gamla poster så att minnesanvändningen hålls konstant under taket (50 poster).

### B. Framkalla en riktig OutOfMemoryError & skapa en Heap Dump lokalt
Kör det färdiga demonstrationsskriptet:
```bash
cd backend/03-jvm-memory-gc
./demo-local-oom.sh
```

Skriptet startar [MemoryLeakSimulation](file:///backend/03-jvm-memory-gc/src/main/java/com/vault/memory/MemoryLeakSimulation.java) med begränsad heap (`-Xmx32m`) och flaggan `-XX:+HeapDumpOnOutOfMemoryError`. Inom 2 sekunder får du en färdig `oom.hprof` i mappen som du kan öppna och inspektera i t.ex. **Eclipse Memory Analyzer (MAT)** eller **VisualVM**!

---

## 5. Så här testar du i Kubernetes (Minikube / Kind / Docker Desktop)

Om du har ett lokalt Kubernetes-kluster finns en färdig [Dockerfile](file:///backend/03-jvm-memory-gc/Dockerfile) och manifestet [k8s-oom-demo.yaml](file:///backend/03-jvm-memory-gc/k8s-oom-demo.yaml).

### Steg 1: Bygg container-imagen
```bash
cd backend/03-jvm-memory-gc
docker build -t jvm-memory-leak:1.0 .
```

### Steg 2: Applicera Kubernetes-manifesten
```bash
kubectl apply -f k8s-oom-demo.yaml
```

Detta startar två poddar med olika konfiguration:
1. **`oomkilled-pod` (The Flaw):** Har container limit på 64Mi men JVM startas med `-Xmx128m`.
2. **`heapdump-pod` (The Fix):** Har container limit på 128Mi med `-XX:MaxRAMPercentage=50.0` och monterad volym för dumpen.

### Steg 3: Verifiera OOMKilled & Exit Code 137 på `oomkilled-pod`
```bash
kubectl get pod oomkilled-pod
kubectl describe pod oomkilled-pod | grep -E "(State|Reason|Exit Code)"
```
Förväntat utfall: `Exit Code: 137` / `Reason: OOMKilled`.

### Steg 4: Verifiera och extrahera Heap Dump från `heapdump-pod`
```bash
kubectl logs heapdump-pod
kubectl cp heapdump-pod:/dumps/oom.hprof ./oom.hprof
ls -lh ./oom.hprof
```

### Steg 5: Städa upp
```bash
kubectl delete -f k8s-oom-demo.yaml
```

---

## 6. Senior Intervju-djupdykning: Tre produktionsmönster för Crash Dumps

I en riktig produktionsmiljö körs aldrig manuella `kubectl cp`-kommandon mot kraschade poddar. Istället används följande etablerade mönster:

### Mönster 1: Automatisk uppladdning till Object Storage (S3 / GCS / Azure Blob) – *Bästa praxis*
Containern körs med ett startskript som fångar upp kraschen eller en sidecar-process som övervakar dump-katalogen:
```bash
java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps/oom.hprof -jar app.jar || true

if [ -f /dumps/oom.hprof ]; then
    echo "Upptäckte heap dump! Laddar upp till S3..."
    aws s3 cp /dumps/oom.hprof "s3://prod-heapdumps/service-$(hostname)-$(date +%s).hprof"
fi
```
* **Fördel:** Podden startar om omedelbart utan att blockeras. Utvecklarna får ett larm i Slack med en länk direkt till S3-objektet.

### Mönster 2: Delad PersistentVolumeClaim (PVC)
* Montera en delad nätverksvolym (t.ex. AWS EFS eller NFS med `ReadWriteMany`) på `/dumps`.
* När applikationspodden dör ligger dumpen kvar säkert på nätverkslagringen och kan läsas av en separat felsökningspod.

### Mönster 3: Keep-Alive Sleep Wrapper (För lokala labb & felsökning)
* Om Java kraschar fångas exit-koden upp och skriptet kör `sleep 3600`. Då förblir containern `Running`, och `kubectl cp` fungerar för manuell extrahering.
