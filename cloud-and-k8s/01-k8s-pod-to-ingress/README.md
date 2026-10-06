# ☸️ 01-k8s-pod-to-ingress: Ingress -> Service -> Pod Port Mappings

## 1. Problem: Vad kraschar eller varför bryr vi oss?
Driftsättning av en ny mikrotjänst i Kubernetes lyckas utan kompileringsfel. Poddarna visar status `Running (1/1)`. Men externa användare möts av **502 Bad Gateway** eller **503 Service Temporarily Unavailable** från NGINX/Traefik Ingress Controller.

Nybörjarmisstaget som drabbar 80% av utvecklare:
1. **Port vs TargetPort förväxling:** Servicen lyssnar på `port: 80`, men vidarebefordrar till `targetPort: 80` istället för containerns faktiska applikationsport (t.ex. `8080` för Spring Boot eller `3000` för Node.js).
2. **Selector Mismatch:** Service-definitionens `spec.selector` matchar inte exakt Poddens `metadata.labels`. Servicen har därmed **noll registrerade endpoints** (`Endpoints: <none>`).

---

## 2. Keyword Bingo (Facit-lingo)
* **Port-hierarkin:**
  * **Ingress `service.port.number`:** Porten som Ingress Controller kontaktar (måste matcha Servicens `port`).
  * **Service `spec.ports[*].port`:** Den interna port som exponeras inom klustrets virtuella IP-nät (ClusterIP).
  * **Service `spec.ports[*].targetPort`:** Porten inuti Poddens container där applikationsprocessen faktiskt lyssnar (`containerPort`).
* **Selector Mismatch:** När etiketter (labels) inte matchar, vilket gör att Kubernetes Controller Manager inte länkar samman Pod och Service.
* **Endpoints / EndpointSlice:** Kubernetes-objektet som håller de faktiska IP-adresserna till friska Poddar. Om `Endpoints: <none>` kommer trafik aldrig fram.
* **502 Bad Gateway:** Ingress kan prata med Servicen, men anslutningen mot backend-podden nekas (Connection Refused pga fel port).
* **503 Service Unavailable:** Ingress hittar inga friska endpoints alls för den angivna servicen.

---

## 3. Verklighets-svar (30–60 sek)
> *"Om Ingress ger 502 eller 503 är det första jag gör att köra `kubectl get endpoints <service-name>`. Om fältet är `<none>` beror det på en Selector Mismatch mellan `service.spec.selector` och `pod.metadata.labels`. Om endpoints finns men Ingress ändå ger 502 beror det nästan alltid på en portförväxling mellan Servicens `port` och `targetPort`. Servicens `port` är klusterporten (ofta 80), medan `targetPort` måste matcha applikationens faktiska `containerPort` (t.ex. 8080). Bäst praxis är att namnge portarna explicit med `name: http` både i containern och i servicen för att helt eliminera numeriska portkonflikter."*

---

## 4. Felsökningsguide (Kommandon & Verktyg)

### A. Kontrollera Endpoints omedelbart
```bash
# Steg 1: Har servicen hittat några pods?
kubectl get endpoints vault-backend-service
# Om output visar <none> har du en label/selector mismatch!

# Steg 2: Jämför labels och selector
kubectl get pods --show-labels
kubectl describe svc vault-backend-service | grep -E "(Selector|Endpoints|Port)"
```

### B. Port-Forward direkt till Pod vs Service för att isolera
```bash
# Testa Podden direkt: Svarar appen på 8080?
kubectl port-forward pod/<pod-name> 8080:8080

# Testa Servicen: Dirigerar servicen trafiken vidare till podden?
kubectl port-forward svc/vault-backend-service 8080:80
```

---

## 5. Koden & Verifiering

* **The Flaw:** [flawed-routing.yaml](file:///cloud-and-k8s/01-k8s-pod-to-ingress/flawed-routing.yaml) – Selector mismatch (`app: payment-api-v2` mot `app: payment-api`) och felaktig `targetPort: 80`.
* **The Fix:** [fixed-routing.yaml](file:///cloud-and-k8s/01-k8s-pod-to-ingress/fixed-routing.yaml) – Korrekt selector-koppling och namngiven `targetPort: http`.
* **Verification Script:** [verify-endpoints.sh](file:///cloud-and-k8s/01-k8s-pod-to-ingress/verify-endpoints.sh) – Skript som validerar selector-överensstämmelse och endpoints.
