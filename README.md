# Apache NetBeans DataWrangler

Query, convert, inspect, edit and analyze CSV, [Apache Parquet](https://parquet.apache.org/), JSON and Excel files in Apache NetBeans via [DuckDB](https://duckdb.org/). Files open as documents with Visual and Query tabs. The SQL editor has enhanced completion, documentation, error checking and quick fixes for analytical SQL via DuckDB. DuckDB is bundled.

Requires Apache NetBeans 31 or later.

## Features

### Files

| Format | Extensions | What you get |
|---|---|---|
| Apache Parquet | `.parquet`, `.pq` | Visual tab: rows, row groups, per-column type, encoding, compression and size, min/max, null count, preview with nested values as JSON. Query tab. |
| Excel | `.xlsx`, `.xlsm` | Visual tab as a spreadsheet: lettered columns, numbered rows, name box, formula bar, sheet tabs. Sort and filter from header cells, reorder and resize columns, insert and delete rows and columns, edit cells, save. Query tab. |
| CSV, TSV | `.csv`, `.tsv` | Editor, icon, actions. |
| JSON, JSON Lines | `.json`, `.jsonl`, `.ndjson` | NetBeans JSON editor, actions. |

- Context menu on all of them: **Query with DuckDB**, **Convert with DuckDB** (CSV, TSV, Parquet, JSON Lines, JSON array, Excel, DuckDB database), **Copy File Path**.

- **File › New File › Analytics**: SQL Query, Data Exploration Script, Import Script, Parquet Query Script, Export Script, and sample CSV, Parquet, JSON and Excel files.

### SQL editor

Active when the editor's connection is a DuckDB connection.

- **Error checking through DuckDB itself:** each statement is sent as `EXPLAIN`, which parses and binds without executing. Syntax errors are underlined in red, unresolved names in yellow, with DuckDB's message in the tooltip.
- **Quick fixes:** install and load a missing extension, apply DuckDB's "Did you mean" suggestion.
- **Completion:** functions from `duckdb_functions()` including loaded extensions, tables and views, keywords and types, and columns in scope (tables, views, CTEs, subqueries, `read_csv(...)`), resolved with `DESCRIBE`.
- **Documentation on hover** for keywords, types and functions.
- **Objects created earlier** in the same script are not reported as missing.
- **31 code templates** for common DuckDB patterns: type the abbreviation, press Tab. The full list is in [Code templates](#code-templates) below.

### Connections

- **Register DuckDB Database** on the Databases node: in-memory or file, read-only, threads, memory limit. No credentials.
- **Manage DuckDB Extensions** on a connection.
- **Run in DuckDB Result Viewer** in the editor: nested values as formatted JSON.
- Connections are opened automatically when a feature needs them.

## Code templates

Type the abbreviation in an SQL editor and press Tab. Blue fields are parameters, Tab moves between them, Enter finishes. All abbreviations start with `d` so they do not collide with SQL words. Edit or add templates under Tools › Options › Editor › Code Templates › Language: SQL.

| Abbreviation | What it does | Expansion (parameters in angle brackets) |
|---|---|---|
| `dfrom` | FROM-first query; `SELECT *` is implied when the projection is left out. | `FROM <table> SELECT * ;` |
| `dexcl` | All columns except the listed ones. | `SELECT * EXCLUDE (<cols>) FROM <table>;` |
| `drepl` | All columns, with one replaced by an expression under the same name. | `SELECT * REPLACE (<expr> AS <col>) FROM <table>;` |
| `dcols` | Apply an aggregate to every column matching a regular expression. | `SELECT min(COLUMNS('.*')) FROM <table>;` |
| `dqual` | Top-N rows per group using `row_number()` and `QUALIFY`, without a subquery. | `SELECT * FROM <table> QUALIFY row_number() OVER (PARTITION BY <key> ORDER BY <order> DESC) <= 1;` |
| `dgall` | Aggregate with `GROUP BY ALL` and `ORDER BY ALL`, so grouping columns are not repeated. | `SELECT <dims>, count(*) FROM <table> GROUP BY ALL ORDER BY ALL;` |
| `dpiv` | Cross-tab: one column per distinct value of a column. | `PIVOT <table> ON <column> USING sum(<value>) GROUP BY <group>;` |
| `dunpiv` | Turn columns into rows (inverse of PIVOT). | `UNPIVOT <table> ON <columns> INTO NAME name VALUE value;` |
| `dasof` | Match each left row to the nearest earlier right row by time; typical for prices or rates. | `SELECT l.*, r.<rcol> FROM <left> <l> ASOF JOIN <right> <r> ON <l>.<key> = <r>.<key> AND <l>.ts >= <r>.<ts>;` |
| `dcte` | Common table expression followed by a query over it. | `WITH cte AS ( <query> ) SELECT * FROM <cte>;` |
| `dcsv` | Read a CSV file with header and type detection. | `SELECT * FROM read_csv('<path>', header = true, auto_detect = true);` |
| `dpq` | Read one Parquet file or a glob of files. | `SELECT * FROM read_parquet('<path>');` |
| `djson` | Read a JSON array or newline-delimited JSON with schema detection. | `SELECT * FROM read_json_auto('<path>');` |
| `dcopy` | Write a query result to a Parquet file. | `COPY (<query>) TO '<path>' (FORMAT parquet);` |
| `dctas` | Load a file into a table in one statement. | `CREATE OR REPLACE TABLE <table> AS SELECT * FROM '<path>';` |
| `dins` | Insert by column name with an upsert on conflict. | `INSERT INTO <table> BY NAME SELECT <cols> FROM <source> ON CONFLICT DO UPDATE SET <col> = excluded.<col>;` |
| `dmacro` | Define a scalar macro (a reusable expression with parameters). | `CREATE OR REPLACE MACRO <name>(<params>) AS <expr>;` |
| `dtmacro` | Define a table macro (a reusable parameterised query). | `CREATE OR REPLACE MACRO <name>(<params>) AS TABLE <query>;` |
| `dlist` | Filter and transform a list with lambdas. | `SELECT list_transform(list_filter(<list>, x -> x IS NOT NULL), x -> x);` |
| `dstruct` | Build a struct literal and read a field from it. | `SELECT {'a': 1, 'b': 'x'} AS s, s.<a>;` |
| `dunnest` | Explode a list column into one row per element. | `SELECT <id>, unnest(<list>) AS elem FROM <table>;` |
| `dvals` | Inline rows with `VALUES` and a column-list alias; useful for examples without a file. | `SELECT * FROM (VALUES (1, 'a'), (2, 'b') ) AS t(id, name);` |
| `dsample` | Random sample of a table with a fixed seed. | `SELECT * FROM <table> USING SAMPLE 10% (bernoulli, 42);` |
| `dsum` | Per-column statistics: min, max, distinct count, quartiles, null percentage. | `SUMMARIZE <table>;` |
| `ddesc` | Column names and types of a query result. | `DESCRIBE SELECT * FROM <source>;` |
| `dexpl` | Run a query and show the plan with actual timings. | `EXPLAIN ANALYZE <query>;` |
| `dext` | Install and load a DuckDB extension. | `INSTALL <ext>; LOAD <ext>;` |
| `dattach` | Attach another database file and query a table in it. | `ATTACH '<path>' AS <name> (READ_ONLY); SELECT * FROM <name>.main.<table>;` |
| `dsecret` | Store S3 credentials for `httpfs` reads. | `CREATE OR REPLACE SECRET s3 (TYPE s3, KEY_ID '<key>', SECRET '<secret>', REGION 'eu-west-1');` |
| `ddate` | Aggregate by calendar bucket with `date_trunc`. | `SELECT date_trunc('month', <ts>) AS bucket, count(*) FROM <table> GROUP BY ALL ORDER BY ALL;` |
| `dwin` | Running total with a window frame. | `SELECT <cols>, sum(<value>) OVER (PARTITION BY <key> ORDER BY <order> ROWS UNBOUNDED PRECEDING) AS running_total FROM <table>;` |

## Build and install

```
mvn install
```

produces `target/datawrangler-1.0.0-SNAPSHOT.nbm`. Install it with Tools › Plugins › Downloaded › Add Plugin. All dependencies are on Maven Central.

To run a NetBeans instance with the module for development:

```
mvn nbm:cluster nbm:run-ide
```

Tests run against an in-memory DuckDB. 

## How it works

DataWrangler has no SQL parser and no model of the database schema. Everything the editor reports comes from running statements against the connected DuckDB database. 

- To check a statement, DataWrangler runs it as EXPLAIN, which makes DuckDB parse the statement and resolve every name in it without executing it; DuckDB's error message and position become the underline and the tooltip. 
- To list the columns available after s., it runs DESCRIBE on whatever s refers to, whether a table, a view, a common table expression, a subquery or a read_csv(...) call. The function list is read from duckdb_functions(), Parquet file details from parquet_metadata(), and spreadsheet cells from read_xlsx().

These statements run on a separate connection to the same database, so editor checks do not interfere with queries the user is running.

**Note:**
- What the editor shows is correct for the DuckDB version that is installed and the extensions that are loaded, and it remains correct when a new DuckDB release adds syntax, because there is nothing in DataWrangler to update.
- On the other hand, features that need a syntax tree of the file, such as renaming an alias throughout a script, are not supported, and most features require a connection to an open database, which is automatically created when needed.

## Layout

```
src/main/java/org/data/wrangler/
  analysis/     statement splitting, EXPLAIN diagnostics, scope and column resolution, highlighting, quick fixes
  completion/   completion provider, function catalog, keyword and syntax documentation
  connection/   Register DuckDB Database dialog
  convert/      Convert with DuckDB
  dataview/     result viewer with JSON rendering of nested values
  driver/       driver registration and the JDBC wrapper driver
  excel/        Excel reading, writing, spreadsheet view
  extensions/   Manage DuckDB Extensions
  files/        file types, templates, Query with DuckDB, shared data-file window
  parquet/      Parquet inspector and views
src/main/resources/org/data/wrangler/
  syntax-docs.properties, duckdb-codetemplates.xml, icons, templates
upstream/       proposed changes to NetBeans (SqlDialect SPI, SQL execution fix)
```

## License

Apache License 2.0.
