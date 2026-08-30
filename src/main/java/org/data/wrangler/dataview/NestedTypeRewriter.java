package org.data.wrangler.dataview;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * NetBeans' generic result grid renders LIST/STRUCT/MAP cells with
 * {@code Object.toString()}. Rather than patching db.dataview (not pluggable),
 * we wrap the user's query so nested columns come back as JSON text:
 * {@code SELECT a, to_json(b) AS b FROM (<query>)}.
 */
public final class NestedTypeRewriter {

 public record Col(String name, String type) {
 public boolean nested() {
            String t = type.toUpperCase(Locale.ROOT);
 return t.contains("[") || t.startsWith("STRUCT") || t.startsWith("MAP") || t.startsWith("UNION") || t.startsWith("LIST");
        }
    }

 private NestedTypeRewriter() {}

 public static List<Col> describe(Connection conn, String query) throws SQLException {
        List<Col> cols = new ArrayList<>();
 synchronized (conn) {
 try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("DESCRIBE " + query)) {
 while (rs.next()) cols.add(new Col(rs.getString("column_name"), rs.getString("column_type")));
            }
        }
 return cols;
    }

    /** Pure function; unit-tested. */
 public static String rewrite(String query, List<Col> cols) {
 if (cols.stream().noneMatch(Col::nested)) return query;
        StringBuilder sb = new StringBuilder("SELECT ");
 for (int i = 0; i < cols.size(); i++) {
            Col c = cols.get(i);
            String q = "\"" + c.name().replace("\"", "\"\"") + "\"";
 if (i > 0) sb.append(", ");
 sb.append(c.nested() ? "to_json(" + q + ") AS " + q : q);
        }
 return sb.append(" FROM (").append(stripTrailingSemicolon(query)).append(") AS duckdb_nb_q").toString();
    }

 private static String stripTrailingSemicolon(String q) {
        String t = q.strip();
 return t.endsWith(";") ? t.substring(0, t.length() - 1) : t;
    }
}
