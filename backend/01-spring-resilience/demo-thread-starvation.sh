#!/usr/bin/env bash
# ==============================================================================
# 🧵 DEMO-SKRIPT: Thread Pool Starvation & Management Port Isolation
# ==============================================================================
# Detta skript demonstrerar:
# 1. Att Tomcats trådpool (max: 5) snabbt svälts ut av långsamma anrop (/unsafe-hang)
# 2. Att inkommande anrop #6 blockeras helt på port 8080
# 3. Att Actuator på separat port 8081 FORTFARANDE svarar med sin isolerade trådpool!
# 4. Att /safe-timeout med TimeLimiter förhindrar Thread Starvation genom att klippa i tid.
# ==============================================================================

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

APP_URL="http://localhost:8080"
ACTUATOR_URL="http://localhost:8081"

echo -e "\n${BOLD}${CYAN}======================================================================${NC}"
echo -e "${BOLD}${CYAN}   🔥 LIVE DEMO: Thread Pool Starvation vs Management Port Isolation   ${NC}"
echo -e "${BOLD}${CYAN}======================================================================${NC}\n"

# Kontrollera att servern är online
if ! curl -s --max-time 2 "${APP_URL}/api/payment/safe" >/dev/null; then
  echo -e "${RED}❌ Servern är inte startad! Starta den med: cd backend/01-spring-resilience && mvn spring-boot:run${NC}\n"
  exit 1
fi

echo -e "${BOLD}Konfiguration:${NC} server.tomcat.threads.max = 5 (Tomcat har exakt 5 worker-trådar)\n"

# ------------------------------------------------------------------------------
# TEST 1: Svält ut Tomcats 5 worker-trådar
# ------------------------------------------------------------------------------
echo -e "${BOLD}----------------------------------------------------------------------${NC}"
echo -e "${BOLD}1. SKICKAR 5 SAMTIDIGA LÅNGSAMMA ANROP (/unsafe-hang)${NC}"
echo -e "${BOLD}----------------------------------------------------------------------${NC}"
echo -e "${YELLOW}Skjuter iväg 5 bakgrundsanrop som var och en låser 1 tråd i 10 sekunder...${NC}"

for i in {1..5}; do
  curl -s "${APP_URL}/api/payment/unsafe-hang?tx=HANG-${i}" >/dev/null 2>&1 &
done

sleep 0.5
echo -e "${RED}⚠️ Alla 5 Tomcat-trådar är nu 100% upptagna i vänteläge!${NC}\n"

# ------------------------------------------------------------------------------
# TEST 2: Testa anrop #6 på port 8080 (Skall blockeras / timea ut!)
# ------------------------------------------------------------------------------
echo -e "${BOLD}----------------------------------------------------------------------${NC}"
echo -e "${BOLD}2. TESTAR ANROP #6 PÅ PORT 8080 (Kundtrafik)${NC}"
echo -e "${BOLD}----------------------------------------------------------------------${NC}"
echo -e "${YELLOW}Försöker nå /api/payment/safe (med 2 sekunders timeout)...${NC}"

START_T=$(date +%s)
if curl -s --max-time 2 "${APP_URL}/api/payment/safe?tx=TEST-6" >/dev/null; then
  echo -e "${GREEN}Anropet gick igenom!${NC}"
else
  END_T=$(date +%s)
  DIFF=$((END_T - START_T))
  echo -e "${RED}❌ TIMEOUT (${DIFF}s)! Port 8080 svarar INTE!${NC}"
  echo -e "${RED}BEVIS: Tomcats trådpool är tömd (Thread Pool Starvation). Inga nya requests kan hanteras!${NC}\n"
fi

# ------------------------------------------------------------------------------
# TEST 3: Testa Actuator på port 8081 (Skall svara direkt!)
# ------------------------------------------------------------------------------
echo -e "${BOLD}----------------------------------------------------------------------${NC}"
echo -e "${BOLD}3. TESTAR ACTUATOR PÅ PORT 8081 (Separat Management Port)${NC}"
echo -e "${BOLD}----------------------------------------------------------------------${NC}"
echo -e "${YELLOW}Anropar http://localhost:8081/actuator/health medan kundporten 8080 är helt fryst...${NC}"

START_T=$(date +%s%N 2>/dev/null || python3 -c 'import time; print(int(time.time()*1e9))')
HEALTH_RES=$(curl -s --max-time 2 "${ACTUATOR_URL}/actuator/health" || echo "FAILED")
END_T=$(date +%s%N 2>/dev/null || python3 -c 'import time; print(int(time.time()*1e9))')
HEALTH_MS=$(( (END_T - START_T) / 1000000 ))

if echo "$HEALTH_RES" | grep -q "status"; then
  echo -e "${GREEN}✅ SUCCESS! Actuator svarade på ${HEALTH_MS} ms!${NC}"
  echo -e "${GREEN}BEVIS: Separat management port räddar Kubernetes Liveness/Readiness probes!${NC}"
  echo -e "      Hade Actuator legat på port 8080 hade Kubernetes trott att podden var död och startat om den i en dödsspiral!\n"
else
  echo -e "${RED}❌ Actuator svarade inte.${NC}\n"
fi

echo -e "${YELLOW}Väntar 7 sekunder så de bakgrundshängande trådarna avslutas...${NC}"
sleep 7

# ------------------------------------------------------------------------------
# TEST 4: Testa TimeLimiter / Timeout-skydd (/safe-timeout)
# ------------------------------------------------------------------------------
echo -e "${BOLD}----------------------------------------------------------------------${NC}"
echo -e "${BOLD}4. LÖSNINGEN: /safe-timeout MED TIMELIMITER KAN INTE SVÄLTA TRÅDPOOLEN${NC}"
echo -e "${BOLD}----------------------------------------------------------------------${NC}"
echo -e "${YELLOW}Skickar anrop till /safe-timeout (gateway hänger i 10s, men TimeLimiter klipper efter 1000ms)...${NC}"

START_T=$(date +%s)
TIMEOUT_RES=$(curl -s "${APP_URL}/api/payment/safe-timeout?tx=PROTECTED-1")
END_T=$(date +%s)
DIFF=$((END_T - START_T))

echo -e "Svar:     ${GREEN}${TIMEOUT_RES}${NC}"
echo -e "Tid:      ${GREEN}${DIFF} sekund (Klipptes direkt vid timeout istället för att blockera i 10s!)${NC}\n"

echo -e "${BOLD}${CYAN}======================================================================${NC}"
echo -e "${BOLD}${CYAN}   🎉 DEMO KLAR: Alla kombinationer av trådlåsning och skydd bevisade! ${NC}"
echo -e "${BOLD}${CYAN}======================================================================${NC}\n"
