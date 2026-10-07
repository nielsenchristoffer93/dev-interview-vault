#!/usr/bin/env bash
# ==============================================================================
# 🚀 dev-interview-vault: Setup Prerequisites & Lab Infrastructure
# ==============================================================================
# Detta skript kontrollerar, installerar och startar verktyg och lab-infrastruktur:
#   1. Systemverktyg: curl, jq
#   2. Docker & Socket-rättigheter
#   3. Java (JDK 21+) & Maven
#   4. Node.js & npm (Frontend-labbar)
#   5. Kubernetes CLI: kubectl & minikube
#   6. Cloud CLI: terraform & aws
#   7. Startar Docker Compose (Postgres, Redis, LocalStack, Prometheus, Grafana)
#   8. (Valfritt) Startar Minikube med Ingress
# ==============================================================================

set -e

# Färgkoder för terminal
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$REPO_ROOT"

echo -e "${BOLD}${CYAN}======================================================================${NC}"
echo -e "${BOLD}${CYAN}🛠️  DEV-INTERVIEW-VAULT: Setup & Verifiering av Förutsättningar${NC}"
echo -e "${BOLD}${CYAN}======================================================================${NC}"

# Kontrollera kommandoradsflaggor
AUTO_STOP=false
WITH_MINIKUBE=false
for arg in "$@"; do
    case "$arg" in
        --auto-stop|-y)
            AUTO_STOP=true
            ;;
        --with-minikube)
            WITH_MINIKUBE=true
            ;;
    esac
done

# Hjälpfunktion för att kontrollera om ett kommando finns
check_cmd() {
    command -v "$1" >/dev/null 2>&1
}

# Hjälpfunktion för att kontrollera och frigöra portar
check_and_free_port() {
    local port="$1"
    local service_desc="$2"
    local expected_container_prefix="$3"

    # Kontrollera om en Docker-container publicerar porten
    local conflicting_containers
    conflicting_containers=$(docker ps --filter "publish=${port}" --format "{{.Names}}" 2>/dev/null || true)

    if [ -n "$conflicting_containers" ]; then
        for container in $conflicting_containers; do
            # Om det redan är vår egen container för labbet, är allt lugnt
            if [[ "$container" == "$expected_container_prefix"* ]]; then
                continue
            fi

            echo -e "${YELLOW}⚠️  Port ${port} (${service_desc}) är upptagen av Docker-containern '${container}'!${NC}"
            if [ "$AUTO_STOP" = "true" ]; then
                echo -e "${CYAN}   Stoppar containern '${container}' automatiskt...${NC}"
                docker stop "$container" >/dev/null 2>&1 || sudo docker stop "$container" >/dev/null 2>&1
                echo -e "${GREEN}✅ Stoppade '${container}'. Port ${port} är nu frigjord.${NC}"
            else
                read -r -p "   Vill du stoppa containern '${container}' för att frigöra port ${port}? [y/N]: " answer < /dev/tty || answer="n"
                if [[ "$answer" =~ ^[Yy]$ ]]; then
                    echo -e "${CYAN}   Stoppar containern '${container}'...${NC}"
                    docker stop "$container" >/dev/null 2>&1 || sudo docker stop "$container" >/dev/null 2>&1
                    echo -e "${GREEN}✅ Stoppade '${container}'. Port ${port} är nu frigjord.${NC}"
                else
                    echo -e "${YELLOW}   ⚠️ Hoppar över att stoppa '${container}'. Obs: ${service_desc} kommer troligen att krascha vid port-bindning!${NC}"
                fi
            fi
        done
        return
    fi

    # Kontrollera om en lokal process utanför Docker lyssnar på porten
    if timeout 1 bash -c "cat < /dev/null > /dev/tcp/127.0.0.1/${port}" 2>/dev/null; then
        local pid=""
        if check_cmd lsof; then
            pid=$(lsof -ti :"${port}" 2>/dev/null | head -n 1 || true)
        fi
        
        local proc_name="Okänd process"
        if [ -n "$pid" ]; then
            proc_name=$(ps -p "$pid" -o comm= 2>/dev/null || echo "PID $pid")
        fi

        echo -e "${YELLOW}⚠️  Port ${port} (${service_desc}) är upptagen av lokal process: ${proc_name} ${pid:+(PID: $pid)}!${NC}"
        if [ -n "$pid" ]; then
            if [ "$AUTO_STOP" = "true" ]; then
                echo -e "${CYAN}   Avslutar process ${pid}...${NC}"
                kill -15 "$pid" 2>/dev/null || sudo kill -15 "$pid" 2>/dev/null || true
                echo -e "${GREEN}✅ Avslutade process ${pid}.${NC}"
            else
                read -r -p "   Vill du avsluta processen ${proc_name} (PID: ${pid})? [y/N]: " answer < /dev/tty || answer="n"
                if [[ "$answer" =~ ^[Yy]$ ]]; then
                    kill -15 "$pid" 2>/dev/null || sudo kill -15 "$pid" 2>/dev/null || true
                    echo -e "${GREEN}✅ Avslutade process ${pid}.${NC}"
                else
                    echo -e "${YELLOW}   ⚠️ Hoppar över att avsluta processen.${NC}"
                fi
            fi
        fi
    fi
}

# ------------------------------------------------------------------------------
# 1. Systemverktyg: curl & jq
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[1/7] Kontrollerar basverktyg (curl, jq)...${NC}"
if ! check_cmd curl || ! check_cmd jq; then
    echo -e "${YELLOW}⚠️ Installerar curl och jq...${NC}"
    sudo apt-get update -qq && sudo apt-get install -y -qq curl jq
    echo -e "${GREEN}✅ Basverktyg installerade.${NC}"
else
    echo -e "${GREEN}✅ curl och jq är redan installerade.${NC}"
fi

# ------------------------------------------------------------------------------
# 2. Docker & Grupprättigheter
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[2/7] Kontrollerar Docker...${NC}"
if ! check_cmd docker; then
    echo -e "${RED}❌ Docker är inte installerat.${NC}"
    echo "Installera Docker via: sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2"
else
    # Kontrollera om docker daemon svarar utan sudo
    if docker info >/dev/null 2>&1; then
        echo -e "${GREEN}✅ Docker är igång och körs med korrekta användarrättigheter.${NC}"
    else
        echo -e "${YELLOW}⚠️ Användaren '$USER' saknar åtkomst till /var/run/docker.sock.${NC}"
        echo -e "   Lägger till $USER i gruppen 'docker'..."
        sudo usermod -aG docker "$USER" || true
        echo -e "${YELLOW}💡 OBS: Du kan behöva köra 'newgrp docker' eller starta om WSL för att ändringen ska slå igenom.${NC}"
    fi
fi

# ------------------------------------------------------------------------------
# 3. Java & Maven (Backend)
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[3/7] Kontrollerar Java & Maven...${NC}"
# Ladda SDKMAN om det finns i hemkatalogen
if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    source "$HOME/.sdkman/bin/sdkman-init.sh"
fi

if check_cmd java && check_cmd mvn; then
    JAVA_VER=$(java -version 2>&1 | head -n 1)
    MVN_VER=$(mvn -version 2>&1 | head -n 1)
    echo -e "${GREEN}✅ Java funnet: $JAVA_VER${NC}"
    echo -e "${GREEN}✅ Maven funnet: $MVN_VER${NC}"
else
    echo -e "${YELLOW}⚠️ Java eller Maven saknas i PATH.${NC}"
    echo "   Rekommendation: Installera via SDKMAN:"
    echo "   curl -s 'https://get.sdkman.io' | bash"
    echo "   sdk install java 21.0.2-tem && sdk install maven"
fi

# ------------------------------------------------------------------------------
# 4. Node.js & npm (Frontend)
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[4/7] Kontrollerar Node.js & npm...${NC}"
if check_cmd node && check_cmd npm; then
    echo -e "${GREEN}✅ Node.js funnet: $(node -v)${NC}"
    echo -e "${GREEN}✅ npm funnet: $(npm -v)${NC}"
else
    echo -e "${YELLOW}⚠️ Node.js saknas i WSL. Installerar Node.js LTS via apt...${NC}"
    sudo apt-get update -qq && sudo apt-get install -y -qq nodejs npm || true
    if check_cmd node; then
        echo -e "${GREEN}✅ Node.js installerat: $(node -v)${NC}"
    fi
fi

# ------------------------------------------------------------------------------
# 5. Kubernetes CLI: kubectl & minikube
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[5/7] Kontrollerar Kubernetes-verktyg (kubectl, minikube)...${NC}"

# Installera kubectl om det saknas
if ! check_cmd kubectl; then
    echo -e "${YELLOW}⚠️ kubectl saknas. Laddar ner senaste stabila versionen...${NC}"
    KUBECTL_VER=$(curl -L -s https://dl.k8s.io/release/stable.txt)
    curl -sLO "https://dl.k8s.io/release/${KUBECTL_VER}/bin/linux/amd64/kubectl"
    sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
    rm -f kubectl
    echo -e "${GREEN}✅ kubectl installerat framgångsrikt i /usr/local/bin/kubectl${NC}"
else
    echo -e "${GREEN}✅ kubectl är redan installerat.${NC}"
fi

# Installera minikube om det saknas
if ! check_cmd minikube; then
    echo -e "${YELLOW}⚠️ minikube saknas. Laddar ner officiell Linux amd64-binär...${NC}"
    curl -sLO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
    sudo install minikube-linux-amd64 /usr/local/bin/minikube
    rm -f minikube-linux-amd64
    echo -e "${GREEN}✅ minikube installerat framgångsrikt i /usr/local/bin/minikube${NC}"
else
    echo -e "${GREEN}✅ minikube är redan installerat.${NC}"
fi

# ------------------------------------------------------------------------------
# 6. Starta Docker Compose Infrastruktur
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[6/7] Startar Docker Compose lab-infrastruktur...${NC}"

# Identifiera vilket compose-kommando som är installerat (docker-compose vs docker compose)
if command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD="docker-compose"
elif docker compose version >/dev/null 2>&1; then
    COMPOSE_CMD="docker compose"
else
    echo -e "${RED}❌ Varken 'docker-compose' eller 'docker compose' hittades.${NC}"
    echo "Installera docker-compose eller docker-compose-plugin."
    exit 1
fi

# Kontrollera om sudo behövs för docker-socket
if ! docker info >/dev/null 2>&1; then
    if ! groups | grep -q docker; then
        echo -e "${YELLOW}⚠️ Användaren saknar åtkomst till /var/run/docker.sock. Kör med sudo...${NC}"
        COMPOSE_CMD="sudo $COMPOSE_CMD"
    fi
fi

# Kontrollera potentiella portkonflikter och erbjud användaren att stoppa dem
echo -e "   🔍 Kontrollerar om nödvändiga portar är lediga..."
check_and_free_port 5432 "PostgreSQL" "vault-postgres"
check_and_free_port 6379 "Redis" "vault-redis"
check_and_free_port 4566 "LocalStack" "vault-localstack"
check_and_free_port 9090 "Prometheus" "vault-prometheus"
check_and_free_port 3000 "Grafana" "vault-grafana"

echo -e "   🚀 Startar bas-tjänster (PostgreSQL, Redis, LocalStack) via '$COMPOSE_CMD'..."
$COMPOSE_CMD -f compose/docker-compose.base.yml up -d

echo -e "   🚀 Startar monitoring (Prometheus, Grafana) via '$COMPOSE_CMD'..."
$COMPOSE_CMD -f compose/docker-compose.monitoring.yml up -d

echo -e "${GREEN}✅ Alla Docker-containrar startade!${NC}"

# ------------------------------------------------------------------------------
# 7. Fråga om Minikube-start
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[7/7] Kubernetes-kluster (Minikube)...${NC}"
echo -e "Vill du starta Minikube nu med Ingress aktiverat? (tar ~1-2 minuter)"
echo -e "Kör manuellt när som helst med: ${CYAN}minikube start --driver=docker && minikube addons enable ingress${NC}"

# Kontrollera om Minikube ska startas
if [ "$WITH_MINIKUBE" = "true" ]; then
    echo -e "${CYAN}Startar Minikube automatiskt enligt flagga...${NC}"
    if docker info >/dev/null 2>&1; then
        minikube start --driver=docker
    else
        sg docker -c "minikube start --driver=docker"
    fi
    minikube addons enable ingress
    echo -e "${GREEN}✅ Minikube är igång och Ingress är aktiverat!${NC}"
fi

# ------------------------------------------------------------------------------
# Sammanfattning
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}${GREEN}======================================================================${NC}"
echo -e "${BOLD}${GREEN}🎉 ALLA FÖRUTSÄTTNINGAR ÄR KLARA FÖR INTERVJURIGGEN!${NC}"
echo -e "${BOLD}${GREEN}======================================================================${NC}"
echo -e "  🐘 PostgreSQL:     localhost:5432 (vault_user / vault_pass)"
echo -e "  🔴 Redis:          localhost:6379"
echo -e "  ☁️ LocalStack:     localhost:4566"
echo -e "  📈 Prometheus:     http://localhost:9090"
echo -e "  📊 Grafana:        http://localhost:3000 (admin / admin)"
echo -e "  ☸️ Kubernetes:     kubectl & minikube redo"
echo -e "======================================================================"
echo -e "Nästa steg:"
echo -e "  • Kör alla backend-tester:  ${CYAN}cd backend && mvn test${NC}"
echo -e "  • Starta resilience-appen:  ${CYAN}cd backend/01-spring-resilience && mvn spring-boot:run${NC}"
echo -e "  • Starta Minikube vid behov:${CYAN}minikube start --driver=docker && minikube addons enable ingress${NC}"
echo -e "======================================================================\n"
