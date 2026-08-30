package org.data.wrangler.connection;

import java.util.Properties;
import org.data.wrangler.DuckDB;

/** Plain value object so the URL/properties logic is testable without Swing. */
public final class DuckDBConnectionSettings {

 public enum Mode { IN_MEMORY, FILE }

 private Mode mode = Mode.IN_MEMORY;
 private String databasePath = "";
 private boolean readOnly;
 private Integer threads;
 private String memoryLimit;
 private String displayName;

 public Mode getMode() { return mode; }
 public void setMode(Mode mode) { this.mode = mode; }
 public String getDatabasePath() { return databasePath; }
 public void setDatabasePath(String p) { this.databasePath = p == null ? "" : p.trim(); }
 public boolean isReadOnly() { return readOnly; }
 public void setReadOnly(boolean b) { this.readOnly = b; }
 public Integer getThreads() { return threads; }
 public void setThreads(Integer t) { this.threads = t; }
 public String getMemoryLimit() { return memoryLimit; }
 public void setMemoryLimit(String m) { this.memoryLimit = m == null || m.isBlank() ? null : m.trim(); }
 public String getDisplayName() { return displayName; }
 public void setDisplayName(String n) { this.displayName = n; }

 public String buildUrl() {
 if (mode == Mode.IN_MEMORY) return DuckDB.IN_MEMORY_URL;
 return DuckDB.URL_PREFIX + databasePath;
    }

    /** Properties understood by org.duckdb.DuckDBDriver / DuckDB config. */
 public Properties buildProperties() {
        Properties p = new Properties();
 if (readOnly) p.setProperty("duckdb.read_only", "true");
 if (threads != null) p.setProperty("threads", String.valueOf(threads));
 if (memoryLimit != null) p.setProperty("memory_limit", memoryLimit);
 return p;
    }

 public String effectiveDisplayName() {
 if (displayName != null && !displayName.isBlank()) return displayName;
 if (mode == Mode.IN_MEMORY) return "DuckDB (in-memory)";
 int slash = Math.max(databasePath.lastIndexOf('/'), databasePath.lastIndexOf('\\'));
 return "DuckDB - " + databasePath.substring(slash + 1);
    }

 public String validate() {
 if (mode == Mode.FILE && databasePath.isBlank()) return "Choose a database file.";
 if (memoryLimit != null && !memoryLimit.matches("(?i)\\d+(\\.\\d+)?\\s*(B|KB|MB|GB|TB|KiB|MiB|GiB|TiB)"))
 return "Memory limit must look like '4GB' or '512MB'.";
 return null;
    }
}
