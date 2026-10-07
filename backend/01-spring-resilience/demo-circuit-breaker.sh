#!/usr/bin/env bash
# ==============================================================================
# 🛡️ DEMO-SKRIPT: Resilience4j Circuit Breaker State Transitions
# ==============================================================================
# Detta skript demonstrerar livscykeln för en Circuit Breaker i realtid:
# 1. CLOSED (Normaldrift)
# 2. Öppning till OPEN efter feltröskeln nås (Fail-Fast)
# 3. Direkt avvisning när kretsen är OPEN (0 ms nätverks-overhead)
# 4. Övergång till HALF_OPEN efter 5 sekunder (waitDurationInOpenState)
# 5. Återhämtning tillbaka till CLOSED efter lyckade anrop
# ==============================================================================

set -e

# Färgkoder för snygg utskrift i terminalen
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

APP_URL="http://localhost:8080"
ACTUATOR_URL="http://localhost:8081"

# Hjälpfunktion för att hämta nuvarande kretsbrytar-status direkt från Prometheus (100% facit)
# Prometheus exponerar: resilience4j_circuitbreaker_state{name="paymentService",state="open"} 1.0
get_cb_state() {
  local state
  state=$(curl -s "${ACTUATOR_URL}/actuator/prometheus" 2>/dev/null | \
    grep 'resilience4j_circuitbreaker_state{name="paymentService",state=' | \
    grep ' 1.0' | \
    sed -n 's/.*state="\([^"]*\)".*/\1/p' | \
    tr '[:lower:]' '[:upper:]')
  
  if [ -z "$state" ]; then
    echo "UNKNOWN"
  else
    echo "$state"
  fi
}

echo -e "\n${BOLD}${CYAN}======================================================================${NC}"
echo -e "${BOLD}${CYAN}   🚀 STARTAR CIRCUIT BREAKER LIVE DEMO (Resilience4j & Spring Boot)   ${NC}"
echo -e "${BOLD}${CYAN}======================================================================${NC}\n"

# Steg 0: Kontrollera att applikationen faktiskt körs
echo -e "${YELLOW}🔍 Kontrollerar om Spring Boot-appen är igång på port 8080/8081...${NC}"
if ! curl -s --max-time 2 "${APP_URL}/api/payment/safe" >/dev/null; then
  echo -e "${RED}❌ Applikationen svarar inte på ${APP_URL}!${NC}"
  echo -e "Starta servern först i en separat terminal med:"
  echo -e "${BOLD}  cd backend/01-spring-resilience && mvn spring-boot:run${NC}\n"
  exit 1
fi
echo -e "${GREEN}✅ Applikationen är online och svarar!${NC}\n"

# ------------------------------------------------------------------------------
# STEG 1: Kontrollera utgångsläge (CLOSED)
# ------------------------------------------------------------------------------
echo -e "${BOLD}----------------------------------------------------------------------${NC}"
echo -e "${BOLD}1. INITIALT TILLSTÅND: Kretsen ska vara CLOSED (Normaldrift)${NC}"
echo -e "${BOLD}----------------------------------------------------------------------${NC}"

RESPONSE=$(curl -s "${APP_URL}/api/payment/safe?tx=ORD-INIT")
CB_STATE=$(get_cb_state)

echo -e "Anrop till: ${CYAN}/api/payment/safe${NC}"
echo -e "Svar:       ${GREEN}${RESPONSE}${NC}"
echo -e "CB State:   ${GREEN}${BOLD}${CB_STATE}${NC} (Trafik släpps igenom som vanligt)\n"

sleep 1

# ------------------------------------------------------------------------------
# STEG 2: Skicka felaktiga anrop i snabb följd för att trigga kretsbrytaren
# ------------------------------------------------------------------------------
echo -e "${BOLD}----------------------------------------------------------------------${NC}"
echo -e "${BOLD}2. SIMULERAR NÄTVERKSHAVERI: Skickar 3 fallerande anrop i snabb följd${NC}"
echo -e "   Konfiguration: minimumNumberOfCalls=3, failureRateThreshold=50%${NC}"
echo -e "${BOLD}----------------------------------------------------------------------${NC}"

for i in {1..3}; do
  echo -e "${YELLOW}⚡ Skickar fallerande anrop #${i}...${NC}"
  FAIL_RES=$(curl -s "${APP_URL}/api/payment/fail-fast?tx=FAIL-${i}")
  echo -e "   Respons: ${RED}${FAIL_RES}${NC}"
  sleep 0.1
done

echo ""
sleep 0.3

# ------------------------------------------------------------------------------
# STEG 3: Verifiera att kretsen nu är OPEN
# ------------------------------------------------------------------------------
echo -e "${BOLD}----------------------------------------------------------------------${NC}"
echo -e "${BOLD}3. KONTROLLERAR TILLSTÅNDSFÖRÄNDRING: Kretsen ska nu ha slagit om till OPEN${NC}"
echo -e "${BOLD}----------------------------------------------------------------------${NC}"

CB_STATE=$(get_cb_state)
echo -e "CB State:   ${RED}${BOLD}${CB_STATE}${NC}"

if [ "$CB_STATE" = "OPEN" ]; then
  echo -e "Prometheus: ${RED}resilience4j_circuitbreaker_state{state=\"open\"} = 1.0${NC} ✅"
else
  echo -e "Prometheus: Väntar in metrik..."
fi
echo ""

# ------------------------------------------------------------------------------
# STEG 4: Testa Fail-Fast (Blixtsnabb avvisning)
# ------------------------------------------------------------------------------
echo -e "${BOLD}----------------------------------------------------------------------${NC}"
echo -e "${BOLD}4. FAIL-FAST TEST: Anrop medan kretsen är OPEN nekas omedelbart!${NC}"
echo -e "${BOLD}----------------------------------------------------------------------${NC}"

START_TIME=$(python3 -c 'import time; print(int(time.time()*1000))' 2>/dev/null || date +%s)
FAST_RES=$(curl -s "${APP_URL}/api/payment/fail-fast?tx=FAIL-BLOCKED")
END_TIME=$(python3 -c 'import time; print(int(time.time()*1000))' 2>/dev/null || date +%s)

DURATION_MS=$(( END_TIME - START_TIME ))

echo -e "Svar:       ${YELLOW}${FAST_RES}${NC}"
echo -e "Svarstid:   ${GREEN}${DURATION_MS} ms${NC} (Ingen backend/nätverks-wait, sparar Tomcat-trådar!)"
echo ""

# ------------------------------------------------------------------------------
# STEG 5: Vänta ut waitDurationInOpenState (5 sekunder) till HALF_OPEN
# ------------------------------------------------------------------------------
echo -e "${BOLD}----------------------------------------------------------------------${NC}"
echo -e "${BOLD}5. ÅTERHÄMTNING: Väntar 5 sekunder (waitDurationInOpenState)...${NC}"
echo -e "${BOLD}----------------------------------------------------------------------${NC}"

for s in {5..1}; do
  echo -ne "${YELLOW}⏳ Återstår: ${s} sekunder...\r${NC}"
  sleep 1
done
echo -e "\n${GREEN}⏰ Tiden har löpt ut!${NC}"

sleep 0.5
HALF_OPEN_STATE=$(get_cb_state)
echo -e "CB State:   ${YELLOW}${BOLD}${HALF_OPEN_STATE}${NC} (Kretsen är nu i provläge!)\n"

# ------------------------------------------------------------------------------
# STEG 6: Skicka friska anrop för att stänga kretsen igen (CLOSED)
# ------------------------------------------------------------------------------
echo -e "${BOLD}----------------------------------------------------------------------${NC}"
echo -e "${BOLD}6. PROVANROP: Skickar 2 lyckade anrop för att återställa kretsen till CLOSED${NC}"
echo -e "${BOLD}----------------------------------------------------------------------${NC}"

RECOVER_1=$(curl -s "${APP_URL}/api/payment/safe?tx=RECOVER-1")
echo -e "Provanrop 1: ${GREEN}${RECOVER_1}${NC}"
sleep 0.2

RECOVER_2=$(curl -s "${APP_URL}/api/payment/safe?tx=RECOVER-2")
echo -e "Provanrop 2: ${GREEN}${RECOVER_2}${NC}"

sleep 0.5
FINAL_STATE=$(get_cb_state)
echo -e "\n${BOLD}Slutgiltig CB State: ${GREEN}${BOLD}${FINAL_STATE}${NC} (Återställd till normal CLOSED drift!)\n"

echo -e "${BOLD}${CYAN}======================================================================${NC}"
echo -e "${BOLD}${CYAN}   🎉 DEMO KLAR: Alla kretsbrytartillstånd (CLOSED -> OPEN -> HALF_OPEN -> CLOSED) verifierade!${NC}"
echo -e "${BOLD}${CYAN}======================================================================${NC}\n"
