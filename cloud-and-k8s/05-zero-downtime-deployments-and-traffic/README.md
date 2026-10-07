# ☁️ Cloud & K8s: Zero-Downtime Deployments & Traffic Routing

> **Intervju-fokus:** Kubernetes RollingUpdate race conditions, `preStop` sleep hook vs SIGTERM, `maxUnavailable: 0` & `maxSurge`, Blue/Green vs Canary deployments, och kube-proxy iptables avregistrering.

---

## 🎯 Scenario 1: The Dropped Connection Race Condition (HTTP 502 vid Rolling Update)

### Problem (Vad kraschar / varför bryr vi oss)
Ett team driftsätter en ny version av sin Spring Boot-backend mitt under ordinarie kontorstid med 500 req/s.
De använder en standard Kubernetes Deployment.
Under de 60 sekunder som utrullningen pågår rapporterar Sentry och Datadog en kraftig spik av:
```text
HTTP 502 Bad Gateway
eller
connect() failed (111: Connection refused) while connecting to upstream
```
Utvecklaren invänder: *"Vår readiness probe var grön och vi hade 3 repliker igång hela tiden, varför tappades anslutningar?!"*

**Vad som faktiskt händer i Kubernetes-klustret:**
När en gammal pod ska termineras sker två asynkrona händelser **helt oberoende av varandra i parallell**:
1. **Kubelet skickar `SIGTERM`** direkt till applikationscontainern.
2. **Endpoint Controller** märker att podden termineras och skickar en uppdatering till `kube-proxy` på alla noder för att ta bort poddens IP från `iptables` / `IPVS`.

Eftersom nätverksuppdateringen över klustrets noder tar 2–5 sekunder fortsätter Ingress-kontrollern och andra poddar att skicka inkommande HTTP-förfrågningar till den döende podden. Eftersom containern redan tagit emot `SIGTERM` har applikationen stängt sin `ServerSocket`, vilket ger omedelbara `Connection Refused` och 502-fel!

---

### Keyword Bingo (Facit-lingo)
* **Kube-proxy iptables / IPVS Propagation Delay:** Tidsfördröjningen (1–5 sekunder) mellan att en pod markeras som `Terminating` och att alla noder i klustret faktiskt har tagit bort poddens IP från sina lokala routningstabeller.
* **`preStop` Lifecycle Hook:** En livscykelhake i Pod-specifikationen som körs *innan* `SIGTERM` skickas; en `preStop: exec: command: ["sleep", "15"]` ger kube-proxy tid att avregistrera podden från trafiken innan appen stänger porten.
* **Graceful Shutdown (`server.shutdown=graceful`):** Spring Boots mekanism som vid `SIGTERM` slutar acceptera nya anslutningar men väntar på att pågående HTTP-förfrågningar (upp till `spring.lifecycle.timeout-per-shutdown-phase=30s`) ska slutföras.
* **`terminationGracePeriodSeconds`:** Kubernetes-tidsgränsen (standard 30s) innan Kubelet skickar ett ovillkorligt `SIGKILL` (Exit Code 137). Måste alltid vara större än `preStop sleep` + Spring graceful timeout.
* **Asynchronous Termination Flow:** Arkitekturinsikten att Pod-terminering och Endpoint-borttagning är två helt asynkrona kontrollloopar i K8s.

---

### Verklighets-svar (30–60 sek)
> "Att få 502 Bad Gateway under en rullande uppdatering i Kubernetes beror nästan alltid på ett kapplöpningstillstånd mellan Pod-terminering och kube-proxy. När en pod termineras skickar Kubelet `SIGTERM` samtidigt som Endpoint-kontrollern försöker uppdatera `iptables`. Denna nätverkssynkronisering tar några sekunder. Under den tiden skickar Ingressen fortfarande trafik till en container som redan stängt ner sin lyssnande socket.
> 
> Som senior löser jag detta med tre samverkande konfigurationer:
> 1. En `preStop`-hook med `sleep 15` i containern. Detta tvingar podden att förbli aktiv och hantera inkommande anrop medan klustret plockar bort dess IP från routingen.
> 2. `server.shutdown=graceful` i Spring Boot med 30s timeout för att låta pågående requests avslutas rent.
> 3. Sätta `terminationGracePeriodSeconds: 60` så att Kubernetes inte skickar `SIGKILL` innan applikationen hunnit avsluta."

---

## 🎯 Scenario 2: RollingUpdate Trap (`maxUnavailable` & `maxSurge` i små kluster)

### Problem (Vad kraschar / varför bryr vi oss)
En affärskritisk mikrotjänst körs med `replicas: 2`. Deploymenten använder standardinställningarna i Kubernetes:
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxUnavailable: 25%
    maxSurge: 25%
```
Under en driftsättning beräknar Kubernetes:
* `maxUnavailable: 25%` av 2 = `0.5` (avrundas neråt till `0` eller dödar 1 pod om heltal används).
* `maxSurge: 25%` av 2 = `0.5` (avrundas uppåt till `1`).

Om en av poddarna termineras omedelbart bär den enda kvarvarande podden **100% av hela systemets trafik**. 
Dess CPU klockar i taket, drabbas av **CPU Throttling**, svarstiderna tiofaldigas och Kubernetes Liveness Probe misslyckas, varpå även den sista podden startas om i en total **Cascading Outage**.

---

### Keyword Bingo (Facit-lingo)
* **`maxSurge`:** Det maximala antalet extra poddar som får skapas utöver `replicas` under en utrullning (t.ex. `maxSurge: 1` eller `25%`).
* **`maxUnavailable`:** Det maximala antalet poddar som får vara otillgängliga under utrullningen.
* **Zero-Downtime Golden Rule:** För att garantera 100% kapacitet under hela utrullningen måste `maxUnavailable: 0` och `maxSurge: 1` (eller `100%`) konfigureras.
* **PodDisruptionBudget (PDB):** En Kubernetes-resurs (`minAvailable: 2` eller `maxUnavailable: 1`) som garanterar att frivilliga störningar (såsom noduppgraderingar eller cluster autoscaler drains) inte sänker tjänsten under en säker minimikapacitet.
* **Capacity Planning under Deployments:** Att säkerställa att Kubernetes-noderna faktiskt har tillräckligt med ledigt RAM/CPU för att rymma `maxSurge`-poddarna; annars fastnar de nya poddarna i `Pending` pga resursbrist.

---

### Verklighets-svar (30–60 sek)
> "Standardvärdena för `maxUnavailable` (25%) i Kubernetes är farliga för distributioner med få repliker (2–3 stycken), eftersom nedsläckningen av en enda pod omedelbart överbelastar de återstående noderna och triggar CPU throttling eller kaskadkrascher.
> 
> Som senior konfigurerar jag alltid:
> 1. `maxUnavailable: 0` och `maxSurge: 1` (eller `50%`). Detta tvingar Kubernetes att starta och verifiera en ny frisk pod innan en enda gammal pod tillåts stängas av.
> 2. En tillhörande **PodDisruptionBudget (PDB)** med `minAvailable: 1` eller `minAvailable: 50%` som skyddar tjänsten under driftunderhåll och noddräneringar.
> 3. Korrekt dimensionerade `resources.requests` så att klustret garanterat har kapacitet för transienta surge-poddar."

---

## 🎯 Scenario 3: Deployment-strategier – Rolling vs Blue/Green vs Canary

### Problem (Vad kraschar / varför bryr vi oss)
Ett team släpper en ny version av sin applikation. 
Under en vanlig **RollingUpdate** dirigeras 50% av användarna till v1 och 50% till v2.
Om det finns en bugg i v2 (eller en schemaändring som inte följer Expand/Contract) drabbas 50% av alla kunder omedelbart.
Om teamet behöver rulla tillbaka tar en rollback ytterligare 2–3 minuter medan v1-poddarna startar om.

---

### Keyword Bingo (Facit-lingo)
* **RollingUpdate:** Standardmetoden; byter ut poddar sekventiellt. Fördel: resurssnål. Nackdel: v1 och v2 körs samtidigt; svårt med omedelbar rollback.
* **Blue/Green Deployment:** Två identiska produktionsmiljöer (Blue = aktiv v1, Green = ny v2). All testning sker på Green innan trafiken switchas instantant via Service selector eller Ingress. Fördel: 0 ms rollback. Nackdel: dubbel infrastrukturkostnad under deploy.
* **Canary Deployment (Argo Rollouts / Flagger / Istio):** Trafik dirigeras progressivt (t.ex. 95% till v1, 5% till v2). Om felkvot eller P99-latens ökar rullas v2 automatiskt tillbaka innan 95% av kunderna ens märkt något.
* **Traffic Weighting (Service Mesh / Ingress NGINX):** Förmågan att vikta trafik baserat på procent (`nginx.ingress.kubernetes.io/canary-weight: "10"`).
* **Automated Metric Analysis (AnalysisTemplate):** Regler i Argo Rollouts som kontinuerligt queryar Prometheus och avbryter utrullningen om HTTP 5xx överstiger 0.5%.

---

### Verklighets-svar (30–60 sek)
> "Valet av driftsättningsstrategi handlar om riskhantering.
> * För standardfunktioner med full bakåtkompatibilitet räcker **RollingUpdate** med `maxUnavailable: 0` och `preStop sleep`.
> * För kritiska releaser där vi vill eliminera risken för samexistens mellan v1 och v2 använder vi **Blue/Green Deployment**, där vi har två miljöer och switchar Service-selectorn med 0 sekunders nätverksavbrott och omedelbar rollback.
> * För enterprise med hundratusentals användare kör vi **Canary Releases med Argo Rollouts eller Istio**. Vi styr 5% av trafiken till den nya versionen och låter en automatiserad `AnalysisTemplate` mot Prometheus övervaka felkvoter och latens i 10 minuter innan trafiken gradvis trappas upp till 100%."

---

## 🛠️ Koden & Verifiering

* **The Flaw:** [flawed-deployment.yaml](file:///cloud-and-k8s/05-zero-downtime-deployments-and-traffic/flawed-deployment.yaml)
  * Saknar `preStop` hook (orsakar 502-fel under pod-terminering).
  * Använder `maxUnavailable: 50%` vilket sänker klustret under deploy.
  * Saknar PodDisruptionBudget.
* **The Fix:** [zero-downtime-deployment.yaml](file:///cloud-and-k8s/05-zero-downtime-deployments-and-traffic/zero-downtime-deployment.yaml)
  * Implementerar `preStop: exec: command: ["sleep", "15"]`.
  * Konfigurerar `maxUnavailable: 0` och `maxSurge: 1`.
  * Inkluderar `PodDisruptionBudget` med `minAvailable: 2`.
  * Definierar korrekt `terminationGracePeriodSeconds: 60`.
* **Verifieringsskript:** [verify-zero-downtime.sh](file:///cloud-and-k8s/05-zero-downtime-deployments-and-traffic/verify-zero-downtime.sh)
  * Kör en ApacheBench / cURL-loop under pågående rollout och bevisar 0 misslyckade requests.

---

## 🧪 Så här kör du och testar

```bash
cd cloud-and-k8s/05-zero-downtime-deployments-and-traffic
chmod +x verify-zero-downtime.sh
./verify-zero-downtime.sh
```
