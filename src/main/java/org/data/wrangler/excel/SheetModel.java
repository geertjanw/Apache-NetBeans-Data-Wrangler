package org.data.wrangler.excel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.table.AbstractTableModel;

/**
 * Excel-like model over a raw cell grid: optional header row (row 1 stays on top),
 * per-column sort (rows physically reordered, numbering restarts at 1, as Excel does)
 * and per-column value filters (rows hidden, original numbers kept, as Excel does).
 * NetBeans-free and unit-tested.
 */
public final class SheetModel extends AbstractTableModel {

 public enum Sort { NONE, ASC, DESC }

 private final List<String[]> raw;      // all rows, normalised to 'columns' width, edited in place
 private int columns;
 private boolean modified;
 private final List<Runnable> modifiedListeners = new ArrayList<>();
 private boolean headerRow;
 private List<Integer> view = new ArrayList<>();   // indexes into raw of visible data rows, in display order
 private int sortColumn = -1;
 private Sort sort = Sort.NONE;
 private final Map<Integer, Set<String>> filters = new TreeMap<>(); // column -> allowed values

 public SheetModel(XlsxInspector.Sheet sheet) {
 this.columns = Math.max(1, sheet.columns());
 this.raw = new ArrayList<>();
 for (String[] r : sheet.rows()) raw.add(Arrays.copyOf(r, columns));
 if (raw.isEmpty()) raw.add(new String[columns]);
 this.headerRow = looksLikeHeader(sheet);
 rebuild();
    }

    /** Row 1 is a header if every cell is non-empty text and at least one later row has a number in some column. */
 static boolean looksLikeHeader(XlsxInspector.Sheet s) {
 if (s.rows().size() < 2) return false;
        String[] first = s.rows().get(0);
 for (int c = 0; c < s.columns(); c++) {
            String v = c < first.length ? first[c] : null;
 if (v == null || v.isBlank() || isNumeric(v)) return false;
        }
 return true;
    }

    // ---- configuration ----
 public boolean isHeaderRow() { return headerRow; }
 public void setHeaderRow(boolean b) { headerRow = b; sortColumn = -1; sort = Sort.NONE; filters.clear(); rebuild(); }
 public int getSortColumn() { return sortColumn; }
 public Sort getSort() { return sort; }
 public boolean isFiltered(int column) { return filters.containsKey(column); }
 public boolean isAnyFilter() { return !filters.isEmpty(); }

 public void sort(int column, Sort s) {
 sortColumn = s == Sort.NONE ? -1 : column;
 sort = s;
 rebuild();
    }

 public void filter(int column, Set<String> allowed) {
 if (allowed == null) filters.remove(column); else filters.put(column, new LinkedHashSet<>(allowed));
 rebuild();
    }

 public void clearFilters() { filters.clear(); rebuild(); }

    /** Distinct values of a column over the data rows, in display sort order, empty shown as "(Blanks)". */
 public List<String> distinctValues(int column) {
        Set<String> s = new LinkedHashSet<>();
 for (int i = firstDataRow(); i < raw.size(); i++) s.add(displayValue(raw.get(i), column));
        List<String> out = new ArrayList<>(s);
 out.sort(CELL_ORDER);
 return out;
    }

    /** Header text for a column: row-1 value in header mode, else the letter. */
 public String headerText(int column) {
 if (headerRow && !raw.isEmpty()) {
            String v = displayValue(raw.get(0), column);
 return v.isEmpty() ? XlsxSheets.columnLabel(column) : v;
        }
 return XlsxSheets.columnLabel(column);
    }

    // ---- TableModel ----
    @Override public int getRowCount() { return (headerRow ? 1 : 0) + view.size(); }
    @Override public int getColumnCount() { return columns; }
    @Override public String getColumnName(int c) { return XlsxSheets.columnLabel(c); }
    @Override public boolean isCellEditable(int r, int c) { return true; }

    @Override
 public void setValueAt(Object value, int r, int c) {
        String[] row = rawRow(r);
 if (row == null || c < 0 || c >= columns) return;
        String v = value == null ? null : value.toString();
 if (v != null && v.isEmpty()) v = null;
 if (java.util.Objects.equals(row[c], v)) return;
 row[c] = v;
 fireTableCellUpdated(r, c);
 setModified(true);
    }

    // ---- structure edits (Excel: Insert / Delete on the row or column header) ----
 public void insertRow(int displayRow, boolean above) {
 int rawIdx = rawIndex(displayRow);
 if (rawIdx < 0) rawIdx = raw.size() - 1;
 raw.add(above ? rawIdx : rawIdx + 1, new String[columns]);
 setModified(true);
 rebuild();
    }

 public void deleteRow(int displayRow) {
 if (isHeaderRowIndex(displayRow)) return;
 int rawIdx = rawIndex(displayRow);
 if (rawIdx < 0 || raw.size() <= 1) return;
 raw.remove(rawIdx);
 setModified(true);
 rebuild();
    }

 public void insertColumn(int column, boolean left) {
 int at = left ? column : column + 1;
 for (int i = 0; i < raw.size(); i++) {
            String[] old = raw.get(i), n = new String[columns + 1];
            System.arraycopy(old, 0, n, 0, at);
            System.arraycopy(old, at, n, at + 1, columns - at);
 raw.set(i, n);
        }
 columns++;
 filters.clear(); sortColumn = -1; sort = Sort.NONE;
 setModified(true);
 rebuild();
 fireTableStructureChanged();
    }

 public void deleteColumn(int column) {
 if (columns <= 1) return;
 for (int i = 0; i < raw.size(); i++) {
            String[] old = raw.get(i), n = new String[columns - 1];
            System.arraycopy(old, 0, n, 0, column);
            System.arraycopy(old, column + 1, n, column, columns - column - 1);
 raw.set(i, n);
        }
 columns--;
 filters.clear(); sortColumn = -1; sort = Sort.NONE;
 setModified(true);
 rebuild();
 fireTableStructureChanged();
    }

    /** All rows in current (sorted) order, header first if present, for saving. */
 public List<String[]> rowsForSave() {
        List<String[]> out = new ArrayList<>();
 if (headerRow) out.add(raw.get(0));
 if (sort == Sort.NONE && !isAnyFilter()) {
 for (int i = firstDataRow(); i < raw.size(); i++) out.add(raw.get(i));
        } else {
            // keep hidden (filtered) rows too: a filter is a view, not a deletion
            List<Integer> order = new ArrayList<>(view);
 for (int i = firstDataRow(); i < raw.size(); i++) if (!view.contains(i)) order.add(i);
 for (int i : order) out.add(raw.get(i));
        }
 return out;
    }

 public boolean isModified() { return modified; }
 public void setModified(boolean m) {
 if (modified == m) return;
 modified = m;
 modifiedListeners.forEach(Runnable::run);
    }
 public void addModifiedListener(Runnable r) { modifiedListeners.add(r); }

 private int rawIndex(int displayRow) {
 if (isHeaderRowIndex(displayRow)) return 0;
 int dataIdx = headerRow ? displayRow - 1 : displayRow;
 return dataIdx >= 0 && dataIdx < view.size() ? view.get(dataIdx) : -1;
    }
    @Override public Object getValueAt(int r, int c) {
        String[] row = rawRow(r);
 return row == null ? null : (c < row.length ? row[c] : null);
    }

    /** True if display row r is the (fixed) header row. */
 public boolean isHeaderRowIndex(int r) { return headerRow && r == 0; }

    /** The row number Excel would show: sequential unless filtering hides rows, then the original. */
 public int displayRowNumber(int r) {
 if (isHeaderRowIndex(r)) return 1;
 int dataIdx = headerRow ? r - 1 : r;
 if (dataIdx < 0 || dataIdx >= view.size()) return r + 1;
 return isAnyFilter() ? view.get(dataIdx) + 1 : r + 1;
    }

 private String[] rawRow(int r) {
 if (isHeaderRowIndex(r)) return raw.get(0);
 int dataIdx = headerRow ? r - 1 : r;
 return dataIdx >= 0 && dataIdx < view.size() ? raw.get(view.get(dataIdx)) : null;
    }

 private int firstDataRow() { return headerRow ? 1 : 0; }

 private void rebuild() {
        List<Integer> idx = new ArrayList<>();
 for (int i = firstDataRow(); i < raw.size(); i++) {
            String[] row = raw.get(i);
 boolean ok = true;
 for (Map.Entry<Integer, Set<String>> f : filters.entrySet()) {
 if (!f.getValue().contains(displayValue(row, f.getKey()))) { ok = false; break; }
            }
 if (ok) idx.add(i);
        }
 if (sort != Sort.NONE && sortColumn >= 0) {
            Comparator<Integer> cmp = Comparator.comparing(i -> displayValue(raw.get(i), sortColumn), CELL_ORDER);
 if (sort == Sort.DESC) cmp = cmp.reversed();
 idx.sort(cmp);
        }
 view = idx;
 fireTableDataChanged();
    }

 static String displayValue(String[] row, int c) {
        String v = c < row.length ? row[c] : null;
 return v == null ? "" : v;
    }

 static boolean isNumeric(String s) {
 if (s == null || s.isEmpty()) return false;
 char ch = s.charAt(0);
 if (!(Character.isDigit(ch) || ch == '-' || ch == '+' || ch == '.')) return false;
 try { Double.parseDouble(s.replace(",", "")); return true; } catch (NumberFormatException e) { return false; }
    }

    /** Excel order: numbers (numerically) before text (case-insensitive), blanks last. */
 static final Comparator<String> CELL_ORDER = (a, b) -> {
 boolean ea = a == null || a.isEmpty(), eb = b == null || b.isEmpty();
 if (ea || eb) return Boolean.compare(ea, eb);
 boolean na = isNumeric(a), nb = isNumeric(b);
 if (na && nb) return Double.compare(Double.parseDouble(a.replace(",", "")), Double.parseDouble(b.replace(",", "")));
 if (na != nb) return na ? -1 : 1;
 return a.toLowerCase(Locale.ROOT).compareTo(b.toLowerCase(Locale.ROOT));
    };
}
