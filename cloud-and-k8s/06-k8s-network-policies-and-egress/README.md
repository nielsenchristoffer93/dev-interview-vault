# 🔒 Cloud & K8s: NetworkPolicies & Egress Security (Zero-Trust K8s)

> **Intervju-fokus:** Default-Allow fällan, Lateral Movement, CNI enforcement (Calico/Cilium eBPF vs Flannel), Egress DNS CoreDNS port 53-fällan, och Ingress mikrosegmentering.

---

## 🎯 Scenario 1: The "Default-Allow" Flat Network Trap & Lateral Movement

### Problem (Vad kraschar / varför bryr vi oss)
En publik frontend-applikation körs i K8s-namespacet `frontend`. 
Databasen (PostgreSQL med kreditkortsdata) körs i namespacet `database`.
Frontend drabbas av en Zero-Day RCE (Remote Code Execution) via ett sårbart npm-paket.
Angriparen får ett bash-shell inuti frontend-podden och kör:
```bash
# Från frontend-podden:
nc -zv postgres.database.svc.cluster.local 5432
# Svar: Connection to postgres.database.svc.cluster.local 5432 port [tcp/postgresql] succeeded!
```
Angriparen ansluter direkt till produktionsdatabasen, dumpar hela kundregistret och raderar tabeller!
DevOps frågar: *"Hur kunde frontend nå databasen när de ligger i helt olika namespaces?!"*

**Svaret:**
Som standard i Kubernetes är nätverket **helt platt ("Default Allow")**. Alla poddar i alla namespaces kan prata fritt med alla andra poddar i hela klustret såvida ingen explicit **NetworkPolicy** har applicerats!

---

### Keyword Bingo (Facit-lingo)
* **Default-Allow (Platt K8s-nätverk):** Standardbeteendet i Kubernetes där nätverkstrafik mellan samtliga poddar och namespaces är helt öppen och oreglerad.
* **NetworkPolicy Resource:** Kubernetes inbyggda resursdeklaration för brandväggsregler på pod-nivå (Layer 3 och Layer 4).
* **Default-Deny Pattern:** Säkerhetsprincipen att applicera en `default-deny-all`-policy (både för Ingress och Egress) i varje namespace som blockerar all trafik som inte explicit vitlistas.
* **Lateral Movement (Sido-förflyttning):** Angriparens teknik att efter intrång i en perifer container (t.ex. webbfrontend) röra sig obehindrat internt i klustret mot känsligare system (databaser, betaltjänster).
* **Micro-segmentation:** Principen att isolera varje tjänst så att enbart specifikt godkända anrop (t.ex. `order-service` -> `payment-service` på port 8080) tillåts.
* **PodSelector & NamespaceSelector:** Mekanismen i NetworkPolicy för att matcha käll- och målpoddar baserat på etiketter (labels).

---

### Verklighets-svar (30–60 sek)
> "Ett av de vanligaste säkerhetsmissarna i Kubernetes är att anta att namespaces isolerar nätverkstrafik. Namespaces är enbart logiska resurshinkar – Kubernetes nätverk är som standard helt öppet. En komprometterad frontend-pod kan ansluta direkt till en databas-pod i ett annat namespace.
> 
> Som senior implementerar jag **Zero-Trust K8s via NetworkPolicies**:
> 1. Varje namespace inleds med en **`default-deny-all`** policy som stänger av all Ingress och Egress.
> 2. Därefter öppnar vi strikta, deklarativa regler som enbart tillåter nödvändig trafik med både `namespaceSelector` och `podSelector` samt specifika TCP-portar (t.ex. enbart `order-service` får ansluta till `postgres` på port 5432).
> Detta neutraliserar lateral movement och förhindrar datastöld vid perifer kompromettering."

---

## 🎯 Scenario 2: The CoreDNS Port 53 Egress Blackout

### Problem (Vad kraschar / varför bryr vi oss)
Ett team vill säkra sin applikation mot skadlig dataexfiltrering och applicerar en strikt Egress NetworkPolicy som enbart tillåter trafik till databasen på port 5432.
Omedelbart efter att policyn rullats ut kraschar applikationen under omstart med:
```text
java.net.UnknownHostException: postgres.database.svc.cluster.local: Name or service not known
```
Databasen är uppe och lyssnar på port 5432, men applikationen kan inte ens starta!

**Vad hände?**
När applikationen försöker ansluta till `postgres.database.svc.cluster.local` måste den först göra en **DNS-uppslagning** mot Kubernetes interna CoreDNS-server (`10.96.0.10` / `kube-dns` i `kube-system`).
DNS-uppslagningar sker över **UDP/TCP port 53**.
Eftersom teamets Egress-policy bara tillät port 5432 blockerades DNS-anropen tyst! Applikationen kunde inte resolva IP-adressen för databasen och dog!

---

### Keyword Bingo (Facit-lingo)
* **CoreDNS Egress Trap:** Misstaget att blockera all utgående trafik utan att explicit vitlista CoreDNS på UDP/TCP port 53 i `kube-system`.
* **Port 53 UDP/TCP:** Standardporten för DNS-frågor som varje pod behöver för att kunna slå upp Kubernetes interna domäner (`*.svc.cluster.local`) och externa API:er.
* **Egress Filtering:** Att kontrollera och begränsa vart en pod får skicka nätverkstrafik; avgörande för att förhindra att skadlig kod laddas ner eller stulen data skickas till angriparens Command & Control (C2) servrar.
* **NamespaceSelector för `kube-system`:** Nätverkspolicyns regel som matchar `kubernetes.io/metadata.name: kube-system` på port 53.

---

### Verklighets-svar (30–60 sek)
> "Att sätta en `default-deny` på Egress utan att tillåta DNS är en klassisk fälla som omedelbart sänker applikationen med `UnknownHostException`. Innan en container kan öppna en TCP-socket till `postgres` på port 5432 måste den fråga CoreDNS i `kube-system` över UDP/TCP port 53.
> 
> Som senior inkluderar jag alltid en **CoreDNS Egress-regel** i baspolicyn för alla applikationsnamespaces:
> Vi tillåter explicit utgående trafik till poddar med label `k8s-app: kube-dns` i namespacet `kube-system` på port 53 för både UDP och TCP. Först därefter tillåter vi den specifika affärstrafiken."

---

## 🎯 Scenario 3: CNI Enforcement – Calico & Cilium (eBPF) vs Flannel Trap

### Problem (Vad kraschar / varför bryr vi oss)
Ett team spenderar veckor på att skriva vackra NetworkPolicies i YAML och kör `kubectl apply -f policies/`. 
Kommandot returnerar:
```text
networkpolicy.networking.k8s.io/deny-all created
```
DevOps känner sig säkra. Men när de testar anslutningar från en extern pod märker de att trafiken **fortfarande är 100% öppen**! Ingenting blockeras!

**Varför?**
Kubernetes API-server validerar och sparar NetworkPolicy-objektet utan att blinka. Men Kubernetes själv implementerar inga nätverksregler! 
Om klustret körs med ett CNI (Container Network Interface) som **Flannel** (vilket saknar NetworkPolicy-stöd) ignoreras reglerna helt i tysthet!

---

### Keyword Bingo (Facit-lingo)
* **CNI (Container Network Interface):** Klusterpluginet som ansvarar för att tilldela IP-adresser till poddar och dirigera nätverkspaket (t.ex. Calico, Cilium, AWS VPC CNI, Flannel).
* **CNI Enforcement Requirement:** Insikten att Kubernetes NetworkPolicy är ett tomt API-skal om inte klustrets underliggande CNI aktivt stödjer och övervakar policy-tillämpning.
* **Flannel Limitation:** Flannel är en ren L3-overlay som historiskt saknar NetworkPolicy-stöd (kräver Canal / Calico för policies).
* **Cilium & eBPF:** Modernt, högpresterande CNI som ersätter `iptables` med Linux-kärnans eBPF-program; möjliggör Layer 7 (HTTP/gRPC) NetworkPolicies och eliminerar O(N) prestandaförluster vid tiotusentals iptables-regler.
* **Calico:** Det traditionella industristandardiserade CNI:et för Layer 3/4 iptables/IPVS-baserad mikrosegmentering.

---

### Verklighets-svar (30–60 sek)
> "Att applicera en NetworkPolicy i Kubernetes utan att ha verifierat klustrets CNI är en falsk trygghet. `kubectl` bekräftar att policyn skapats, men om klustret använder en ren overlay som standard-Flannel tillämpas noll regler i Linux-kärnan.
> 
> I produktion använder vi **Cilium med eBPF** eller **Calico**:
> Cilium är modernt guldstandard eftersom det opererar direkt i Linux-kärnan via eBPF. Det eliminerar prestandaförlusterna från tusentals komplexa `iptables`-regler och ger oss dessutom **Layer 7 NetworkPolicies** där vi inte bara kan styra IP och port, utan specificera att Service A enbart får göra `GET /api/v1/orders` mot Service B och nekas alla andra HTTP-metoder."

---

## 🛠️ Koden & Verifiering

* **The Flaw:** [flawed-open-namespace.yaml](file:///cloud-and-k8s/06-k8s-network-policies-and-egress/flawed-open-namespace.yaml)
  * Saknar NetworkPolicy (fullständig Default-Allow mellan alla namespaces och poddar).
* **The Fix:** [zero-trust-network-policies.yaml](file:///cloud-and-k8s/06-k8s-network-policies-and-egress/zero-trust-network-policies.yaml)
  * `01-default-deny-all`: Stänger all Ingress och Egress i namespacet.
  * `02-allow-dns-egress`: Tillåter UDP/TCP port 53 till CoreDNS i `kube-system`.
  * `03-allow-app-microsegmentation`: Tillåter enbart `frontend` -> `backend:8080` och `backend` -> `database:5432`.
* **Verifieringsskript:** [verify-network-policies.sh](file:///cloud-and-k8s/06-k8s-network-policies-and-egress/verify-network-policies.sh)
  * Validerar YAML-syntax, CoreDNS-regler och Ingress/Egress-selektorer.

---

## 🧪 Så här kör du testerna

```bash
cd cloud-and-k8s/06-k8s-network-policies-and-egress
chmod +x verify-network-policies.sh
./verify-network-policies.sh
```
