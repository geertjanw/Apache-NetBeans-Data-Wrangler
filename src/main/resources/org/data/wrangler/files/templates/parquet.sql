-- ${name}.sql  (${date})
-- Query Parquet files directly; no import needed.
-- Tip: New File > Analytics > Sample Parquet File creates sample.parquet next to this script; adjust the paths below.

-- Columns and their Parquet types, straight from the file footer (no data is read).
SELECT * FROM parquet_schema('sample.parquet');

-- Physical layout: Parquet stores rows in row groups; each has statistics DuckDB uses to skip
-- groups that cannot match a WHERE clause. Compressed size shows what zstd/snappy achieved.
SELECT row_group_id, num_rows, total_compressed_size FROM parquet_metadata('sample.parquet');

-- Read the file. Parquet is columnar, so only the columns you SELECT are actually read;
-- a WHERE on a column with statistics can skip whole row groups.
SELECT *
FROM read_parquet('sample.parquet')
LIMIT 100;

-- Many files at once. ** recurses into subfolders. hive_partitioning = true turns folder names
-- like year=2026/month=01 into columns you can filter on, and DuckDB then skips other folders.
-- SELECT * FROM read_parquet('path/to/**/*.parquet', hive_partitioning = true);
