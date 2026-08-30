package org.data.wrangler.completion;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * DuckDB-specific SQL vocabulary that NetBeans' generic SQL lexer does not know.
 * Standard keywords are left to the built-in SQL completion.
 */
public final class DuckDBKeywords {

 private DuckDBKeywords() {}

    /** Keywords / clauses unique to (or unusual in) DuckDB's dialect. */
 public static final Set<String> KEYWORDS = Set.of(
            // star expressions and projection
            "EXCLUDE", "REPLACE", "COLUMNS",
            // filtering / windows
            "QUALIFY", "FILTER",
            // joins
            "ASOF", "SEMI", "ANTI", "POSITIONAL", "LATERAL",
            // reshaping
            "PIVOT", "UNPIVOT", "PIVOT_WIDER", "PIVOT_LONGER",
            // sampling
            "SAMPLE", "USING SAMPLE", "RESERVOIR", "BERNOULLI", "SYSTEM",
            // catalog / extensions / attach
            "INSTALL", "LOAD", "FORCE INSTALL", "ATTACH", "DETACH", "USE",
            "MACRO", "CREATE MACRO", "CREATE OR REPLACE", "CREATE SECRET", "DROP SECRET",
            // io
            "COPY", "EXPORT DATABASE", "IMPORT DATABASE", "FORMAT", "HEADER", "DELIMITER",
            // inspection
            "DESCRIBE", "SUMMARIZE", "EXPLAIN ANALYZE", "SHOW TABLES", "SHOW ALL TABLES",
            // misc
            "PRAGMA", "SET", "RESET", "CHECKPOINT", "FORCE CHECKPOINT", "RETURNING",
            "STRUCT", "LIST", "MAP", "UNION BY NAME", "GROUP BY ALL", "ORDER BY ALL",
            "TRY_CAST", "LAMBDA", "STRUCT_PACK");

    /**
     * Standard SQL keywords for which we provide DuckDB-flavoured hover docs.
     * NetBeans' lexer already colours these; we only attach a tooltip.
     */
 public static final Set<String> DOCUMENTED_STANDARD = Set.of(
            "SELECT", "FROM", "WHERE", "JOIN", "GROUP", "HAVING", "ORDER", "LIMIT", "WITH",
            "CREATE", "INSERT", "UPDATE", "DELETE", "CAST", "CASE", "DISTINCT", "OVER", "VALUES", "TABLE", "EXPLAIN");

    /** Types that are DuckDB-only or spelled differently from ANSI. */
 public static final Set<String> TYPES = Set.of(
            "HUGEINT", "UHUGEINT", "UTINYINT", "USMALLINT", "UINTEGER", "UBIGINT",
            "BLOB", "UUID", "INTERVAL", "TIMESTAMPTZ", "TIMESTAMP_S", "TIMESTAMP_MS", "TIMESTAMP_NS",
            "JSON", "BIT", "ENUM", "STRUCT", "LIST", "MAP", "UNION", "ARRAY", "GEOMETRY");

    /**
     * Fallback function list used only when there is no live connection to read
     * duckdb_functions() from. The connected path is always preferred.
     */
 public static final List<FunctionInfo> OFFLINE_FUNCTIONS = List.of(
 fn("read_csv", "path", "VARCHAR", "Read CSV file(s) with automatic type detection"),
 fn("read_csv_auto", "path", "VARCHAR", "Alias of read_csv"),
 fn("read_parquet", "path", "VARCHAR", "Read Parquet file(s)"),
 fn("read_json", "path", "VARCHAR", "Read newline-delimited or array JSON"),
 fn("read_json_auto", "path", "VARCHAR", "Read JSON with automatic schema detection"),
 fn("parquet_metadata", "path", "VARCHAR", "Metadata of a Parquet file"),
 fn("parquet_schema", "path", "VARCHAR", "Schema of a Parquet file"),
 fn("glob", "pattern", "VARCHAR", "List files matching a glob"),
 fn("range", "start, stop, step", "BIGINT", "Generate a range of values"),
 fn("generate_series", "start, stop, step", "BIGINT", "Generate an inclusive series"),
 fn("list_value", "any...", "LIST", "Create a LIST"),
 fn("list_transform", "list, lambda", "LIST", "Apply a lambda to each element"),
 fn("list_filter", "list, lambda", "LIST", "Keep elements matching a lambda"),
 fn("unnest", "list", "ANY", "Explode a list or struct"),
 fn("struct_pack", "name := value...", "STRUCT", "Create a STRUCT"),
 fn("map", "keys, values", "MAP", "Create a MAP"),
 fn("regexp_matches", "string, pattern", "BOOLEAN", "Regex test"),
 fn("regexp_extract", "string, pattern, group", "VARCHAR", "Regex capture group"),
 fn("strftime", "timestamp, format", "VARCHAR", "Format a timestamp"),
 fn("strptime", "string, format", "TIMESTAMP", "Parse a timestamp"),
 fn("date_trunc", "part, timestamp", "TIMESTAMP", "Truncate to a date part"),
 fn("epoch_ms", "timestamp", "BIGINT", "Milliseconds since epoch"),
 fn("quantile_cont", "x, pos", "DOUBLE", "Interpolated quantile"),
 fn("approx_count_distinct", "x", "BIGINT", "HyperLogLog distinct count"),
 fn("string_agg", "x, sep", "VARCHAR", "Concatenate with separator"),
 fn("list_aggregate", "list, name", "ANY", "Aggregate over a list"),
 fn("duckdb_functions", "", "TABLE", "Catalog of functions"),
 fn("duckdb_extensions", "", "TABLE", "Catalog of extensions"),
 fn("duckdb_tables", "", "TABLE", "Catalog of tables"),
 fn("duckdb_settings", "", "TABLE", "Current settings"));

 private static FunctionInfo fn(String name, String params, String ret, String desc) {
        List<String> p = params.isBlank() ? List.of() : List.of(params.split(",\\s*"));
 return new FunctionInfo(name, "scalar", p, ret, desc);
    }

 public static Set<String> allKeywordsSorted() {
        TreeSet<String> s = new TreeSet<>(KEYWORDS);
 s.addAll(TYPES);
 return s;
    }
}
