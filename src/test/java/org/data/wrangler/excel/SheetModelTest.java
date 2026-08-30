package org.data.wrangler.excel;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SheetModelTest {

    private static XlsxInspector.Sheet sheet() {
        return new XlsxInspector.Sheet("orders", 3, List.of(
                new String[] { "city", "product", "amount" },
                new String[] { "Utrecht", "Keyboard", "49.99" },
                new String[] { "Delft", "Monitor", "189" },
                new String[] { "Amsterdam", "Laptop", "999" },
                new String[] { "Delft", "Mouse", "19.95" }));
    }

    @Test
    void detectsHeaderAndKeepsItOnTop() {
        SheetModel m = new SheetModel(sheet());
        assertTrue(m.isHeaderRow());
        assertEquals(5, m.getRowCount());
        assertTrue(m.isHeaderRowIndex(0));
        assertEquals("city", m.headerText(0));
        assertEquals("A", m.getColumnName(0));
    }

    @Test
    void sortIsNumericAwareAndRenumbersSequentially() {
        SheetModel m = new SheetModel(sheet());
        m.sort(2, SheetModel.Sort.DESC);
        assertEquals("999", m.getValueAt(1, 2));
        assertEquals("19.95", m.getValueAt(4, 2));
        assertEquals(1, m.displayRowNumber(0));
        assertEquals(2, m.displayRowNumber(1));   // sequential, as Excel after a sort
        assertEquals("city", m.getValueAt(0, 0)); // header untouched
        m.sort(0, SheetModel.Sort.ASC);
        assertEquals("Amsterdam", m.getValueAt(1, 0));
    }

    @Test
    void filterHidesRowsAndKeepsOriginalNumbers() {
        SheetModel m = new SheetModel(sheet());
        m.filter(0, Set.of("Delft"));
        assertEquals(3, m.getRowCount());
        assertTrue(m.isFiltered(0));
        assertEquals(3, m.displayRowNumber(1));   // original row numbers, as Excel when filtered
        assertEquals(5, m.displayRowNumber(2));
        assertEquals(List.of("Amsterdam", "Delft", "Utrecht"), m.distinctValues(0));
        m.filter(0, null);
        assertEquals(5, m.getRowCount());
        assertFalse(m.isAnyFilter());
    }

    @Test
    void withoutHeaderRowEverythingIsData() {
        SheetModel m = new SheetModel(sheet());
        m.setHeaderRow(false);
        assertEquals(5, m.getRowCount());
        assertFalse(m.isHeaderRowIndex(0));
        assertEquals("A", m.headerText(0));
        m.sort(2, SheetModel.Sort.ASC);
        assertEquals("19.95", m.getValueAt(0, 2));  // numbers first, then the text "amount" last
        assertEquals("amount", m.getValueAt(4, 2));
    }

    @Test
    void cellOrderPutsBlanksLastAndNumbersBeforeText() {
        assertTrue(SheetModel.CELL_ORDER.compare("10", "9") > 0);
        assertTrue(SheetModel.CELL_ORDER.compare("10", "abc") < 0);
        assertTrue(SheetModel.CELL_ORDER.compare("abc", "") < 0);
        assertTrue(SheetModel.CELL_ORDER.compare("apple", "Banana") < 0);
    }
}
