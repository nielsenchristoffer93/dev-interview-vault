-- ==============================================================================
-- THE FLAW: Vanliga produktionsmisstag i databaser
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Saknad ledande kolumn i sammansatt B-Tree index (Leftmost Prefix Rule)
-- ------------------------------------------------------------------------------
-- Förutsättning: Index skapat på (status, created_at)
-- CREATE INDEX idx_orders_status_created ON orders (status, created_at);

-- FARA: Frågan filtrerar enbart på created_at utan 'status'.
-- B-Tree-indexet kan inte användas för direkt sökning -> Full Seq Scan!
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM orders
WHERE created_at >= NOW() - INTERVAL '7 days';


-- ------------------------------------------------------------------------------
-- 2. Farlig DDL utan lock_timeout (Blockerar hela trafiken)
-- ------------------------------------------------------------------------------
-- FARA: Kräver ACCESS EXCLUSIVE LOCK. Om en långsam SELECT pågår blockeras denna.
-- Och ALLA efterföljande SELECT/INSERT fastnar bakom denna i kön -> Total krasch!
ALTER TABLE orders ADD COLUMN is_priority BOOLEAN DEFAULT false NOT NULL;


-- ------------------------------------------------------------------------------
-- 3. Write Skew under READ COMMITTED / REPEATABLE READ
-- ------------------------------------------------------------------------------
-- Förutsättning: Invariant "Minst 1 läkare på jour" (just nu 2 aktiva läkare: doc_1 och doc_2).
-- Transaktion A (Dr Alice vill gå hem):
BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT COUNT(*) FROM on_call_doctors WHERE is_on_call = true; -- Returnerar 2
UPDATE on_call_doctors SET is_on_call = false WHERE id = 1;
-- (Transaktion B kör exakt samtidigt i en annan session):
-- BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;
-- SELECT COUNT(*) FROM on_call_doctors WHERE is_on_call = true; -- Returnerar också 2!
-- UPDATE on_call_doctors SET is_on_call = false WHERE id = 2;
-- COMMIT;
COMMIT;
-- Resultat: 0 aktiva läkare kvar! Invarianten krossad!
