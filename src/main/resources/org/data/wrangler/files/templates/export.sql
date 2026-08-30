-- ${name}.sql  (${date})
-- Export data from DuckDB with COPY ... TO. The options in parentheses control the format.

-- Any query to one Parquet file. Parquet keeps types (dates, decimals, lists) and compresses well;
-- zstd is smaller than the default snappy, slightly slower to write.
COPY (SELECT * FROM my_table) TO 'out/my_table.parquet' (FORMAT parquet, COMPRESSION zstd);

-- A whole table to CSV. HEADER writes column names on the first line. Everything becomes text,
-- so types are lost; use this for spreadsheets and other tools, Parquet for anything else.
COPY my_table TO 'out/my_table.csv' (HEADER, DELIMITER ',');

-- Partitioned output: one subfolder per distinct value of the PARTITION_BY column
-- (out/partitioned/year=2026/...). Readers that understand Hive partitioning can then skip folders.
-- COPY my_table TO 'out/partitioned' (FORMAT parquet, PARTITION_BY (year));

-- The entire database: a schema.sql with the DDL plus one Parquet file per table.
-- Restore with IMPORT DATABASE 'out/backup'.
-- EXPORT DATABASE 'out/backup' (FORMAT parquet);
