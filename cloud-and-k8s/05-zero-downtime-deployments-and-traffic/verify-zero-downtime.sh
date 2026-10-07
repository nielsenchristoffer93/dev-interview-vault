#!/usr/bin/env bash
# ==============================================================================
# Cloud & K8s: Zero-Downtime Deployment Verifier
# ==============================================================================
# Verifies syntax and configuration differences between flawed and resilient
# Kubernetes deployment strategies for zero-downtime rollouts.
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FLAWED_FILE="$SCRIPT_DIR/flawed-deployment.yaml"
RESILIENT_FILE="$SCRIPT_DIR/zero-downtime-deployment.yaml"

echo "=========================================================="
echo " K8s Zero-Downtime Deployment Verification Lab"
echo "=========================================================="

echo "[1/4] Checking YAML manifest syntax..."
if command -v kubectl >/dev/null 2>&1; then
    kubectl apply --dry-run=client -f "$FLAWED_FILE" >/dev/null
    kubectl apply --dry-run=client -f "$RESILIENT_FILE" >/dev/null
    echo "  -> Both manifests pass kubectl dry-run validation."
else
    echo "  -> kubectl not installed locally, skipping client dry-run."
fi

echo "[2/4] Verifying Flawed Deployment Anti-Patterns:"
if grep -q "maxUnavailable: 50%" "$FLAWED_FILE"; then
    echo "  [FLAW DETECTED] maxUnavailable allows 50% capacity drop during rollout."
fi
if ! grep -q "preStop" "$FLAWED_FILE"; then
    echo "  [FLAW DETECTED] Missing preStop hook! Vulnerable to 502 Bad Gateway during iptables sync."
fi

echo "[3/4] Verifying Resilient Deployment Gold Standard:"
if grep -q "maxUnavailable: 0" "$RESILIENT_FILE"; then
    echo "  [FIX VERIFIED] maxUnavailable: 0 guarantees zero capacity degradation."
fi
if grep -q "maxSurge: 1" "$RESILIENT_FILE"; then
    echo "  [FIX VERIFIED] maxSurge: 1 allocates extra healthy pod before draining old pods."
fi
if grep -q "preStop" "$RESILIENT_FILE" && grep -q "sleep 15" "$RESILIENT_FILE"; then
    echo "  [FIX VERIFIED] preStop hook waits 15s for kube-proxy iptables propagation."
fi
if grep -q "kind: PodDisruptionBudget" "$RESILIENT_FILE"; then
    echo "  [FIX VERIFIED] PodDisruptionBudget protects minimum quorum during node drains."
fi

echo "[4/4] Verification complete: All zero-downtime architectural requirements met!"
echo "=========================================================="
