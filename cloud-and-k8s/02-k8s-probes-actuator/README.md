# 🩺 02-k8s-probes-actuator: Liveness, Readiness & Management Port

## 1. Problem: Vad kraschar eller varför bryr vi oss?
Ett klassiskt produktionshaveri i Kubernetes:
Databasen får en tillfällig trafiktopp eller nätverksspik och svarstiderna ökar från 5ms till 3 sekunder.
* Om din **Liveness Probe** är kopplad till full `/actuator/health` (som inkluderar DB-ping), tror Kubernetes att själva container-processen har hängt sig.
* Kubernetes börjar genast skjuta ner och starta om alla Poddar samtidigt.
* När 20 nya poddar startar samtidigt bombarderar de den redan ansträngda databasen med nya anslutningspooler under uppstarten.
* Detta kallas en **Death Spiral (Dödsspiral / Cascading Restart)** och förvandlar en 5 sekunders nätverksglitch till ett timslångt totalhaveri.

Dessutom: Om Actuator körs på samma port (`8080`) som användartrafik kan DoS-attacker mot appen göra att probes timear ut och triggar omstarter i onödan, samtidigt som känslig Actuator-data (`/actuator/env`, `/actuator/heapdump`) riskerar att läcka till internet.

---

## 2. Keyword Bingo (Facit-lingo)
* **Liveness Probe:** "Lever processen?" Om den fallerar görs `SIGTERM` och omstart av containern. Bör **ALDRIG** kontrollera externa beroenden som databaser eller meddelandeköer!
* **Readiness Probe:** "Är applikationen redo att ta emot trafik?" Om den fallerar plockas Poddens IP bort från Servicens Endpoints/Load Balancer. Trafik stoppas tillfälligt, men podden startas **INTE** om.
* **Startup Probe:** Ger långsamma monoliter eller Spring Boot-appar med många beans tid att initialiseras innan Liveness och Readiness probes börjar straffa den.
* **Cascading Restart / Death Spiral:** När Liveness-omstarter ökar belastningen på externa system och drar ner hela klustret.
* **Separate Management Port (`management.server.port: 8081`):** Säkerställer att mätvärden och hälsokontroller har sin egen interna trådpool och port, helt isolerad från inkommande användartrafik på port 8080.
* **Graceful Shutdown (`server.shutdown: graceful`):** Tillåter pågående HTTP-requests att slutföras inom en timeout innan Podden avslutas vid deployment.

---

## 3. Verklighets-svar (30–60 sek)
> *"Det farligaste misstaget i Kubernetes är att låta Liveness Proben kontrollera externa beroenden som databasen via standard `/actuator/health`. Går databasen ner startar Kubernetes om alla friska poddar i en dödsspiral. Lösningen är att strikt skilja på Liveness och Readiness. Vi konfigurerar Spring Boot Actuator med `management.endpoint.health.probes.enabled=true`. Liveness Probe pekar mot `/actuator/health/liveness` och kollar enbart om JVM-processen lever. Readiness Probe pekar mot `/actuator/health/readiness` och avgör om podden ska ta emot trafik via Endpoints. Dessutom flyttar vi Actuator till en separat port, `8081`, så att interna hälsoprober inte blockeras om kundporten 8080 blir överbelastad."*

---

## 4. Felsökningsguide (Kommandon & Verktyg)

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

## 5. Koden & Verifiering

* **The Flaw:** [flawed-probes.yaml](file:///cloud-and-k8s/02-k8s-probes-actuator/flawed-probes.yaml) – Liveness probe pekar mot databasberoende endpoint på samma port som affärstrafik.
* **The Fix:** [fixed-probes-actuator.yaml](file:///cloud-and-k8s/02-k8s-probes-actuator/fixed-probes-actuator.yaml) & [application-production.yml](file:///cloud-and-k8s/02-k8s-probes-actuator/application-production.yml) – Separat port 8081, isolerade prober och graceful shutdown.
