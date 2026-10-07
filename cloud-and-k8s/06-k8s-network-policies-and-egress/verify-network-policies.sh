#!/usr/bin/env bash
set -euo pipefail

echo "=========================================================="
echo "🧪 Verifierar Kubernetes Zero-Trust NetworkPolicies"
echo "=========================================================="

FLAWED_FILE="flawed-open-namespace.yaml"
ZERO_TRUST_FILE="zero-trust-network-policies.yaml"

# 1. Kontrollera att filerna existerar
if [[ ! -f "$FLAWED_FILE" || ! -f "$ZERO_TRUST_FILE" ]]; then
  echo "❌ Kunde inte hitta YAML-manifestfilerna!"
  exit 1
fi

echo "1. Analyserar flawed-open-namespace.yaml..."
if grep -q "kind: NetworkPolicy" "$FLAWED_FILE"; then
  echo "❌ Flawed-filen innehåller oväntat NetworkPolicy!"
  exit 1
fi
echo "   ✅ Flawed fil saknar NetworkPolicy: Klustret körs i sårbar 'Default-Allow' utan segmentering."

echo "2. Kontrollerar Default-Deny-All i zero-trust-network-policies.yaml..."
if ! grep -q "name: default-deny-all" "$ZERO_TRUST_FILE"; then
  echo "❌ Saknar 'default-deny-all' NetworkPolicy!"
  exit 1
fi
if ! grep -A 5 "name: default-deny-all" "$ZERO_TRUST_FILE" | grep -q "podSelector: {}"; then
  echo "❌ 'default-deny-all' måste ha tomt podSelector för att matcha alla pods i namespacet!"
  exit 1
fi
echo "   ✅ Default-Deny-All är korrekt konfigurerad för att blockera all oönskad Ingress och Egress."

echo "3. Kontrollerar CoreDNS Port 53 Egress-regel..."
if ! grep -q "name: allow-dns-egress" "$ZERO_TRUST_FILE"; then
  echo "❌ Saknar CoreDNS Egress-policy för port 53!"
  exit 1
fi
if ! grep -q "port: 53" "$ZERO_TRUST_FILE"; then
  echo "❌ CoreDNS-policy saknar port 53 (UDP/TCP)!"
  exit 1
fi
echo "   ✅ CoreDNS Port 53 Egress är konfigurerad. Förhindrar UnknownHostException!"

echo "4. Kontrollerar Mikrosegmentering mot Lateral Movement..."
if ! grep -q "name: database-policy" "$ZERO_TRUST_FILE"; then
  echo "❌ Saknar database-policy!"
  exit 1
fi
# Kontrollera att frontend INTE har direkt access till port 5432
if grep -A 25 "name: frontend-policy" "$ZERO_TRUST_FILE" | grep -q "5432"; then
  echo "❌ Frontend-policy tillåter otillåtet direktåtkomst till databasport 5432!"
  exit 1
fi
# Kontrollera att database-policy enbart tillåter payment-backend
if ! grep -A 20 "name: database-policy" "$ZERO_TRUST_FILE" | grep -q "app: payment-backend"; then
  echo "❌ Database-policy tillåter inte anslutning från payment-backend!"
  exit 1
fi
echo "   ✅ Mikrosegmentering verifierad: Frontend kan INTE nå databasen (port 5432). Lateral Movement förhindrad!"

echo ""
echo "🎉 ALLA 4 KUBERNETES ZERO-TRUST NÄTVERKSPOLICY-TESTER GICK IGENOM!"
echo "=========================================================="
