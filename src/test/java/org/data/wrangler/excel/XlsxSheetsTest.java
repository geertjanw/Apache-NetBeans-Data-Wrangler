package org.data.wrangler.excel;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class XlsxSheetsTest {

    static File sample() throws Exception {
        File f = Files.createTempFile("sample-", ".xlsx").toFile();
        try (InputStream in = XlsxSheetsTest.class.getResourceAsStream("/org/data/wrangler/files/templates/sample.xlsx")) {
            assertNotNull(in, "sample.xlsx missing");
            Files.copy(in, f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return f;
    }

    @Test
    void listsSheetsFromWorkbookXml() throws Exception {
        assertEquals(List.of("orders", "products"), XlsxSheets.sheetNames(sample()));
    }

    @Test
    void usedRangeIgnoresStyledBlankCells() throws Exception {
        File f = Files.createTempFile("gaps-", ".xlsx").toFile();
        try (InputStream in = getClass().getResourceAsStream("/org/data/wrangler/excel/gaps.xlsx")) {
            Files.copy(in, f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        // Row 1 has an empty B1 and styled-but-empty F1..Z1; values exist in A..E down to row 23
        assertEquals(List.of("Sheet1"), XlsxSheets.sheetNames(f));
        XlsxSheets.UsedRange r = XlsxSheets.usedRange(f, "Sheet1");
        assertNotNull(r);
        assertEquals("A1:E23", r.a1());
        assertEquals(5, r.columns());
        assertEquals(1, XlsxSheets.usedRange(sample(), "orders").columns() - 6);
    }

    @Test
    void columnLabels() {
        assertEquals(0, XlsxSheets.columnIndex("A"));
        assertEquals(26, XlsxSheets.columnIndex("AA"));
        assertEquals("A", XlsxSheets.columnLabel(0));
        assertEquals("Z", XlsxSheets.columnLabel(25));
        assertEquals("AA", XlsxSheets.columnLabel(26));
        assertEquals("AZ", XlsxSheets.columnLabel(51));
        assertEquals("BA", XlsxSheets.columnLabel(52));
    }

    /** Needs the DuckDB excel extension; skipped when it cannot be installed (offline). */
    @Test
    void readsCellsThroughDuckDB() throws Exception {
        File f = sample();
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:")) {
            XlsxInspector insp = new XlsxInspector(c);
            try {
                insp.ensureExtension();
            } catch (SQLException noNetwork) {
                assumeTrue(false, "excel extension unavailable: " + noNetwork.getMessage());
            }
            XlsxInspector.Sheet orders = insp.read(f, "orders", 100);
            assertEquals(9, orders.rows().size(), "header row + 8 data rows");
            assertEquals("order_id", orders.rows().get(0)[0]);
            assertEquals("Alice", orders.rows().get(1)[1]);
            XlsxInspector.Sheet products = insp.read(f, "products", 100);
            assertEquals(7, products.rows().size());
        }
    }
}
