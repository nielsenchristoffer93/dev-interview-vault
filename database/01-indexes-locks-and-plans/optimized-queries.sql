-- ==============================================================================
-- THE FIX: Seniora databaslösningar och best practices
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Korrekt Leftmost Prefix Rule & Covering Index
-- ------------------------------------------------------------------------------
-- A. Matchar ledande kolumn:
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM orders
WHERE status = 'COMPLETED' AND created_at >= NOW() - INTERVAL '7 days';

-- B. Skapa dedikerat index om sökning enbart sker på created_at:
-- Använd CONCURRENTLY för att slippa låsa tabellen i produktion!
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_created_at ON orders (created_at);

-- C. Covering Index (Index Only Scan) med INCLUDE för att slippa läsa heap-tabellen:
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_orders_customer_covering 
ON orders (customer_id) INCLUDE (total_amount, status);


-- ------------------------------------------------------------------------------
-- 2. Säker Zero-Downtime DDL med lock_timeout
-- ------------------------------------------------------------------------------
-- Sätt aggressiv lock_timeout så att migrationen avbryts omedelbart om tabellen är låst,
-- istället för att ställa sig i kön och blockera applikationens anslutningspool.
SET lock_timeout = '2s';

-- Genomför DDL säkert:
ALTER TABLE orders ADD COLUMN IF NOT EXISTS is_priority BOOLEAN DEFAULT false;


-- ------------------------------------------------------------------------------
-- 3. Förhindra Write Skew med SELECT ... FOR UPDATE (eller SERIALIZABLE)
-- ------------------------------------------------------------------------------
-- Lösning: Radlåsning serialiserar transaktionerna och skyddar invarianten.
BEGIN;

-- Lås de rader som beslutet baseras på:
SELECT id FROM on_call_doctors WHERE is_on_call = true FOR UPDATE;

-- Om räkningen visar >= 2:
UPDATE on_call_doctors SET is_on_call = false WHERE id = 1;

COMMIT;
