package org.data.wrangler.excel;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.data.wrangler.extensions.ExtensionService;

/** Reads a sheet as a raw cell grid through DuckDB's excel extension. */
public final class XlsxInspector {

 public record Sheet(String name, int columns, List<String[]> rows) {}

 private final Connection conn;

 public XlsxInspector(Connection conn) {
 this.conn = conn;
    }

    /** LOAD excel, installing it first if needed (needs network once). */
 public void ensureExtension() throws SQLException {
        ExtensionService ext = new ExtensionService(conn);
 synchronized (conn) {
 try {
 ext.load("excel");
            } catch (SQLException notInstalled) {
 ext.install("excel");
 ext.load("excel");
            }
        }
    }

    /**
     * Cells as in the workbook: header = false keeps row 1 as data,
     * all_varchar = true avoids type coercion so the grid shows what Excel shows.
     */
 public Sheet read(File xlsx, String sheet, int maxRows) throws SQLException {
        String p = xlsx.getAbsolutePath().replace("'", "''");
        String s = sheet.replace("'", "''");
        // Explicit range: read_xlsx's own width detection stops at the first empty cell in row 1
        // (a blank header cell hides every column to its right) and its row detection stops at
        // the first empty row. The used range from the sheet XML has neither problem.
        String range = "";
 try {
            XlsxSheets.UsedRange used = XlsxSheets.usedRange(xlsx, sheet);
 if (used != null) range = ", range = '" + used.a1() + "', stop_at_empty = false";
        } catch (java.io.IOException ignore) {
            // fall back to DuckDB's detection
        }
        String sql = "SELECT * FROM read_xlsx('" + p + "', sheet = '" + s + "', header = false, all_varchar = true, empty_as_varchar = true" + range + ") LIMIT " + maxRows;
        List<String[]> rows = new ArrayList<>();
 int cols;
 synchronized (conn) {
 try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                ResultSetMetaData md = rs.getMetaData();
 cols = md.getColumnCount();
 while (rs.next()) {
                    String[] row = new String[cols];
 for (int i = 0; i < cols; i++) row[i] = rs.getString(i + 1);
 rows.add(row);
                }
            }
        }
 return new Sheet(sheet, cols, rows);
    }
}
