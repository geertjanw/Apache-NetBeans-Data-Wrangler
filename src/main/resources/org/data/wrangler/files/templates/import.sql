-- ${name}.sql  (${date})
-- Import files into DuckDB tables. Works for CSV, Parquet, JSON and globs.
-- CREATE TABLE ... AS SELECT (CTAS) runs the query once and stores the result as a real table,
-- so later queries do not re-read the file.

-- CSV. read_csv sniffs the delimiter, quoting and column types; header = true takes column
-- names from the first line. Pass delim = '\t' for TSV, or types = {'col': 'DATE'} to override a guess.
CREATE OR REPLACE TABLE csv_data AS
SELECT * FROM read_csv('path/to/data.csv', header = true, auto_detect = true);

-- Parquet. A glob reads every matching file as one table; filename = true adds a column
-- with the source file so you can tell rows apart afterwards. Only referenced columns are read.
CREATE OR REPLACE TABLE parquet_data AS
SELECT *, filename FROM read_parquet('path/to/*.parquet', filename = true);

-- JSON. read_json_auto handles both an array of objects and newline-delimited JSON,
-- and infers a schema (nested objects become STRUCTs, arrays become LISTs).
CREATE OR REPLACE TABLE json_data AS
SELECT * FROM read_json_auto('path/to/data.json');

-- Row counts for each imported table, stacked with UNION ALL (keeps duplicates, no sort).
SELECT 'csv' AS source, count(*) FROM csv_data
UNION ALL SELECT 'parquet', count(*) FROM parquet_data
UNION ALL SELECT 'json', count(*) FROM json_data;
