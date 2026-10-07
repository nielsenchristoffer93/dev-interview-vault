# 🧠 03-jvm-memory-gc: OOM, Heap Dumps, GC-pauser & K8s Limits

## 1. Problem: Vad kraschar eller varför bryr vi oss?
I Kubernetes dör containrar plötsligt med **Exit Code 137 (OOMKilled)** utan att Spring Boot hinner skriva ett enda felmeddelande i loggarna. Detta beror ofta på två distinkta orsaker:
1. **JVM Heap Minnesläcka:** Statiska samlingar eller event listeners som aldrig rensas fyller Young & Old Generation. Garbage Collector kör frenetiskt och skapar långa **Stop-The-World (STW)** pauser tills appen kraschar med `java.lang.OutOfMemoryError: Java heap space`.
2. **K8s Container vs JVM Memory Discrepancy:** JVM allokerar inte bara heap, utan även **Off-Heap/Native Memory** (Metaspace, trådstackar med 1MB per tråd, Direct ByteBuffers, JIT-kompilering). Om K8s limit är satt till `1Gi` och JVM startas med `-Xmx1g` kommer Linux cgroups OOM-killer skjuta ner processen med `SIGKILL (137)`.

---

## 2. Keyword Bingo (Facit-lingo)
* **Exit Code 137 / OOMKilled:** Linux kernel cgroups avslutar processen när cgroup `memory.max` överskrids.
* **Stop-The-World (STW):** Pauser där alla applikationstrådar fryses medan GC flyttar och markerar objekt.
* **GC Thrashing / Overhead Limit Exceeded:** När JVM spenderar >98% av tiden på GC men återvinner <2% av minnet (`java.lang.OutOfMemoryError: GC overhead limit exceeded`).
* **`-XX:MaxRAMPercentage=75.0`:** Det rekommenderade sättet att konfigurera JVM i containrar så att 25% sparas till OS-buffers, trådstackar och Metaspace.
* **Off-Heap / Metaspace:** Området där klassmetadata lagras. Till skillnad från gamla PermGen allokeras Metaspace i native memory.
* **Heap Dump (`.hprof`):** En ögonblicksbild av alla objekt i minnet vid kraschögonblicket.

---

## 3. Verklighets-svar (30–60 sek)
> *"Om en podd dör med Exit Code 137 har Linux OOM-killer skjutit ner den för att cgroup-gränsen överskreds. Ett vanligt misstag är att sätta container memory limit till 1GB och `-Xmx1g`. JVM:ens totala minne är Heap PLUS Metaspace, trådstackar (1MB per tråd) och native buffers. Lösningen är att använda container-awareness via `-XX:MaxRAMPercentage=75.0` och alltid slå på `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps/oom.hprof`. Då får vi en heap dump vid minnesläckor, och vi kan analysera dominatorträdet i Eclipse Memory Analyzer (MAT) för att hitta exakt vilket objekt eller cache som håller referenserna."*

---

## 4. Felsökningsguide (Kommandon & Verktyg)

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

## 5. Koden & Verifiering

* **The Flaw:** [MemoryLeakSimulation.java](file:///backend/03-jvm-memory-gc/src/main/java/com/vault/memory/MemoryLeakSimulation.java) – Obegränsad statisk cache som samlar data utan TTL eller storleksgräns.
* **The Fix:** [BoundedMemoryCache.java](file:///backend/03-jvm-memory-gc/src/main/java/com/vault/memory/BoundedMemoryCache.java) – Bounded LRU-cache som evicerar äldsta poster och sätter tak för minnesanvändning.
* **Verification Test:** [MemoryVerificationTest.java](file:///backend/03-jvm-memory-gc/src/test/java/com/vault/memory/MemoryVerificationTest.java) – Bevisar att minnet hålls konstant trots tusentals inmatningar.

---

## 6. Så här testar du (Ingen Kubernetes krävs!)

Du behöver **inget Kubernetes-kluster** för att köra och testa koden i denna modul. Kubernetes-avsnitten ovan beskriver hur du felsöker och resonerar kring detta i **produktionsmiljö och på tekniska intervjuer**.

Hela modulen kan köras och verifieras lokalt på din maskin:

### A. Kör enhetstesterna med Maven
```bash
cd backend/03-jvm-memory-gc
mvn clean test
```
* **Vad testet bevisar:** 
  * `testTheFlaw_unboundedGrowth`: Visar hur objekt ackumuleras i minnet och aldrig släpps för GC.
  * `testTheFix_boundedCacheEvictsEldest`: Visar hur LRU-cachen klipper referenserna till gamla poster så att minnesanvändningen hålls konstant under taket (50 poster).

---

### B. Framkalla en riktig OutOfMemoryError & skapa en Heap Dump lokalt
Vill du prova hur en riktig `OutOfMemoryError` ser ut och se en `.hprof`-fil skapas på disken lokalt utan Kubernetes?

Kör det färdiga skriptet:
```bash
cd backend/03-jvm-memory-gc
./demo-local-oom.sh
```

Skriptet startar [MemoryLeakSimulation](file:///backend/03-jvm-memory-gc/src/main/java/com/vault/memory/MemoryLeakSimulation.java) med begränsad heap (`-Xmx32m`) och flaggan `-XX:+HeapDumpOnOutOfMemoryError`. Inom 2 sekunder får du en färdig `oom.hprof` i mappen som du kan öppna och inspektera i t.ex. **Eclipse Memory Analyzer (MAT)** eller **VisualVM**!

---

## 7. Så här testar du i Kubernetes (Minikube, Kind eller Docker Desktop)

Om du har ett lokalt Kubernetes-kluster (t.ex. Minikube, Kind eller Docker Desktop K8s) och faktiskt vill testa `kubectl`-kommandona i praktiken, finns en färdig [Dockerfile](file:///backend/03-jvm-memory-gc/Dockerfile) och manifestet [k8s-oom-demo.yaml](file:///backend/03-jvm-memory-gc/k8s-oom-demo.yaml).

### Steg 1: Bygg container-imagen
Stå i `backend/03-jvm-memory-gc`:
```bash
docker build -t jvm-memory-leak:1.0 .

# Om du använder Kind, ladda in imagen i klustret:
# kind load docker-image jvm-memory-leak:1.0

# Om du använder Minikube, kör detta innan du bygger så imagen hamnar i minikubes docker:
# eval $(minikube docker-env)
```

### Steg 2: Applicera Kubernetes-manifesten
```bash
kubectl apply -f k8s-oom-demo.yaml
```

Detta startar två poddar med olika konfiguration:
1. **`oomkilled-pod` (The Flaw):** Har container limit på 64Mi men JVM startas med `-Xmx128m`.
2. **`heapdump-pod` (The Fix):** Har container limit på 128Mi med `-XX:MaxRAMPercentage=50.0` och monterad volym för dumpen.

---

### Steg 3: Verifiera OOMKilled & Exit Code 137 på `oomkilled-pod`
Efter några sekunder när minnet överskrider 64Mi skickar Linux cgroups `SIGKILL (137)`:
```bash
kubectl get pod oomkilled-pod
```
Output visar `OOMKilled` eller `Error`:
```
NAME             READY   STATUS      RESTARTS   AGE
oomkilled-pod    0/1     OOMKilled   0          15s
```

Inspektera poddens termineringsorsak:
```bash
kubectl describe pod oomkilled-pod | grep -E "(State|Reason|Exit Code)"
```
Förväntat utfall i K8s:
```text
State:          Terminated
  Reason:       OOMKilled
  Exit Code:    137
```
> **Slutsats:** Inga Java-loggar eller stacktraces hinner skrivas. Processen terminerades brutalt av Linux-kärnan.

---

### Steg 4: Verifiera och extrahera Heap Dump från `heapdump-pod`
I `heapdump-pod` klipper JVM:en minnet internt (`OutOfMemoryError`) innan Linux cgroups slår till:
```bash
# Se loggen där JVM själv dumpar minnet:
kubectl logs heapdump-pod
```
Output:
```text
Startar JVM med begränsad heap och automatisk Heap Dump...
java.lang.OutOfMemoryError: Java heap space
Dumping heap to /dumps/oom.hprof ...
Heap dump file created [...]
✅ OOM inträffade och dump sparades i /dumps/oom.hprof.
⏳ Håller containern vid liv så att 'kubectl cp' fungerar...
```

Ladda ner heap dumpen från poddens volym till din lokala maskin:
```bash
kubectl cp heapdump-pod:/dumps/oom.hprof ./oom.hprof

# Verifiera att filen laddades ner:
ls -lh ./oom.hprof
```

> [!NOTE]
> **Intervju-insikt (Varför podden måste hållas vid liv):**  
> `kubectl cp` kör `tar` under huven via `kubectl exec` inuti containern. Om containern tillåts dö direkt vid `OutOfMemoryError` hamnar podden i tillståndet `Failed`, vilket ger felet `cannot exec into a container in a completed pod`.  
> I testmanifestet håller ett `sleep 3600`-skal containern vid liv i `Running`-läge så att du hinner ladda ner dumpen. I produktion skickas dumpar istället automatiskt till S3 eller sparas på en delad `PersistentVolumeClaim` (PVC).

---

### Steg 5: Städa upp
```bash
kubectl delete -f k8s-oom-demo.yaml
```


