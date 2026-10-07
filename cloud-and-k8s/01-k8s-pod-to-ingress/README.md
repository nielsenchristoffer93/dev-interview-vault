# ☸️ 01-k8s-pod-to-ingress: Ingress -> Service -> Pod Port Mappings

Denna modul täcker 3 fundamentala nätverks- och routing-utmaningar i Kubernetes från Ingress Controller hela vägen till applikationscontainern. Varje scenario presenterar problemet (vad som kraschar och varför vi bryr oss), dess specifika **Keyword Bingo** (facit-lingo), samt ett **Verklighets-svar (30–60 sek)** skräddarsytt för tekniska intervjuer.

---

## 1. Koncept & Scenarier

### Scenario 1: Port vs TargetPort förväxling (502 Bad Gateway)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Driftsättningen av en applikation i Kubernetes lyckas helt utan kompileringsfel och poddarna visar status `Running (1/1)`. Men när externa användare gör anrop möts de omedelbart av **`502 Bad Gateway`** från Ingress Controllern (NGINX/Traefik).

Orsaken är en förväxling av Kubernetes port-hierarki: Service-definitionen anger `port: 80` och vidarebefordrar till `targetPort: 80` istället för containerns faktiska applikationsport (t.ex. `8080` för Spring Boot eller `3000` för Node.js). Servicen försöker skicka trafik till en stängd port inuti podden, vilket resulterar i *Connection Refused*.

#### 2. Keyword Bingo (Facit-lingo)
* **Port-hierarkin:**
  * *Ingress `service.port.number`:* Porten som Ingress Controller kontaktar (måste matcha Servicens `port`).
  * *Service `spec.ports[*].port`:* Den virtuella klusterporten inom klustrets privata nät (ClusterIP).
  * *Service `spec.ports[*].targetPort`:* Den faktiska port där applikationen lyssnar inuti containern (`containerPort`).
* **502 Bad Gateway:** Ingress kan nå Servicen, men vidarekopplingen till Podden nekas (*Connection Refused*).
* **ContainerPort:** Deklarerad port i Pod-specifikationen (`containerPort: 8080`).
* **Connection Refused:** Nätverksfelet som uppstår när ingen process lyssnar på målporten.

#### 3. Verklighets-svar (30–60 sek)
> *"Ett 502 Bad Gateway från Ingress betyder nästan alltid att Kubernetes når fram till poddens IP-adress men att anslutningen nekas på portnivå. Felet är oftast en förväxling mellan `port` och `targetPort` i Servicen. Servicens `port` är den interna klusterporten (ofta 80), medan `targetPort` måste matcha containerns faktiska lyssningsport, exempelvis 8080 för Spring Boot. Bästa praxis är att namnge porten i containern med `name: http` och referera till namnet i servicens `targetPort: http` för att helt eliminera numeriska portmissar."*

**Labb & Kod:** [flawed-routing.yaml](file:///cloud-and-k8s/01-k8s-pod-to-ingress/flawed-routing.yaml) vs [fixed-routing.yaml](file:///cloud-and-k8s/01-k8s-pod-to-ingress/fixed-routing.yaml)

---

### Scenario 2: Selector Mismatch & Tomma Endpoints (`Endpoints: <none>` -> 503 Service Unavailable)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
Poddarna rullar för fullt och servicen är skapad, men externa anrop returnerar **`503 Service Temporarily Unavailable`**.

Detta beror på en **Selector Mismatch**: Service-objektets `spec.selector` (t.ex. `app: payment-api-v2`) matchar inte exakt Poddens `metadata.labels` (t.ex. `app: payment-api`). Kubernetes Controller Manager kan därför inte koppla ihop servicen med några körande poddar. Servicens endpoint-lista är helt tom (**`Endpoints: <none>`**), och trafiken har ingenstans att ta vägen.

#### 2. Keyword Bingo (Facit-lingo)
* **Selector Mismatch:** När etiketter (labels) skiljer sig åt mellan Pod och Service, vilket bryter den logiska kopplingen.
* **Endpoints / EndpointSlice:** Kubernetes-objektet som håller reda på de faktiska IP-adresserna till friska poddar.
* **`Endpoints: <none>`:** Den avslöjande indikatorn på att servicen inte hittar några matchande poddar.
* **503 Service Unavailable:** Ingress Controllern hittar noll tillgängliga endpoints i klustret att dirigera trafiken till.
* **`kubectl get endpoints`:** Det absolut första felsökningskommandot när en service inte svarar.

#### 3. Verklighets-svar (30–60 sek)
> *"Om Ingress returnerar 503 är det första steget alltid att köra `kubectl get endpoints <service-name>`. Om fältet visar `<none>` beror det på en Selector Mismatch: servicens `spec.selector` matchar inte poddarnas labels i `metadata.labels`. Servicen är i praktiken död eftersom den saknar mål-IP:n. Vi åtgärdar det genom att synkronisera etiketterna, och kontrollerar sedan omedelbart med `kubectl get endpoints` att poddarnas IP-adresser dyker upp i listan."*

**Labb & Kod:** [flawed-routing.yaml](file:///cloud-and-k8s/01-k8s-pod-to-ingress/flawed-routing.yaml) vs [fixed-routing.yaml](file:///cloud-and-k8s/01-k8s-pod-to-ingress/fixed-routing.yaml) & [verify-endpoints.sh](file:///cloud-and-k8s/01-k8s-pod-to-ingress/verify-endpoints.sh)

---

### Scenario 3: Named Ports & Ren Ingress-routing (End-to-End Arkitektur)

#### 1. Problem: Vad kraschar eller varför bryr vi oss?
I stora team ändras applikationers interna portar (t.ex. vid uppgradering från HTTP/1.1 till gRPC, eller när en container byter bas-port från 8080 till 3000). Om hårdkodade heltal (`8080`) sprids ut över Ingress-, Service- och Pod-manifest tvingas man uppdatera och synkronisera tiotals YAML-filer samtidigt, vilket ofta leder till produktionshaverier vid driftsättning.

#### 2. Keyword Bingo (Facit-lingo)
* **Named Ports:** Att namnge portar i containern (`name: http`) och använda strängnamnet i Servicen (`targetPort: http`).
* **Ingress Controller (NGINX/Traefik):** Den externa reverse proxyn som läser Ingress-regler och dirigerar trafik direkt till poddarnas Endpoint-IP:n (kringgår ClusterIP kube-proxy för prestanda).
* **ClusterIP:** Standardtypen för Kubernetes-services som ger en stabil intern IP-adress inom klustret.
* **Declarative Port Decoupling:** Att frikoppla den externa servicens presentationsport från containerns interna lyssningsport.

#### 3. Verklighets-svar (30–60 sek)
> *"För att göra Kubernetes-arkitekturen underhållbar och motståndskraftig mot fel använder vi alltid Named Ports. I containern deklarerar vi `containerPort: 8080` under namnet `http`. I servicen sätter vi `port: 80` men `targetPort: http`. Om containerns interna port någonsin ändras behöver vi bara uppdatera Deployment-manifestet; alla Services och Ingress-regler förblir helt oförändrade eftersom de refererar till det stabila symboliska namnet `http`."*

**Labb & Kod:** [fixed-routing.yaml](file:///cloud-and-k8s/01-k8s-pod-to-ingress/fixed-routing.yaml)

---

## 2. Felsökningsguide (Kommandon & Verktyg)

### A. Kontrollera Endpoints omedelbart
```bash
# Steg 1: Har servicen hittat några pods?
kubectl get endpoints vault-backend-service
# Om output visar <none> har du en label/selector mismatch!

# Steg 2: Jämför labels och selector
kubectl get pods --show-labels
kubectl describe svc vault-backend-service | grep -E "(Selector|Endpoints|Port)"
```

### B. Port-Forward direkt till Pod vs Service för att isolera felet
```bash
# Testa Podden direkt: Svarar appen på 8080?
kubectl port-forward pod/<pod-name> 8080:8080

# Testa Servicen: Dirigerar servicen trafiken vidare till podden?
kubectl port-forward svc/vault-backend-service 8080:80
```

---

## 3. Koden & Verifiering

* **The Flaw:** [flawed-routing.yaml](file:///cloud-and-k8s/01-k8s-pod-to-ingress/flawed-routing.yaml) – Selector mismatch (`app: payment-api-v2` mot `app: payment-api`) och felaktig `targetPort: 80`.
* **The Fix:** [fixed-routing.yaml](file:///cloud-and-k8s/01-k8s-pod-to-ingress/fixed-routing.yaml) – Korrekt selector-koppling och namngiven `targetPort: http`.
* **Verification Script:** [verify-endpoints.sh](file:///cloud-and-k8s/01-k8s-pod-to-ingress/verify-endpoints.sh) – Skript som validerar selector-överensstämmelse och endpoints.

---

## 4. Så här kör du testerna

```bash
cd cloud-and-k8s/01-k8s-pod-to-ingress
./verify-endpoints.sh
```
