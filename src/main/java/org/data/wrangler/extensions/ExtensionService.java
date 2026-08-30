package org.data.wrangler.extensions;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Thin, JDBC-only wrapper around DuckDB's extension catalog. Kept free of
 * NetBeans APIs so it can be unit-tested against an in-memory DuckDB.
 */
public final class ExtensionService {

 private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_]+");
 private final Connection conn;

 public ExtensionService(Connection conn) {
 this.conn = conn;
    }

 public List<DuckDBExtension> list() throws SQLException {
        List<DuckDBExtension> out = new ArrayList<>();
        String sql = "SELECT extension_name, installed, loaded, description, extension_version "
                   + "FROM duckdb_extensions() ORDER BY extension_name";
 try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
 while (rs.next()) {
 out.add(new DuckDBExtension(
 rs.getString(1), rs.getBoolean(2), rs.getBoolean(3),
 rs.getString(4), rs.getString(5)));
            }
        }
 return out;
    }

 public void install(String name) throws SQLException {
 exec("INSTALL " + checked(name));
    }

 public void load(String name) throws SQLException {
 exec("LOAD " + checked(name));
    }

 private void exec(String sql) throws SQLException {
 try (Statement st = conn.createStatement()) {
 st.execute(sql);
        }
    }

 public static String checked(String name) {
 if (name == null || !SAFE_NAME.matcher(name).matches()) {
 throw new IllegalArgumentException("Not a valid extension name: " + name);
        }
 return name;
    }
}
