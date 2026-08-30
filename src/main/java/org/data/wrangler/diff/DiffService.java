package org.data.wrangler.diff;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.data.wrangler.dataview.NestedTypeRewriter;
import org.data.wrangler.excel.XlsxSheets;
import org.data.wrangler.files.QueryTemplates;

/**
 * Compares two data sources with DuckDB: schema (column names and types) and
 * rows (EXCEPT ALL in both directions over the common columns). Columns present
 * in both but with different types are compared as text. NetBeans-free.
 */
public final class DiffService {

    public record ColumnDiff(String column, String typeA, String typeB) {
        public String status() {
            if (typeA == null) return "only in B";
            if (typeB == null) return "only in A";
            return typeA.equalsIgnoreCase(typeB) ? "same" : "type differs";
        }
    }

    public record Rows(List<String> columns, List<Object[]> rows, long total) {}

    public record Result(String labelA, String labelB, long countA, long countB,
                         List<ColumnDiff> schema, List<String> commonColumns, Rows onlyInA, Rows onlyInB,
                         String textA, String textB) {
        public boolean identical() {
            return countA == countB && onlyInA.total() == 0 && onlyInB.total() == 0
                    && schema.stream().allMatch(c -> "same".equals(c.status()));
        }
    }

    private static final int MAX_ROWS = 2_000;
    private final Connection conn;

    public DiffService(Connection conn) {
        this.conn = conn;
    }

    /** Compare two files of the same kind (Excel: first sheet unless {@link #compareSheet} is used). */
    public Result compareFiles(File a, File b) throws SQLException {
        return compare(QueryTemplates.readerFor(a.getAbsolutePath()), a.getName(),
                       QueryTemplates.readerFor(b.getAbsolutePath()), b.getName());
    }

    /** Compare one named sheet of two workbooks. */
    public Result compareSheet(File a, File b, String sheet) throws SQLException {
        return compare(xlsx(a, sheet), a.getName() + " [" + sheet + "]", xlsx(b, sheet), b.getName() + " [" + sheet + "]");
    }

    private static String xlsx(File f, String sheet) throws SQLException {
        String p = f.getAbsolutePath().replace("'", "''");
        String range = "";
        try {
            XlsxSheets.UsedRange r = XlsxSheets.usedRange(f, sheet);
            if (r != null) range = ", range = '" + r.a1() + "', stop_at_empty = false";
        } catch (java.io.IOException ignore) { }
        return "read_xlsx('" + p + "', sheet = '" + sheet.replace("'", "''") + "'" + range + ")";
    }

    /** Compare two FROM-able sources. */
    public Result compare(String srcA, String labelA, String srcB, String labelB) throws SQLException {
        synchronized (conn) {
            Map<String, String> typesA = describe(srcA), typesB = describe(srcB);
            List<ColumnDiff> schema = new ArrayList<>();
            List<String> common = new ArrayList<>();
            List<String> selectA = new ArrayList<>(), selectB = new ArrayList<>();
            for (String c : typesA.keySet()) {
                String tb = typesB.get(c);
                schema.add(new ColumnDiff(c, typesA.get(c), tb));
                if (tb != null) {
                    common.add(c);
                    String ta = typesA.get(c);
                    if (tb.equalsIgnoreCase(ta)) {
                        selectA.add(q(c));
                        selectB.add(q(c));
                    } else {
                        // Different types: compare numbers as numbers (DECIMAL vs DOUBLE vs INTEGER
                        // would otherwise differ as text: 10.00 vs 10.0), everything else as text.
                        String cast = isNumeric(ta) && isNumeric(tb) ? "DOUBLE" : "VARCHAR";
                        selectA.add("CAST(" + q(c) + " AS " + cast + ") AS " + q(c));
                        selectB.add("CAST(" + q(c) + " AS " + cast + ") AS " + q(c));
                    }
                }
            }
            for (String c : typesB.keySet()) if (!typesA.containsKey(c)) schema.add(new ColumnDiff(c, null, typesB.get(c)));

            long countA = count(srcA), countB = count(srcB);
            Rows onlyA, onlyB;
            if (common.isEmpty()) {
                onlyA = new Rows(List.of(), List.of(), countA);
                onlyB = new Rows(List.of(), List.of(), countB);
            } else {
                String a = "SELECT " + String.join(", ", selectA) + " FROM " + srcA;
                String b = "SELECT " + String.join(", ", selectB) + " FROM " + srcB;
                onlyA = rows("(" + a + ") EXCEPT ALL (" + b + ")");
                onlyB = rows("(" + b + ") EXCEPT ALL (" + a + ")");
            }
            String textA = canonicalText(srcA, selectA, common), textB = canonicalText(srcB, selectB, common);
            return new Result(labelA, labelB, countA, countB, schema, common, onlyA, onlyB, textA, textB);
        }
    }

    /** Rows over the common columns, one per line, tab-separated, sorted by all columns so equal rows align in a text diff. */
    static final int TEXT_ROWS = 5_000;

    private String canonicalText(String src, List<String> select, List<String> common) throws SQLException {
        StringBuilder sb = new StringBuilder(String.join("\t", common)).append('\n');
        if (common.isEmpty()) return sb.toString();
        String q = "SELECT " + String.join(", ", select) + " FROM " + src + " ORDER BY ALL LIMIT " + TEXT_ROWS;
        try {
            q = NestedTypeRewriter.rewrite(q, NestedTypeRewriter.describe(conn, q));
        } catch (SQLException ignore) { }
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(q)) {
            int n = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                for (int i = 1; i <= n; i++) {
                    Object v = rs.getObject(i);
                    sb.append(v == null ? "" : v.toString().replace('\t', ' ').replace('\n', ' '));
                    if (i < n) sb.append('\t');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private Map<String, String> describe(String src) throws SQLException {
        Map<String, String> m = new LinkedHashMap<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("DESCRIBE SELECT * FROM " + src)) {
            while (rs.next()) m.put(rs.getString("column_name"), rs.getString("column_type"));
        }
        return m;
    }

    private long count(String src) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM " + src)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private Rows rows(String setQuery) throws SQLException {
        long total;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT count(*) FROM (" + setQuery + ")")) {
            total = rs.next() ? rs.getLong(1) : 0;
        }
        String q = "SELECT * FROM (" + setQuery + ") LIMIT " + MAX_ROWS;
        try {
            q = NestedTypeRewriter.rewrite(q, NestedTypeRewriter.describe(conn, q));
        } catch (SQLException ignore) { }
        List<String> cols = new ArrayList<>();
        List<Object[]> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(q)) {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) cols.add(md.getColumnLabel(i));
            while (rs.next()) {
                Object[] row = new Object[cols.size()];
                for (int i = 0; i < row.length; i++) row[i] = rs.getObject(i + 1);
                out.add(row);
            }
        }
        return new Rows(cols, out, total);
    }

    static boolean isNumeric(String type) {
        String t = type.toUpperCase(java.util.Locale.ROOT);
        return t.startsWith("DECIMAL") || t.startsWith("NUMERIC") || t.equals("DOUBLE") || t.equals("FLOAT") || t.equals("REAL")
                || t.endsWith("INT") || t.equals("HUGEINT") || t.equals("UHUGEINT");
    }

    private static String q(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }
}
