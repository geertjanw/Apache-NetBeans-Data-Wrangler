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

Context menu on all of them: **Query with DuckDB**, **Convert with DuckDB** (CSV, TSV, Parquet, JSON Lines, JSON array, Excel, DuckDB database), **Copy File Path**.

**File › New File › Analytics**: SQL Query, Data Exploration Script, Import Script, Parquet Query Script, Export Script, and sample CSV, Parquet, JSON and Excel files.

### SQL editor

Active when the editor's connection is a DuckDB connection.

- Error checking through DuckDB itself: each statement is sent as `EXPLAIN`, which parses and binds without executing. Syntax errors are underlined in red, unresolved names in yellow, with DuckDB's message in the tooltip.
- Quick fixes: install and load a missing extension; apply DuckDB's "Did you mean" suggestion.
- Completion: functions from `duckdb_functions()` including loaded extensions, tables and views, keywords and types, and columns in scope (tables, views, CTEs, subqueries, `read_csv(...)`), resolved with `DESCRIBE`.
- Documentation on hover for keywords, types and functions.
- Objects created earlier in the same script are not reported as missing.
- 30 code templates (`dqual`, `dpiv`, `dasof`, `dcsv`, `dcopy`, `dmacro`, ...): type the abbreviation, press Tab.

### Connections

- **Register DuckDB Database** on the Databases node: in-memory or file, read-only, threads, memory limit. No credentials.
- **Manage DuckDB Extensions** on a connection.
- **Run in DuckDB Result Viewer** in the editor: nested values as formatted JSON.
- Connections are opened automatically when a feature needs them.

## Build and install

```
mvn install
```

produces `target/datawrangler-1.0.0-SNAPSHOT.nbm`. Install it with Tools › Plugins › Downloaded › Add Plugin. All dependencies are on Maven Central.

To run a NetBeans instance with the module for development:

```
mvn nbm:cluster nbm:run-ide
```

Tests run against an in-memory DuckDB. Tests that need the DuckDB `excel` extension are skipped when it cannot be downloaded.

## How it works

DataWrangler does not parse SQL itself; it asks the engine. Statements are checked with `EXPLAIN`, columns are resolved with `DESCRIBE`, functions come from `duckdb_functions()`, Parquet metadata from `parquet_metadata()`, spreadsheet cells from `read_xlsx()`. Analysis runs on a second connection to the same database so it does not interfere with queries the user runs. The consequence is that the editor reflects the installed DuckDB version and loaded extensions, and needs no update when DuckDB adds syntax; features that need a syntax tree, such as rename refactoring, are not provided.

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
