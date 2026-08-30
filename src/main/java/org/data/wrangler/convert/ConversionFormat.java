package org.data.wrangler.convert;

/** Output formats for "Convert with DuckDB". */
public enum ConversionFormat {
    CSV("CSV", "csv", "(FORMAT csv, HEADER true)", null),
    TSV("TSV (tab-separated)", "tsv", "(FORMAT csv, HEADER true, DELIMITER '\t')", null),
    PARQUET("Parquet (zstd)", "parquet", "(FORMAT parquet, COMPRESSION zstd)", null),
    PARQUET_SNAPPY("Parquet (snappy)", "parquet", "(FORMAT parquet, COMPRESSION snappy)", null),
    JSON_LINES("JSON Lines (one object per line)", "jsonl", "(FORMAT json)", null),
    JSON_ARRAY("JSON array", "json", "(FORMAT json, ARRAY true)", null),
    XLSX("Excel (.xlsx)", "xlsx", "(FORMAT xlsx, HEADER true)", "excel"),
    DUCKDB("DuckDB database (.duckdb)", "duckdb", null, null);

 public final String label;
 public final String extension;
    /** COPY ... TO options, or null for formats handled specially. */
 public final String copyOptions;
    /** Extension that must be INSTALLed/LOADed first, or null. */
 public final String requiredExtension;

    ConversionFormat(String label, String extension, String copyOptions, String requiredExtension) {
 this.label = label;
 this.extension = extension;
 this.copyOptions = copyOptions;
 this.requiredExtension = requiredExtension;
    }
}
