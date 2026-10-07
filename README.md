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
| [07-observability-distributed-tracing](file:///backend/07-observability-distributed-tracing/README.md) | OpenTelemetry, W3C Tracing & Prometheus | W3C Trace Context (`traceparent`), Distributed Waterfall, RED vs USE Method, High Cardinality OOM Trap, MDC ThreadLocal Leaks, TaskDecorator, Scoped Values |
| [08-contract-testing-and-versioning](file:///backend/08-contract-testing-and-versioning/README.md) | Consumer-Driven Contracts (Pact) & API-versionering | Consumer-Driven Contract Testing, Provider Verification, Pact Broker & `can-i-deploy`, Tolerant Reader Pattern, Breaking Change Detection, API Versioning Strategies, Deprecation Window & Sunset Header |

---

### ⚛️ 2. Frontend (React / Vue / Web Standards)
| Modul | Fokusområde | Nyckelbegrepp (Keyword Bingo) |
| :--- | :--- | :--- |
| [01-tanstack-vs-store](file:///frontend/01-tanstack-vs-store/README.md) | Server State vs Client UI State | Stale Data, Request Deduplication, Stale-While-Revalidate (SWR), Window Focus Refetching, Query Key Invalidation, Zustand/Pinia separation |
| [02-rendering-performance](file:///frontend/02-rendering-performance/README.md) | Onödiga re-renders, Memo & Virtualisering | Input Lag / UI Jank, Parent Re-render Cascade, Referential Equality, `React.memo`, `useCallback`, `useMemo`, DOM Node Thrashing, `@tanstack/react-virtual` |
| [03-auth-session-flow](file:///frontend/03-auth-session-flow/README.md) | BFF-mönster, Cookie-flaggor & XSS vs CSRF | XSS Exfiltration i `localStorage`, Supply Chain Attacks, `HttpOnly` + `Secure` + `SameSite=Lax`, Backend-for-Frontend (BFF), Refresh Token Rotation |
| [04-optimistic-updates](file:///frontend/04-optimistic-updates/README.md) | TanStack Query mutation & rollback | Perceived Latency, Spinner UX Elimination, Ghost Updates, Snapshot Rollback (`context.previousData`), `cancelQueries`, `onSettled` Invalidation |
| [05-typescript-type-safety](file:///frontend/05-typescript-type-safety/README.md) | Type Erasure, Zod Schemas & Branded Types | Type Erasure, Type Assertion (`as`), Parse Don't Validate, Zod Schema, Exhaustiveness Check (`never`), `assertNever`, Branded Types (Nominal Typing) |
| [06-core-web-vitals-and-error-boundaries](file:///frontend/06-core-web-vitals-and-error-boundaries/README.md) | Core Web Vitals (LCP, INP, CLS) & Error Boundaries | Error Boundary, `getDerivedStateFromError`, `componentDidCatch`, White Screen of Death (WSoD), CLS, Aspect-Ratio Reservation, INP, Long Tasks, `startTransition`, Time Slicing |

---

### 🗄️ 3. Database (PostgreSQL / SQL / Internals)
| Modul | Fokusområde | Nyckelbegrepp (Keyword Bingo) |
| :--- | :--- | :--- |
| [01-indexes-locks-and-plans](file:///database/01-indexes-locks-and-plans/README.md) | B-Tree Index, DDL Locks & Transaction Isolation | B-Tree Index, Leftmost Prefix Rule, Seq Scan vs Index Scan, Range Trap, `ACCESS EXCLUSIVE LOCK`, Lock Queue, `lock_timeout`, Write Skew, `SELECT FOR UPDATE`, SSI |
| [02-zero-downtime-migrations-and-replication](file:///database/02-zero-downtime-migrations-and-replication/README.md) | Expand/Contract Migrations & Read Replicas | Expand & Contract Pattern, Dual-Write, Batched Backfill, Replication Lag, Read-Your-Own-Writes, Sticky Primary Window, Keyset vs Offset Pagination |

---

### 🛡️ 4. Security & AppSec (Fullstack)
| Modul | Fokusområde | Nyckelbegrepp (Keyword Bingo) |
| :--- | :--- | :--- |
| [01-web-security-cors-csrf-xss](file:///security/01-web-security-cors-csrf-xss/README.md) | CORS, CSP, CSRF, Token Storage & SSRF | Same-Origin Policy (SOP), Preflight `OPTIONS`, Wildcard with Credentials, In-Memory Token + HttpOnly Refresh, Content Security Policy (CSP Nonces), AWS IMDSv2 SSRF |
| [02-auth-tokens-m2m-and-rotation](file:///security/02-auth-tokens-m2m-and-rotation/README.md) | M2M (Machine-to-Machine) & Refresh Token Rotation | OAuth2 Client Credentials Grant, M2M Scopes, Refresh Token Rotation (RTR), Token Reuse Detection, Token Family Invalidation, Asymmetric Signing (RS256 & JWKS), Key ID (`kid`) |

---

### ☁️ 5. Cloud & Kubernetes
| Modul | Fokusområde | Nyckelbegrepp (Keyword Bingo) |
| :--- | :--- | :--- |
| [01-k8s-pod-to-ingress](file:///cloud-and-k8s/01-k8s-pod-to-ingress/README.md) | Ingress -> Service -> Pod port forwarding | TargetPort vs Port, Selector Mismatch, `Endpoints: <none>`, 502 Bad Gateway vs 503 Service Unavailable, Named Ports, ClusterIP routing |
| [02-k8s-probes-actuator](file:///cloud-and-k8s/02-k8s-probes-actuator/README.md) | Liveness, Readiness & Management Port | Kubernetes Death Spiral, Cascading Restart, Liveness vs Readiness vs Startup Probes, Port 8081 Isolation, Graceful Shutdown (`preStop` hook) |
| [03-aws-event-driven](file:///cloud-and-k8s/03-aws-event-driven/README.md) | EventBridge -> SQS -> Dead-Letter Queue | Poison Pill, Head-of-Line Blocking, Dead-Letter Queue (DLQ), Redrive Policy (`maxReceiveCount`), Visibility Timeout, Fan-out, At-Least-Once Delivery |
| [04-terraform-state-locks](file:///cloud-and-k8s/04-terraform-state-locks/README.md) | S3 Remote Backend & DynamoDB Locking | State File Corruption, Orphaned Resources, DynamoDB `LockID`, `Error acquiring state lock`, `terraform force-unlock`, S3 SSE-KMS & Versioning |
| [05-zero-downtime-deployments-and-traffic](file:///cloud-and-k8s/05-zero-downtime-deployments-and-traffic/README.md) | Zero-Downtime Deployments & Traffic Draining | 502/504 Drop Window, RollingUpdate (`maxSurge` & `maxUnavailable`), Readiness Probe Barrier, `preStop` Sleep Hook, Graceful Connection Draining, SIGTERM vs SIGKILL |
| [06-k8s-network-policies-and-egress](file:///cloud-and-k8s/06-k8s-network-policies-and-egress/README.md) | NetworkPolicies & Egress Security (Zero-Trust K8s) | Default-Allow Trap, NetworkPolicy, Default-Deny Pattern, Lateral Movement, Micro-segmentation, CoreDNS Port 53 Egress Trap, CNI Enforcement (Cilium/Calico vs Flannel) |

---

### 📐 6. System Design (Distributed Systems & Messaging)
| Modul | Fokusområde | Nyckelbegrepp (Keyword Bingo) |
| :--- | :--- | :--- |
| [01-caching-thundering-herd](file:///system-design/01-caching-thundering-herd/README.md) | Cache Stampede, Eviction & Stale-While-Revalidate | Thundering Herd, Single-Flight / Mutex Locking, Cache-Aside Eviction, After-Commit Invalidation, `s-maxage`, `stale-while-revalidate`, ETag Conditional Requests |
| [02-idempotency-patterns](file:///system-design/02-idempotency-patterns/README.md) | Idempotens & distribuerad låsning | Idempotency Key (UUID), Double-Charging, At-Least-Once Delivery, Redis `SET NX EX`, Result Caching, Distributed Locks, Fencing Tokens |
| [03-outbox-pattern](file:///system-design/03-outbox-pattern/README.md) | Transactional Outbox & Debezium CDC | The Dual-Write Problem, 2PC Anti-Pattern, Local ACID Atomicity, Change Data Capture (CDC), PostgreSQL WAL, Debezium, `SKIP LOCKED` Worker |
| [04-rate-limiting](file:///system-design/04-rate-limiting/README.md) | Token Bucket vs Leaky Bucket | Boundary Burst Vulnerability, Fixed Window Counter, Token Bucket (Burstiness), Leaky Bucket (Traffic Shaping), Sliding Window, Redis Lua Script, 429 Too Many Requests |
| [05-rabbitmq-vs-kafka](file:///system-design/05-rabbitmq-vs-kafka/README.md) | RabbitMQ vs Kafka & Message Brokers | Competing Consumers, Out-of-Order Execution, Partition Key Hashing, Prefetch Count (`basic.qos`), Consumer Starvation, Poison Pill, Dead-Letter Exchange (DLX), DLT |
| [06-websockets-vs-sse](file:///system-design/06-websockets-vs-sse/README.md) | WebSockets vs SSE & Real-Time Clusters | Simplex vs Full-Duplex, `Last-Event-ID`, HTTP/2 Multiplexing, Redis Pub/Sub Backplane, Fan-out, Zombie Connections, ALB 60s Idle Timeout, Heartbeats |

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
Kör alla moduler och deras automatiserade verifieringstester i ett enda svep:
```bash
cd backend
mvn test
```

### 2. Köra tester för en specifik modul
```bash
# Exempel: Testa alla 8 concurrency-koncept (10 automatiserade tester):
cd backend/02-concurrency-threads
mvn test

# Exempel: Testa Consumer-Driven Contract Testing (Pact / Breaking Changes):
cd backend/08-contract-testing-and-versioning
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

## ⚛️ Köra Frontend & Vitest-tester

Alla 6 frontend-moduler körs med Vitest och har fullständig TypeScript-stöd:

```bash
cd frontend

# Installera beroenden
npm install

# Kör alla 20 verifieringstester över alla 6 moduler
npm test

# Validera TypeScript strikt kompilering
npm run typecheck
```

---

## 🛡️ Köra Security & Token-tester

```bash
# Verifiera Refresh Token Rotation (RTR), M2M scopes & asymmetrisk signering:
cd security/02-auth-tokens-m2m-and-rotation
mvn test
```

---

## ☁️ Köra Cloud & K8s Verifieringsskript

```bash
# Verifiera Zero-Downtime RollingUpdate & Connection Draining:
cd cloud-and-k8s/05-zero-downtime-deployments-and-traffic
./verify-zero-downtime.sh

# Verifiera Zero-Trust NetworkPolicies & CoreDNS Egress:
cd cloud-and-k8s/06-k8s-network-policies-and-egress
./verify-network-policies.sh
```

---

## 🗄️ Köra Databas & PostgreSQL-tester

```bash
cd database/01-indexes-locks-and-plans

# Alternativ A: Live-verifiering mot lokal PostgreSQL (WSL / Docker)
./verify-database.sh

# Alternativ B: Automatiserade JUnit 5 & JDBC-tester (H2 PostgreSQL mode)
mvn test
```

---

## 📐 Köra System Design & Broker-tester

```bash
# Verifiera RabbitMQ vs Kafka (Competing consumers, Prefetch starvation, DLQ):
cd system-design/05-rabbitmq-vs-kafka
mvn test
```

---

## 🤖 Interaktiva Live-demonstrationsskript (Bash)

Flera moduler har färdiga interaktiva demonstrationsskript med färgkodade utskrifter:

* **Kretsbrytarens livscykel:** `backend/01-spring-resilience/demo-circuit-breaker.sh`
* **Thread Pool Starvation & Port-isolering:** `backend/01-spring-resilience/demo-thread-starvation.sh`
* **Race Conditions & Lost Updates:** `backend/02-concurrency-threads/demo-race-condition.sh`
* **Lokal OutOfMemoryError & Heap Dump:** `backend/03-jvm-memory-gc/demo-local-oom.sh`
* **Databas Indexering & Låsköer:** `database/01-indexes-locks-and-plans/verify-database.sh`
* **AWS SQS Poison Pill & DLQ:** `cloud-and-k8s/03-aws-event-driven/verify-dlq.sh`
* **Terraform State Lock-konflikt:** `cloud-and-k8s/04-terraform-state-locks/test-state-lock.sh`

---

## 🎯 Så här använder du repot inför intervjun

1. **Välj ett område:** Klicka in i önskad modul i tabellen ovan.
2. **Kör "The Flaw":** Se testet fallera eller granska varför felaktig kod/konfiguration smäller i produktion.
3. **Läs "Keyword Bingo" & "Verklighets-svaret":** Öva på att svara högt på 45 sekunder med rätt facktermer.
4. **Kör "The Fix":** Kör verifieringstestet och se att det blir grönt.
