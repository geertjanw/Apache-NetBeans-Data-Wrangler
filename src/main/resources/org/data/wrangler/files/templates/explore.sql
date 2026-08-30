-- ${name}.sql  (${date})
-- Explore data with DuckDB. Runs as-is on inline sample data; swap in a file when ready.
-- Run statement by statement (Ctrl+Shift+E on each).

-- 1. Source. A VIEW is a saved query: every step below reads from "src", so changing this
--    one statement (inline rows today, a file tomorrow) changes what all the others see.
--    VALUES builds rows inline; the alias t(...) names the columns.
CREATE OR REPLACE VIEW src AS
SELECT * FROM (VALUES
    (1, 'Alice', 'Utrecht', 10.50, DATE '2026-01-01'),   -- DATE 'yyyy-mm-dd' is a typed date literal
    (2, 'Bob',   'Delft',   20.00, DATE '2026-01-02'),
    (3, 'Carol', 'Utrecht',  5.25, DATE '2026-01-03'),
    (4, 'Dave',  'Delft',   40.00, DATE '2026-01-04')
) AS t(id, name, city, amount, created_at);

--    ...or a file. DuckDB treats a quoted path as a table and picks the reader by extension
--    (CSV, Parquet, JSON). Globs like 'data/*.parquet' read many files as one table.
-- CREATE OR REPLACE VIEW src AS SELECT * FROM 'path/to/data.csv';

-- 2. Shape: column names and the types DuckDB inferred (VALUES -> INTEGER/VARCHAR/DECIMAL/DATE).
DESCRIBE src;

-- 3. One row per column with min, max, approximate distinct count, average, std, quartiles
--    and null percentage. The fastest way to see what a dataset looks like.
SUMMARIZE src;

-- 4. Peek at rows. FROM-first syntax: SELECT * is implied.
FROM src LIMIT 20;

-- 5. Aggregate. GROUP BY ALL = group by every column that is not inside an aggregate (city here).
SELECT city, count(*) AS n, avg(amount) AS avg_amount
FROM src
GROUP BY ALL
ORDER BY n DESC;

-- 6. Top-1 per group. row_number() numbers rows within each city, highest amount first;
--    QUALIFY keeps only number 1, without wrapping the query in a subquery.
SELECT * FROM src
QUALIFY row_number() OVER (PARTITION BY city ORDER BY amount DESC) = 1;
