package org.data.wrangler.extensions;

/** One row of {@code duckdb_extensions()}. */
public record DuckDBExtension(String name, boolean installed, boolean loaded, String description, String version) {}
