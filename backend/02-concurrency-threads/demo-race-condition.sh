#!/usr/bin/env bash
# ==============================================================================
# DEMO: Simulera Race Condition live över HTTP med samtidiga anrop
# ==============================================================================

set -e

# Färger
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

BASE_URL="http://localhost:8080"
TOTAL_REQUESTS=1000
CONCURRENCY=50

echo -e "${BOLD}${CYAN}======================================================================${NC}"
echo -e "${BOLD}${CYAN}🏁 DEMO: Race Conditions & Lost Updates i Spring Boot${NC}"
echo -e "${BOLD}${CYAN}======================================================================${NC}"

# Kontrollera om servern är igång
if ! curl -s --max-time 1 "${BASE_URL}/api/orders/unsafe/count" >/dev/null 2>&1; then
    echo -e "${RED}❌ Servern svarar inte på ${BASE_URL}.${NC}"
    echo -e "Starta Spring Boot-servern i en separat terminal först:"
    echo -e "  ${CYAN}cd backend/02-concurrency-threads && mvn spring-boot:run${NC}"
    exit 1
fi

echo -e "✅ Servern körs på ${BASE_URL}."

# ------------------------------------------------------------------------------
# TEST 1: THE FLAW (Oskyddad primitiv int i Singleton Service)
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}${YELLOW}1. TESTAR THE FLAW (/api/orders/unsafe/increment)${NC}"
echo -e "   Nollställer räknaren..."
curl -s -X POST "${BASE_URL}/api/orders/reset" >/dev/null

echo -e "   Skickar ${TOTAL_REQUESTS} samtidiga anrop..."
if command -v hey >/dev/null 2>&1; then
    hey -n ${TOTAL_REQUESTS} -c ${CONCURRENCY} "${BASE_URL}/api/orders/unsafe/increment" >/dev/null 2>&1
else
    # Fallback till parallella curl-anrop i bakgrunden
    for ((i=1; i<=TOTAL_REQUESTS; i++)); do
        curl -s "${BASE_URL}/api/orders/unsafe/increment" >/dev/null &
        if (( i % CONCURRENCY == 0 )); then
            wait
        fi
    done
    wait
fi

UNSAFE_RESULT=$(curl -s "${BASE_URL}/api/orders/unsafe/count" | grep -o '"count":[0-9]*' | grep -o '[0-9]*')
LOST_UPDATES=$((TOTAL_REQUESTS - UNSAFE_RESULT))

echo -e "   ------------------------------------------------------------"
echo -e "   Förväntat antal:  ${BOLD}${TOTAL_REQUESTS}${NC}"
echo -e "   Faktiskt räknat:  ${BOLD}${RED}${UNSAFE_RESULT}${NC}"
if [ "$LOST_UPDATES" -gt 0 ]; then
    echo -e "   ${RED}❌ RACE CONDITION BEVISAD! ${LOST_UPDATES} uppdateringar försvann i tomma intet!${NC}"
else
    echo -e "   ${YELLOW}Alla anrop hanns med (prova att öka lasten eller kör igen).${NC}"
fi

# ------------------------------------------------------------------------------
# TEST 2: THE FIX (AtomicLong med lock-free Compare-And-Swap)
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}${GREEN}2. TESTAR THE FIX (/api/orders/safe/increment)${NC}"
echo -e "   Nollställer räknaren..."
curl -s -X POST "${BASE_URL}/api/orders/reset" >/dev/null

echo -e "   Skickar ${TOTAL_REQUESTS} samtidiga anrop..."
if command -v hey >/dev/null 2>&1; then
    hey -n ${TOTAL_REQUESTS} -c ${CONCURRENCY} "${BASE_URL}/api/orders/safe/increment" >/dev/null 2>&1
else
    for ((i=1; i<=TOTAL_REQUESTS; i++)); do
        curl -s "${BASE_URL}/api/orders/safe/increment" >/dev/null &
        if (( i % CONCURRENCY == 0 )); then
            wait
        fi
    done
    wait
fi

SAFE_RESULT=$(curl -s "${BASE_URL}/api/orders/safe/count" | grep -o '"count":[0-9]*' | grep -o '[0-9]*')

echo -e "   ------------------------------------------------------------"
echo -e "   Förväntat antal:  ${BOLD}${TOTAL_REQUESTS}${NC}"
echo -e "   Faktiskt räknat:  ${BOLD}${GREEN}${SAFE_RESULT}${NC}"
if [ "$SAFE_RESULT" -eq "$TOTAL_REQUESTS" ]; then
    echo -e "   ${GREEN}✅ TRÅDSÄKERHET VERIFIERAD! AtomicLong behöll exakt 100% av anropen.${NC}"
else
    echo -e "   ${RED}Något oväntat fel inträffade.${NC}"
fi
echo -e "======================================================================\n"
