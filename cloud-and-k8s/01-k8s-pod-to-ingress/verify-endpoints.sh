#!/usr/bin/env bash
set -euo pipefail

# Verifieringsskript för Kubernetes Service & Endpoints
SERVICE_NAME="payment-service"
NAMESPACE="${1:-default}"

echo "=== 1. Undersöker Kubernetes Service '$SERVICE_NAME' i namespace '$NAMESPACE' ==="
kubectl get svc "$SERVICE_NAME" -n "$NAMESPACE" -o wide

echo ""
echo "=== 2. Kontrollerar om Endpoints är kopplade ==="
ENDPOINTS=$(kubectl get endpoints "$SERVICE_NAME" -n "$NAMESPACE" -o jsonpath='{.subsets[*].addresses[*].ip}' || echo "")

if [ -z "$ENDPOINTS" ]; then
  echo "❌ FEL: Inga endpoints hittades för service '$SERVICE_NAME'!"
  echo "Möjliga orsaker:"
  echo " - Selector mismatch i servicen: $(kubectl get svc $SERVICE_NAME -n $NAMESPACE -o jsonpath='{.spec.selector}')"
  echo " - Inga poddar matchar labeln eller poddarna är inte i Ready-status."
  exit 1
else
  echo "✅ FRAMGÅNG: Endpoints hittades!"
  echo "Registrerade Pod IP-adresser: $ENDPOINTS"
fi

echo ""
echo "=== 3. Kontrollerar Port Mapping ==="
PORT_MAP=$(kubectl get svc "$SERVICE_NAME" -n "$NAMESPACE" -o jsonpath='{range .spec.ports[*]}Port: {.port} -> TargetPort: {.targetPort}{"\n"}{end}')
echo "$PORT_MAP"
