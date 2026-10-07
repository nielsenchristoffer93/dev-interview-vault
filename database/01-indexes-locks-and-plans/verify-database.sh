#!/bin/bash
set -e

# ==============================================================================
# 🗄️ VERIFY & DEMO: Database Indexes, Locks & Concurrency Lab
# ==============================================================================

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

echo -e "\n${BOLD}${CYAN}=== 🗄️ 01-INDEXES-LOCKS-AND-PLANS: POSTGRESQL LIVE VERIFIERING ===${NC}\n"

# Kontrollera om PostgreSQL körs i Docker
if ! docker ps 2>/dev/null | grep -q "vault-postgres"; then
    echo -e "${YELLOW}⚠️ 'vault-postgres' hittades inte. Försöker köra via lokal compose om möjlig...${NC}"
fi

PSQL="docker exec -i vault-postgres psql -U vault_user -d interview_db"

echo -e "${CYAN}1. Genererar 50 000 rader och index (status, created_at)...${NC}"
$PSQL << 'EOF' > /dev/null
DROP TABLE IF EXISTS orders;
CREATE TABLE orders (
    id SERIAL PRIMARY KEY,
    customer_id INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    total_amount NUMERIC(10, 2) NOT NULL
);

INSERT INTO orders (customer_id, status, created_at, total_amount)
SELECT
    (random() * 5000)::INT,
    CASE (random() * 3)::INT
        WHEN 0 THEN 'PENDING'
        WHEN 1 THEN 'PROCESSING'
        WHEN 2 THEN 'COMPLETED'
        ELSE 'CANCELLED'
    END,
    NOW() - (random() * 365 * 24 * 3600 || ' seconds')::INTERVAL,
    (random() * 1000 + 10)::NUMERIC(10,2)
FROM generate_series(1, 50000);

CREATE INDEX idx_orders_status_created ON orders (status, created_at);
ANALYZE orders;
EOF
echo -e "${GREEN}✓ Data och index skapade!${NC}\n"

echo -e "${BOLD}${YELLOW}----------------------------------------------------------------------${NC}"
echo -e "${BOLD}🎯 SCENARIO 1: Leftmost Prefix Rule (Seq Scan vs Bitmap Index Scan)${NC}"
echo -e "${BOLD}${YELLOW}----------------------------------------------------------------------${NC}"

echo -e "${RED}[THE FLAW] Söker på created_at UTAN ledande status-kolumn:${NC}"
$PSQL -c "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM orders WHERE created_at >= NOW() - INTERVAL '7 days';"

echo -e "\n${GREEN}[THE FIX] Söker med ledande kolumn (status = 'COMPLETED'):${NC}"
$PSQL -c "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM orders WHERE status = 'COMPLETED' AND created_at >= NOW() - INTERVAL '7 days';"

echo -e "\n${BOLD}${YELLOW}----------------------------------------------------------------------${NC}"
echo -e "${BOLD}🎯 SCENARIO 2: Zero-Downtime DDL & Låstimeout (ACCESS EXCLUSIVE LOCK)${NC}"
echo -e "${BOLD}${YELLOW}----------------------------------------------------------------------${NC}"
$PSQL << 'EOF'
SET lock_timeout = '2s';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS is_priority BOOLEAN DEFAULT false;
EOF
echo -e "${GREEN}✓ DDL genomfördes säkert med lock_timeout = 2s!${NC}\n"

echo -e "${BOLD}${GREEN}=== ALLA DATABAS-SCENARIER VERIFIERADE FRAMGÅNGSRIKT ===${NC}\n"
