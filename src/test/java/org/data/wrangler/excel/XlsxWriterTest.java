package org.data.wrangler.excel;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XlsxWriterTest {

    @Test
    void writesAllSheetsAndReadsBackSheetNames() throws Exception {
        File out = Files.createTempFile("w-", ".xlsx").toFile();
        XlsxWriter.write(out, List.of(
                new XlsxWriter.SheetData("orders", List.of(new String[] { "id", "name", "amount" }, new String[] { "1", "A & B", "49.99" }, new String[] { "2", null, "" })),
                new XlsxWriter.SheetData("products", List.of(new String[] { "product" }, new String[] { "<Laptop>" }))));
        assertEquals(List.of("orders", "products"), XlsxSheets.sheetNames(out));
        String sheet1;
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(out)) {
            sheet1 = new String(z.getInputStream(z.getEntry("xl/worksheets/sheet1.xml")).readAllBytes());
        }
        assertTrue(sheet1.contains("<c r=\"C2\"><v>49.99</v></c>"), "numbers as numbers");
        assertTrue(sheet1.contains("A &amp; B"), "strings escaped");
        assertFalse(sheet1.contains("r=\"B3\""), "empty cells omitted");
    }

    @Test
    void editsAreTrackedAndSavedInOrder() {
        SheetModel m = new SheetModel(new XlsxInspector.Sheet("s", 2, List.of(
                new String[] { "k", "v" }, new String[] { "b", "2" }, new String[] { "a", "1" })));
        assertFalse(m.isModified());
        m.setValueAt("3", 1, 1);
        assertTrue(m.isModified());
        assertEquals("3", m.getValueAt(1, 1));
        m.sort(0, SheetModel.Sort.ASC);
        List<String[]> rows = m.rowsForSave();
        assertEquals("k", rows.get(0)[0]);
        assertEquals("a", rows.get(1)[0]);
        assertEquals("b", rows.get(2)[0]);
        assertEquals("3", rows.get(2)[1]);
        m.insertRow(1, true);
        assertEquals(4, m.getRowCount());
        m.deleteRow(1);
        assertEquals(3, m.getRowCount());
        m.insertColumn(1, false);
        assertEquals(3, m.getColumnCount());
        assertEquals("C", m.getColumnName(2));
        m.deleteColumn(2);
        assertEquals(2, m.getColumnCount());
        m.setModified(false);
        assertFalse(m.isModified());
    }
}
