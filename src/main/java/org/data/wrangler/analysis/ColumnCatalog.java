package org.data.wrangler.analysis;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Asks DuckDB for column names/types of any FROM-able source, and for table names. */
public final class ColumnCatalog {

 public record Column(String name, String type, String source) {}

 private ColumnCatalog() {}

    /** Columns of one source (table, view, CTE, read_csv(...), subquery). */
 public static List<Column> columnsOf(Connection conn, ScopeResolver.Scope scope, String alias, String source) {
        String q = scope.withPrefix() + "SELECT * FROM " + source;
        List<Column> out = new ArrayList<>();
 synchronized (conn) {
 try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("DESCRIBE " + q)) {
 while (rs.next()) out.add(new Column(rs.getString("column_name"), rs.getString("column_type"), alias));
            } catch (SQLException ignore) {
                // unresolved source, nothing to complete
            }
        }
 return out;
    }

    /** Columns of every source in scope, keyed by alias. */
 public static Map<String, List<Column>> allColumns(Connection conn, ScopeResolver.Scope scope) {
        Map<String, List<Column>> out = new LinkedHashMap<>();
 scope.sources().forEach((alias, src) -> out.put(alias, columnsOf(conn, scope, alias, src)));
 return out;
    }

 public record Relation(String schema, String name, String kind) {}

 public static List<Relation> relations(Connection conn) {
        List<Relation> out = new ArrayList<>();
        String sql = "SELECT schema_name, table_name, 'table' FROM duckdb_tables() WHERE NOT internal "
                   + "UNION ALL SELECT schema_name, view_name, 'view' FROM duckdb_views() WHERE NOT internal "
                   + "ORDER BY 1, 2";
 synchronized (conn) {
 try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
 while (rs.next()) out.add(new Relation(rs.getString(1), rs.getString(2), rs.getString(3)));
            } catch (SQLException ignore) { }
        }
 return out;
    }
}
