# 🏛️ dev-interview-vault

> **Pragmatiskt referens- och labbrepo för tekniska intervjuer och djupgående fullstack/cloud-arkitektur.**  
> Fokus: Återskapa felet i kod (*The Flaw*), förstå varför det smäller, formulera svaret med intervju-lingo (*Keyword Bingo*), och verifiera fixen med tester (*The Fix*).

---

## 🧭 Arkitektur & Filosofi: "The 3-File Pattern"

Varje labbmodul är strikt isolerad och byggd kring samma trestegsmönster:

| Fil / Komponent | Syfte & Innehåll |
| :--- | :--- |
| **`README.md`** | **1. Problem:** Vad smäller och varför bryr vi oss i produktion?<br>**2. Keyword Bingo:** Facit-lingo och begrepp intervjuare letar efter.<br>**3. Verklighets-svar (30–60 sek):** Pragmatisk "elevator pitch" utan flum.<br>**4. Felsökningsguide:** Exakta CLI-verktyg och flaggor (`mvn`, `kubectl`, `jcmd`, VisualVM). |
| **The Flaw (`*.java`, `*.tsx`, `*.yaml`...)** | Minimal kodbas som **avsiktligt fallerar**, läcker minne eller skapar en flaskhals. |
| **The Fix / Verification (`*Test.java`, `*spec.ts`...)** | Automatiserat test (JUnit 5, Testcontainers, Vitest, etc.) eller åtgärdad konfiguration som **bevisar** att problemet är löst. |

---

## 🗂️ Modulkarta & Snabbreferens

### ☕ 1. Backend (Java / Spring Boot / JVM)
| Modul | Fokusområde | Nyckelbegrepp (Keyword Bingo) |
| :--- | :--- | :--- |
| [01-spring-resilience](file:///backend/01-spring-resilience/README.md) | Resilience4j, Circuit Breakers, Retries | Cascading failure, Thread starvation, Half-Open state, Exponential backoff |
| [02-concurrency-threads](file:///backend/02-concurrency-threads/README.md) | Race conditions, Spring Singleton scope | Memory visibility, Happens-Before, Non-atomic read-modify-write, `AtomicReference` |
| [03-jvm-memory-gc](file:///backend/03-jvm-memory-gc/README.md) | OOM, Heap dump, GC-pauser, k8s cgroups | Metaspace, Stop-The-World (STW), OOMKilled (Exit 137), `MaxRAMPercentage` |
| [04-jpa-performance](file:///backend/04-jpa-performance/README.md) | N+1 queries, EntityGraph, Locking | Cartesian product, `@EntityGraph`, PESSIMISTIC vs OPTIMISTIC, `@Version` |
| [05-maven-dependency-tree](file:///backend/05-maven-dependency-tree/README.md) | Jar Hell, transitiva beroenden | Nearest-definition-wins, Transitive dependency, Exclusion, `NoSuchMethodError` |
| [06-testing-pyramid](file:///backend/06-testing-pyramid/README.md) | Mockito vs `@WebMvcTest` vs Testcontainers | Unit vs Slice vs Integration, ApplicationContext cache, In-process vs Real container |

---

### ⚛️ 2. Frontend (React / Vue / Web Standards)
| Modul | Fokusområde | Nyckelbegrepp (Keyword Bingo) |
| :--- | :--- | :--- |
| [01-tanstack-vs-store](file:///frontend/01-tanstack-vs-store/README.md) | Server state vs Client/UI state | Stale-While-Revalidate, Cache invalidation, Over-fetching, Zustand/Pinia separation |
| [02-rendering-performance](file:///frontend/02-rendering-performance/README.md) | Onödiga re-renders, useMemo/useCallback | Referential equality, Virtual DOM reconciliation, DOM node thrashing, Virtualization |
| [03-auth-session-flow](file:///frontend/03-auth-session-flow/README.md) | BFF-mönster, Tokenhantering, XSS vs CSRF | SameSite cookies, `HttpOnly`, Backend-for-Frontend (BFF), Credential exfiltration |
| [04-optimistic-updates](file:///frontend/04-optimistic-updates/README.md) | TanStack Query mutation & rollback | Optimistic UI, Snapshot rollback, Race condition on mutation settle, Error recovery |

---

### ☁️ 3. Cloud & Kubernetes
| Modul | Fokusområde | Nyckelbegrepp (Keyword Bingo) |
| :--- | :--- | :--- |
| [01-k8s-pod-to-ingress](file:///cloud-and-k8s/01-k8s-pod-to-ingress/README.md) | Ingress -> Service -> Pod port forwarding | Selector mismatch, TargetPort vs Port, Endpoints/EndpointSlice, 502/503 Gateway |
| [02-k8s-probes-actuator](file:///cloud-and-k8s/02-k8s-probes-actuator/README.md) | Liveness & Readiness vs Actuator | Death spiral, Cascading restart, Probes on separate management port, Graceful shutdown |
| [03-aws-event-driven](file:///cloud-and-k8s/03-aws-event-driven/README.md) | EventBridge -> SQS -> Dead-Letter Queue | Poison pill, At-least-once delivery, Exponential backoff, DLQ redrive policy |
| [04-terraform-state-locks](file:///cloud-and-k8s/04-terraform-state-locks/README.md) | S3 remote backend + DynamoDB lock | Race condition in CI/CD, State corruption, DynamoDB LockID, `terraform force-unlock` |

---

### 📐 4. System Design
| Modul | Fokusområde | Nyckelbegrepp (Keyword Bingo) |
| :--- | :--- | :--- |
| [idempotency-patterns](file:///system-design/idempotency-patterns/README.md) | Idempotens & distribuerad låsning | Idempotency Key, At-least-once delivery, Double-charging, Distributed Redis Redlock |
| [rate-limiting](file:///system-design/rate-limiting/README.md) | Token Bucket vs Leaky Bucket | Sliding Window Counter, Burstiness handling, 429 Too Many Requests, Distributed Redis rate limiter |
| [outbox-pattern](file:///system-design/outbox-pattern/README.md) | Transactional Outbox & Debezium CDC | Dual-write problem, Distributed 2PC anti-pattern, Change Data Capture (CDC), Eventual consistency |

---

## 🚀 Snabbstart & Lab-infrastruktur

Projektet innehåller färdiga Docker Compose-filer för lokala beroenden och mätvärden.

### 1. Bas-infrastruktur (PostgreSQL, Redis, LocalStack)
```bash
docker compose -f compose/docker-compose.base.yml up -d
```
* **PostgreSQL:** `localhost:5432` (db: `interview_db`, user: `vault_user`, pass: `vault_pass`)
* **Redis:** `localhost:6379`
* **LocalStack:** `localhost:4566` (Emulerar AWS S3, DynamoDB, SQS, EventBridge)

### 2. Monitoring (Prometheus & Grafana)
```bash
docker compose -f compose/docker-compose.monitoring.yml up -d
```
* **Prometheus:** `http://localhost:9090`
* **Grafana:** `http://localhost:3000` (admin / admin)

---

## 🎯 Så här använder du repot inför intervjun

1. **Välj ett område:** Klicka in i modulen i tabellen ovan.
2. **Kör "The Flaw":** Se testet fallera eller granska varför felaktig kod/konfiguration smäller i produktion.
3. **Läs "Keyword Bingo" & "Verklighets-svaret":** Öva på att svara högt på 45 sekunder med rätt termer.
4. **Kör "The Fix":** Kör verifieringstestet och se att det blir grönt.
