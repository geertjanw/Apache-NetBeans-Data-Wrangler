package org.data.wrangler.convert;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the SELECT used to read a source file for conversion, repairing types
 * the source format cannot express. Excel stores every number as a double, so
 * read_xlsx returns DOUBLE for id and count columns; a DOUBLE column whose
 * values are all whole numbers is cast to BIGINT so it converts as an integer.
 */
public final class SourceQuery {

 private SourceQuery() {}

    /** A SELECT over {@code reader} with integral DOUBLE columns cast to BIGINT (Excel sources only). */
 public static String select(Connection conn, String reader, boolean excelSource) throws SQLException {
 if (!excelSource) return "SELECT * FROM " + reader;
        List<String[]> cols = new ArrayList<>();
 synchronized (conn) {
 try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("DESCRIBE SELECT * FROM " + reader)) {
 while (rs.next()) cols.add(new String[] { rs.getString("column_name"), rs.getString("column_type") });
            }
            List<String> select = new ArrayList<>();
 for (String[] c : cols) {
                String q = quote(c[0]);
 if ("DOUBLE".equalsIgnoreCase(c[1]) && allWhole(st(conn), reader, q)) {
 select.add("CAST(" + q + " AS BIGINT) AS " + q);
                } else {
 select.add(q);
                }
            }
 return "SELECT " + String.join(", ", select) + " FROM " + reader;
        }
    }

 private static Statement st(Connection c) throws SQLException { return c.createStatement(); }

 private static boolean allWhole(Statement st, String reader, String col) throws SQLException {
 try (st; ResultSet rs = st.executeQuery(
                "SELECT bool_and(" + col + " IS NULL OR " + col + " = trunc(" + col + ")) AND max(abs(" + col + ")) < 9.0e15 FROM " + reader)) {
 return rs.next() && rs.getBoolean(1);
        }
    }

 static String quote(String ident) {
 return "\"" + ident.replace("\"", "\"\"") + "\"";
    }

 public static boolean isExcel(String path) {
        String l = path.toLowerCase(Locale.ROOT);
 return l.endsWith(".xlsx") || l.endsWith(".xlsm");
    }
}
