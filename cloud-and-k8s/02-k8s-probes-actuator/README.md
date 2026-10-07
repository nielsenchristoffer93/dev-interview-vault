# 🩺 02-k8s-probes-actuator: Liveness, Readiness & Management Port

Denna modul täcker 4 fundamentala hälso- och livscykelutmaningar i Kubernetes för Spring Boot- och mikrotjänstapplikationer. Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 1: Databas-beroende Liveness Probes & Kaskadomstarter (The Death Spiral)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Databasen får en tillfällig trafiktopp eller ett kort nätverksavbrott och svarstiderna ökar tillfälligt från 5ms till 3 sekunder.

Om din **Liveness Probe** är kopplad till standard `/actuator/health` (som utför en `SELECT 1` mot databasen) timear anropet ut. Kubelet drar den felaktiga slutsatsen att själva container-processen har hängt sig och skjuter omedelbart ner alla poddar i klustret. När 20 nya poddar startar samtidigt bombarderar de den redan ansträngda databasen med nya uppkopplingar och initieringsfrågor. Detta skapar en ond cirkel (**The Death Spiral / Cascading Restart**) som förvandlar en 5-sekunders glitch till ett timslångt totalhaveri.

#### 2. Keyword Bingo (Facit-lingo)
* **The Kubernetes Death Spiral:** När Liveness-omstarter ökar belastningen på externa system och drar ner hela klustret i en repetitiv omstartskollaps.
* **External Dependency Coupling:** Det allvarliga anti-mönstret att låta en Liveness Probe kontrollera externa beroenden (databaser, köer, tredjeparts-API:er).
* **`SIGTERM` vs `SIGKILL`:** Hur Kubernetes först försöker avsluta containern mjukt innan den tvångsdödas.
* **FailureThreshold & PeriodSeconds:** Konfigurationerna som definierar hur många misslyckade kontroller som krävs innan Kubelet startar om containern.

#### 3. Verklighets-svar (30–60 sek)
> *"Det farligaste misstaget i Kubernetes är att låta Liveness Proben peka mot standard `/actuator/health` som inkluderar databaskontroller. Om databasen blir överbelastad börjar Kubelet skjuta ner fullt friska poddar. När alla poddar startar om samtidigt kraschar databasen totalt under anstormningen av nya anslutningar – en klassisk Death Spiral. En Liveness Probe får ALDRIG kontrollera externa beroenden; den ska enbart kontrollera om själva JVM-processen och dess interna trådar lever."*

**Labb & Kod:** [flawed-probes.yaml](file:///cloud-and-k8s/02-k8s-probes-actuator/flawed-probes.yaml) vs [fixed-probes-actuator.yaml](file:///cloud-and-k8s/02-k8s-probes-actuator/fixed-probes-actuator.yaml)

---

### Scenario 2: Liveness vs Readiness vs Startup Probes (Ansvarsfördelning)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Utvecklare blandar ofta ihop syftet med Liveness och Readiness probes:
* Om man stänger av Liveness omstartar poddar aldrig vid faktiska dödlägen (Deadlocks).
* Om man saknar Readiness Probe skickas kundtrafik till poddar som inte hunnit värma sina cacher eller som tillfälligt förlorat databaskontakt, vilket ger användarna 500- och 503-fel.
* Om man saknar Startup Probe dödas tunga Spring Boot-appar med många beans under uppstarten innan de ens hunnit initieras.

#### 2. Keyword Bingo (Facit-lingo)
* **Liveness Probe ("Lever processen?"):** Om den fallerar startas podden om med `SIGTERM`. Ska enbart kontrollera lokal processhälsa.
* **Readiness Probe ("Är vi redo för trafik?"):** Om den fallerar plockas Poddens IP bort från Servicens Endpoints. Trafik stoppas tillfälligt, men podden startas **INTE** om!
* **Startup Probe ("Har vi startat klart?"):** Skyddar långsamma applikationer under uppstart genom att inaktivera Liveness och Readiness tills initialiseringen är klar.
* **Spring Boot Probe Groups:** `management.endpoint.health.probes.enabled=true` som automatiskt exponerar `/actuator/health/liveness` och `/actuator/health/readiness`.

#### 3. Verklighets-svar (30–60 sek)
> *"Vi skiljer strikt mellan Liveness och Readiness. I Spring Boot slår vi på probe-stöd så att `/actuator/health/liveness` och `/readiness` exponeras. Liveness kollar enbart om JVM:en lever internt – om den fallerar startar Kubernetes om den. Readiness kollar däremot om vi faktiskt kan betjäna requests (inklusive databasstatus) – om den fallerar tas podden temporärt bort från Servicens Endpoints utan att startas om, vilket skyddar användarna från fel utan att orsaka onödiga kaskadomstarter. För långsam uppstart använder vi Startup Probes."*

**Labb & Kod:** [fixed-probes-actuator.yaml](file:///cloud-and-k8s/02-k8s-probes-actuator/fixed-probes-actuator.yaml) & [application-production.yml](file:///cloud-and-k8s/02-k8s-probes-actuator/application-production.yml)

---

### Scenario 3: Separat Management Port (`8081`) & DoS / Säkerhetsisolering

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Om Actuator körs på samma port (`8080`) som användartrafiken uppstår två allvarliga risker:
1. **Trådtömning & DoS:** Vid hög belastning eller DoS-attacker blockeras alla Tomcats trådar på port 8080. Kubelets interna hälsoanrop hamnar i kö och timear ut, vilket får Kubernetes att döda podden av misstag.
2. **Säkerhetsläckor:** Känsliga endpoints som `/actuator/env` (miljövariabler/hemligheter) och `/actuator/heapdump` (minnesinnehåll) riskerar att exponeras mot internet via Ingress.

#### 2. Keyword Bingo (Facit-lingo)
* **`management.server.port: 8081`:** Konfiguration som skapar en helt fristående intern Tomcat Connector och trådpool för Actuator.
* **Network Security Isolation:** Principen att Ingress enbart exponerar port 8080 externt, medan port 8081 endast nås internt av Kubelet och Prometheus.
* **Information Disclosure Vulnerability:** Risken att läcka API-nycklar via `/actuator/env` eller rå minnesdata via `/actuator/heapdump`.
* **Dedicated Connector Thread Pool:** Garantin att interna hälsocheckar svarar på under 2ms även om port 8080 är 100% överbelastad.

#### 3. Verklighets-svar (30–60 sek)
> *"Att köra Actuator på samma port som användartrafiken är en stor arkitekturrisk. Om port 8080 överbelastas hamnar Kubelets prober i kö och podden dödas i onödan, samtidigt som känsliga mätpunkter riskerar att läcka till internet. Vi sätter alltid `management.server.port: 8081`. Det ger Actuator en egen dedikerad Tomcat-trådpool som alltid svarar blixtsnabbt oberoende av kundbelastning. Dessutom tillåter vår Ingress Controller enbart trafik till port 8080, vilket håller interna administrations-endpoints helt isolerade från internet."*

**Labb & Kod:** [application-production.yml](file:///cloud-and-k8s/02-k8s-probes-actuator/application-production.yml)

---

### Scenario 4: Graceful Shutdown (`server.shutdown: graceful`) vid Deployments

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
När en ny version rullas ut (Rolling Update) skickar Kubernetes ett `SIGTERM` till gamla poddar. Om applikationen saknar konfiguration för graceful shutdown avslutas processen omedelbart, vilket klipper av pågående HTTP-requests och transaktioner mitt i körningen.

Användare ser trasiga 502-fel och ofullständiga databasuppdateringar.

#### 2. Keyword Bingo (Facit-lingo)
* **Graceful Shutdown:** Mekanism där webbservern slutar ta emot nya anrop men låter pågående aktiva requests slutföras inom en grace period.
* **`server.shutdown: graceful`:** Spring Boots deklarativa flagga för att aktivera mjuk nedstängning.
* **`spring.lifecycle.timeout-per-shutdown-phase: 30s`:** Tidsgränsen som tilldelas pågående requests innan processen tvångsavslutas.
* **PreStop Hook (`sleep 5`):** Kubernetes-trick som fördröjer `SIGTERM` med några sekunder så att iptables och kube-proxy hinner ta bort poddens IP från alla routingtabeller innan appen slutar svara.

#### 3. Verklighets-svar (30–60 sek)
> *"Vid Kubernetes deployments riskerar användare att få brutna anslutningar om poddar dödas tvärt. Vi åtgärdar detta på två nivåer: I Spring Boot sätter vi `server.shutdown: graceful` med en timeout på 30 sekunder så att pågående transaktioner hinner slutföras. I Kubernetes Pod-specifikationen lägger vi till en `preStop`-hook med `sleep 5`. Detta ger Kubernetes Ingress och kube-proxy tid att plocka bort poddens IP från nätverksroutingen innan Tomcats lyssnarport stängs, vilket garanterar en 100% noll-nertid deployment (Zero-Downtime Deployment)."*

**Labb & Kod:** [application-production.yml](file:///cloud-and-k8s/02-k8s-probes-actuator/application-production.yml) & [fixed-probes-actuator.yaml](file:///cloud-and-k8s/02-k8s-probes-actuator/fixed-probes-actuator.yaml)

---

## 2. Felsökningsguide (Kommandon & Verktyg)

### A. Identifiera Probe-orsakade omstarter
```bash
# Inspektera om podden har restart count > 0 och se senaste termination reason
kubectl describe pod <pod-name> | grep -A 8 "Last State"

# Kontrollera event-loggen för att se om det var Liveness probe som timeade ut
kubectl get events --sort-by='.metadata.creationTimestamp' | grep "Unhealthy"
```

### B. Verifiera separata Actuator-prober lokalt
```bash
# Liveness Probe (skall svara UP även om DB är nere)
curl -i http://localhost:8081/actuator/health/liveness

# Readiness Probe (svarar OUT_OF_SERVICE om DB tappar anslutning)
curl -i http://localhost:8081/actuator/health/readiness
```

---

## 3. Koden & Verifiering

* **The Flaw:** [flawed-probes.yaml](file:///cloud-and-k8s/02-k8s-probes-actuator/flawed-probes.yaml) – Liveness probe pekar mot databasberoende endpoint på samma port som affärstrafik.
* **The Fix:** [fixed-probes-actuator.yaml](file:///cloud-and-k8s/02-k8s-probes-actuator/fixed-probes-actuator.yaml) & [application-production.yml](file:///cloud-and-k8s/02-k8s-probes-actuator/application-production.yml) – Separat port 8081, isolerade prober och graceful shutdown.

---

## 4. Så här kör du testerna

Kör mot ett lokalt eller simulerat kluster:
```bash
cd cloud-and-k8s/02-k8s-probes-actuator
kubectl apply -f fixed-probes-actuator.yaml
```
