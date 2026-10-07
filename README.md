# 🏛️ dev-interview-vault

> **Pragmatiskt referens- och labbrepo för tekniska intervjuer och djupgående fullstack/cloud-arkitektur.**  
> Fokus: Återskapa felet i kod (*The Flaw*), förstå varför det smäller, formulera svaret med intervju-lingo (*Keyword Bingo*), och verifiera fixen med tester (*The Fix*).

---

## 🧭 Arkitektur & Filosofi: "The 3-File Pattern"

Varje labbmodul är strikt isolerad och byggd kring samma trestegsmönster:

| Fil / Komponent | Syfte & Innehåll |
| :--- | :--- |
| **`README.md`** | **Scenario-baserad struktur:** Modulen är indelad i konkreta produktionsscenarier. Varje scenario har sitt eget **Problem (Vad kraschar / varför bryr vi oss)**, sitt specifika **Keyword Bingo (Facit-lingo)** och ett **Verklighets-svar (30–60 sek)** skräddarsytt för intervjuer, följt av **Felsökningsguide (CLI)**, **Körguide** och **Senior Intervju-djupdykning**. |
| **The Flaw (`*.java`, `*.tsx`, `*.yaml`...)** | Minimal kodbas som **avsiktligt fallerar**, läcker minne, skapar kapplöpningstillstånd eller bryter nätverkskontrakt. |
| **The Fix / Verification (`*Test.java`, `*spec.ts`...)** | Automatiserat test (JUnit 5, Testcontainers, Vitest) eller åtgärdad konfiguration som **bevisar** att problemet är löst och att fixen håller under last. |

---

## 🗂️ Modulkarta & Snabbreferens

### ☕ 1. Backend (Java / Spring Boot / JVM)
| Modul | Fokusområde | Nyckelbegrepp (Keyword Bingo) |
| :--- | :--- | :--- |
| [01-spring-resilience](file:///backend/01-spring-resilience/README.md) | Resilience4j, Circuit Breakers, Retries & Timeouts | Cascading Failure, Thread Pool Starvation, TimeLimiter, Exponential Backoff & Jitter, Fallback Degradation, Port 8081 Management Isolation |
| [02-concurrency-threads](file:///backend/02-concurrency-threads/README.md) | Race conditions, ThreadPools, Deadlocks, Virtual Threads | Spring Singleton Scope, Read-Modify-Write, CAS, Lock Ordering Inversion, `computeIfAbsent`, ThreadLocal Leaks, Carrier Pinning, `CallerRunsPolicy`, Semaphore, BlockingQueue |
| [03-jvm-memory-gc](file:///backend/03-jvm-memory-gc/README.md) | OOM, Heap dumps, GC-pauser & K8s limits | Dominator Tree (MAT), Retained Size, OOMKilled (Exit Code 137), Linux cgroups, Metaspace, GC Thrashing (STW), Dead Pod Limitation, S3 Crash Dump Pipeline |
| [04-jpa-performance](file:///backend/04-jpa-performance/README.md) | N+1 queries, EntityGraph, Locking & Batching | N+1 Problem, `JOIN FETCH`, `@EntityGraph`, Cartesian Product Problem, `default_batch_fetch_size`, Lost Updates, Optimistic (`@Version`) vs Pessimistic Locking (`SELECT FOR UPDATE`) |
| [05-maven-dependency-tree](file:///backend/05-maven-dependency-tree/README.md) | Jar Hell, transitiva beroenden & Enforcer | Nearest-Definition-Wins, `NoSuchMethodError`, Classpath Shadowing, `<dependencyManagement>`, Bill of Materials (BOM), `<exclusions>`, `maven-enforcer-plugin` |
| [06-testing-pyramid](file:///backend/06-testing-pyramid/README.md) | Mockito vs `@WebMvcTest` vs Testcontainers | Testing Pyramid, H2-illusionen, Production Parity, Testcontainers (PostgreSQL), ApplicationContext Cache Pollution, `@DirtiesContext`, Mockito vs WebMvc Slices |

---

### ⚛️ 2. Frontend (React / Vue / Web Standards)
| Modul | Fokusområde | Nyckelbegrepp (Keyword Bingo) |
| :--- | :--- | :--- |
| [01-tanstack-vs-store](file:///frontend/01-tanstack-vs-store/README.md) | Server State vs Client UI State | Stale Data, Request Deduplication, Stale-While-Revalidate (SWR), Window Focus Refetching, Query Key Invalidation, Zustand/Pinia separation |
| [02-rendering-performance](file:///frontend/02-rendering-performance/README.md) | Onödiga re-renders, Memo & Virtualisering | Input Lag / UI Jank, Parent Re-render Cascade, Referential Equality, `React.memo`, `useCallback`, `useMemo`, DOM Node Thrashing, `@tanstack/react-virtual` |
| [03-auth-session-flow](file:///frontend/03-auth-session-flow/README.md) | BFF-mönster, Cookie-flaggor & XSS vs CSRF | XSS Exfiltration i `localStorage`, Supply Chain Attacks, `HttpOnly` + `Secure` + `SameSite=Lax`, Backend-for-Frontend (BFF), Refresh Token Rotation |
| [04-optimistic-updates](file:///frontend/04-optimistic-updates/README.md) | TanStack Query mutation & rollback | Perceived Latency, Spinner UX Elimination, Ghost Updates, Snapshot Rollback (`context.previousData`), `cancelQueries`, `onSettled` Invalidation |

---

### ☁️ 3. Cloud & Kubernetes
| Modul | Fokusområde | Nyckelbegrepp (Keyword Bingo) |
| :--- | :--- | :--- |
| [01-k8s-pod-to-ingress](file:///cloud-and-k8s/01-k8s-pod-to-ingress/README.md) | Ingress -> Service -> Pod port forwarding | TargetPort vs Port, Selector Mismatch, `Endpoints: <none>`, 502 Bad Gateway vs 503 Service Unavailable, Named Ports, ClusterIP routing |
| [02-k8s-probes-actuator](file:///cloud-and-k8s/02-k8s-probes-actuator/README.md) | Liveness, Readiness & Management Port | Kubernetes Death Spiral, Cascading Restart, Liveness vs Readiness vs Startup Probes, Port 8081 Isolation, Graceful Shutdown (`preStop` hook) |
| [03-aws-event-driven](file:///cloud-and-k8s/03-aws-event-driven/README.md) | EventBridge -> SQS -> Dead-Letter Queue | Poison Pill, Head-of-Line Blocking, Dead-Letter Queue (DLQ), Redrive Policy (`maxReceiveCount`), Visibility Timeout, Fan-out, At-Least-Once Delivery |
| [04-terraform-state-locks](file:///cloud-and-k8s/04-terraform-state-locks/README.md) | S3 Remote Backend & DynamoDB Locking | State File Corruption, Orphaned Resources, DynamoDB `LockID`, `Error acquiring state lock`, `terraform force-unlock`, S3 SSE-KMS & Versioning |

---

### 📐 4. System Design
| Modul | Fokusområde | Nyckelbegrepp (Keyword Bingo) |
| :--- | :--- | :--- |
| [idempotency-patterns](file:///system-design/idempotency-patterns/README.md) | Idempotens & distribuerad låsning | Idempotency Key (UUID), Double-Charging, At-Least-Once Delivery, Redis `SET NX EX`, Result Caching, Distributed Locks, Fencing Tokens |
| [outbox-pattern](file:///system-design/outbox-pattern/README.md) | Transactional Outbox & Debezium CDC | The Dual-Write Problem, 2PC Anti-Pattern, Local ACID Atomicity, Change Data Capture (CDC), PostgreSQL WAL, Debezium, `SKIP LOCKED` Worker |
| [rate-limiting](file:///system-design/rate-limiting/README.md) | Token Bucket vs Leaky Bucket | Boundary Burst Vulnerability, Fixed Window Counter, Token Bucket (Burstiness), Leaky Bucket (Traffic Shaping), Sliding Window, Redis Lua Script, 429 Too Many Requests |

---

## 🚀 Snabbstart & Lab-infrastruktur

### ⚡ Allt-i-ett installationsskript (Linux / Bash)
För att snabbt verifiera och installera alla förutsättningar (`curl`, `jq`, `kubectl`, `minikube`, `k9s`), konfigurera Docker-rättigheter och starta alla Compose-containrar i ett enda steg:

```bash
./setup-prerequisites.sh

# Valfri flagga:
# --with-minikube : Startar även Minikube automatiskt med Ingress-addon
```

---

### Manuell start av delar

### 1. Bas-infrastruktur (PostgreSQL, Redis, LocalStack)
```bash
docker-compose -f compose/docker-compose.base.yml up -d
```
* **PostgreSQL:** `localhost:5432` (db: `interview_db`, user: `vault_user`, pass: `vault_pass`)
* **Redis:** `localhost:6379`
* **LocalStack:** `localhost:4566` (Emulerar AWS S3, DynamoDB, SQS, EventBridge)

### 2. Monitoring (Prometheus & Grafana)
```bash
docker-compose -f compose/docker-compose.monitoring.yml up -d
```
* **Prometheus:** `http://localhost:9090`
* **Grafana:** `http://localhost:3000` (`admin` / `admin`)

### 3. Lokalt Kubernetes-kluster (Valfritt – för K8s-labs)
För att köra laborationerna i `cloud-and-k8s/` samt OOM-simuleringen i `backend/03-jvm-memory-gc`:

* **Alternativ A: Minikube (Rekommenderat för labb)**
  ```bash
  minikube start --driver=docker
  minikube addons enable ingress
  ```
* **Alternativ B: Kind (Kubernetes in Docker)**
  ```bash
  kind create cluster --name dev-vault
  ```

---

## ☕ Köra Backend & Spring Boot-applikationer

### 1. Köra alla tester över hela backend (Aggregator)
Kör alla 6 moduler och deras automatiserade verifieringstester i ett enda svep:
```bash
cd backend
mvn test
```

### 2. Köra tester för en specifik modul
```bash
# Exempel: Testa alla 8 concurrency-koncept (10 automatiserade tester):
cd backend/02-concurrency-threads
mvn test

# Exempel: Testa JPA Performance (N+1 queries & optimistisk låsning):
cd backend/04-jpa-performance
mvn test
```

### 3. Starta en körande Spring Boot-applikation lokalt
Två moduler är fullt körbara Spring Boot 3-applikationer redo för HTTP-trafik och stress-tester:

1. **Spring Resilience (`backend/01-spring-resilience`):**
   ```bash
   cd backend/01-spring-resilience
   mvn spring-boot:run
   ```
   * **Kundtrafik HTTP:** `http://localhost:8080/api/payment/safe?tx=ORD-1`
   * **Kretsbrytare & Fail-Fast:** `http://localhost:8080/api/payment/fail-fast?tx=TX-1`
   * **Actuator Health & Metrics:** `http://localhost:8081/actuator/health`

2. **Concurrency & Thread Safety (`backend/02-concurrency-threads`):**
   ```bash
   cd backend/02-concurrency-threads
   mvn spring-boot:run
   ```
   * **Unsafe Counter (Lost Updates):** `http://localhost:8080/api/orders/unsafe/increment`
   * **Safe Atomic Counter:** `http://localhost:8080/api/orders/safe/increment`

---

## 🤖 Interaktiva Live-demonstrationsskript (Bash)

Flera moduler har färdiga interaktiva demonstrationsskript med färgkodade utskrifter:

* **Kretsbrytarens livscykel:** `backend/01-spring-resilience/demo-circuit-breaker.sh`
* **Thread Pool Starvation & Port-isolering:** `backend/01-spring-resilience/demo-thread-starvation.sh`
* **Race Conditions & Lost Updates:** `backend/02-concurrency-threads/demo-race-condition.sh`
* **Lokal OutOfMemoryError & Heap Dump:** `backend/03-jvm-memory-gc/demo-local-oom.sh`
* **AWS SQS Poison Pill & DLQ:** `cloud-and-k8s/03-aws-event-driven/verify-dlq.sh`
* **Terraform State Lock-konflikt:** `cloud-and-k8s/04-terraform-state-locks/test-state-lock.sh`

---

## 🎯 Så här använder du repot inför intervjun

1. **Välj ett område:** Klicka in i önskad modul i tabellen ovan.
2. **Kör "The Flaw":** Se testet fallera eller granska varför felaktig kod/konfiguration smäller i produktion.
3. **Läs "Keyword Bingo" & "Verklighets-svaret":** Öva på att svara högt på 45 sekunder med rätt facktermer.
4. **Kör "The Fix":** Kör verifieringstestet och se att det blir grönt.
