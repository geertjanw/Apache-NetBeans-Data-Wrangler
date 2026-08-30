-- ${name}.sql  (${date})
-- DuckDB query. Select the DuckDB connection in the toolbar above, then run with Ctrl+Shift+E.
-- Tip: code templates -> type dqual, dpiv, dasof, dcsv ... and press Tab.

-- Sample table: uncomment this block and run it once so there is something to query.
-- CREATE OR REPLACE TABLE creates the table, or replaces it if it already exists (safe to re-run).
-- CREATE OR REPLACE TABLE orders (
--     order_id    INTEGER,          -- plain integer key
--     customer    VARCHAR,          -- DuckDB strings are VARCHAR; no length needed
--     city        VARCHAR,
--     product     VARCHAR,
--     quantity    INTEGER,
--     unit_price  DECIMAL(10,2),    -- exact decimal: 10 digits, 2 after the point
--     ordered_at  TIMESTAMP,        -- date + time, no time zone
--     tags        VARCHAR[]         -- a LIST of strings (DuckDB nested type)
-- );
-- INSERT INTO orders VALUES         -- one tuple per row; strings quote with '', lists with [ ]
--     (1001, 'Alice', 'Utrecht',   'Keyboard',  1,  49.99, '2026-01-03 09:15', ['hardware']),
--     (1002, 'Bob',   'Delft',     'Monitor',   2, 189.00, '2026-01-03 11:40', ['hardware', 'display']),
--     (1003, 'Alice', 'Utrecht',   'USB-C hub', 1,  29.50, '2026-01-05 14:02', ['hardware']),
--     (1004, 'Carol', 'Amsterdam', 'Laptop',    1, 999.00, '2026-01-07 16:30', ['hardware', 'laptop']),
--     (1005, 'Dave',  'Delft',     'Mouse',     3,  19.95, '2026-01-08 10:05', ['hardware']),
--     (1006, 'Carol', 'Amsterdam', 'Monitor',   1, 189.00, '2026-01-10 13:20', ['hardware', 'display']),
--     (1007, 'Erin',  'Utrecht',   'Webcam',    1,  59.00, '2026-01-12 08:55', ['hardware', 'video']),
--     (1008, 'Bob',   'Delft',     'Keyboard',  2,  49.99, '2026-01-15 17:45', ['hardware']);

FROM orders          -- DuckDB lets a query start with FROM; SELECT * is implied if omitted
SELECT *             -- all columns (try: * EXCLUDE (tags)  or  * REPLACE (upper(city) AS city))
LIMIT 100;           -- cap the rows returned; drop it for the full table

-- Ideas (uncomment one at a time). Expected results are computed from the 8 rows above.

-- Revenue per city. quantity * unit_price is the line total; sum() adds them up per city.
-- GROUP BY ALL groups by every non-aggregated column (here: city), so you never list them twice.
-- Expected, highest first:
--   Amsterdam  1188.00   (Carol's laptop 999.00 + monitor 189.00)
--   Delft       537.83   (Bob's 2 monitors 378.00 + Dave's 3 mice 59.85 + Bob's 2 keyboards 99.98)
--   Utrecht     138.49   (Alice's keyboard 49.99 + hub 29.50 + Erin's webcam 59.00)
-- SELECT city, sum(quantity * unit_price) AS revenue FROM orders GROUP BY ALL ORDER BY revenue DESC;

-- Priciest order per city. row_number() numbers each city's orders, highest unit_price first;
-- QUALIFY keeps only number 1, without a subquery. EXCLUDE (tags) drops the list column.
-- Expected, one row per city:
--   1004  Carol  Amsterdam  Laptop   1  999.00  2026-01-07 16:30
--   1002  Bob    Delft      Monitor  2  189.00  2026-01-03 11:40   (beats the 49.99 keyboards and 19.95 mice)
--   1007  Erin   Utrecht    Webcam   1   59.00  2026-01-12 08:55   (beats the 49.99 keyboard and 29.50 hub)
-- SELECT * EXCLUDE (tags) FROM orders QUALIFY row_number() OVER (PARTITION BY city ORDER BY unit_price DESC) = 1;

-- Cross-tab: one row per product, one column per city, cells = revenue. DuckDB discovers the
-- city values itself; a combination with no orders is NULL.
-- Expected:
--   product    Amsterdam   Delft    Utrecht
--   Keyboard        NULL   99.98      49.99
--   Laptop        999.00    NULL       NULL
--   Monitor       189.00  378.00       NULL
--   Mouse           NULL   59.85       NULL
--   USB-C hub       NULL    NULL      29.50
--   Webcam          NULL    NULL      59.00
-- PIVOT orders ON city USING sum(quantity * unit_price) GROUP BY product;
