package org.data.wrangler;

import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.api.db.explorer.JDBCDriver;

/** Shared constants and small helpers for the DuckDB integration. */
public final class DuckDB {

 public static final String CODE_NAME_BASE = "org.data.wrangler";
    /** The wrapper driver registered with NetBeans (see NbDuckDBDriver). */
 public static final String DRIVER_CLASS   = "org.data.wrangler.driver.NbDuckDBDriver";
 public static final String RAW_DRIVER_CLASS = "org.duckdb.DuckDBDriver";
 public static final String DRIVER_NAME    = "duckdb";
 public static final String URL_PREFIX     = "jdbc:duckdb:";
    /**
     * DuckDB has no users, but NetBeans' ConnectionManager.connect() silently
     * refuses to connect when the user name is empty, so every DuckDB
     * connection carries this nominal user. The driver ignores it.
     */
 public static final String NOMINAL_USER = "duckdb";
    /** A bare prefix opens an in-memory database. */
 public static final String IN_MEMORY_URL  = URL_PREFIX;

 private DuckDB() {}

 public static boolean isDuckDB(DatabaseConnection conn) {
 if (conn == null) return false;
        JDBCDriver d = conn.getJDBCDriver();
 if (d != null && (DRIVER_CLASS.equals(d.getClassName()) || RAW_DRIVER_CLASS.equals(d.getClassName()))) return true;
        String url = conn.getDatabaseURL();
 return url != null && url.startsWith(URL_PREFIX);
    }

 public static boolean isInMemory(DatabaseConnection conn) {
        String url = conn.getDatabaseURL();
 return url != null && (url.equals(URL_PREFIX) || url.equals(URL_PREFIX + ":memory:"));
    }
}
