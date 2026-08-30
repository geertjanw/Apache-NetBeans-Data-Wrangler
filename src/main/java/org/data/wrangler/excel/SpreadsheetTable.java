package org.data.wrangler.excel;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

/**
 * A JTable styled after Excel's default (Office) theme with Excel's header
 * behaviour: the header row carries AutoFilter dropdowns (sort A-Z / Z-A,
 * filter by values), columns can be dragged to reorder and resized; sorting
 * renumbers rows, filtering hides rows and shows the original numbers in blue.
 * Colours are fixed : a workbook is a document and looks the same in a dark IDE.
 */
public final class SpreadsheetTable extends JScrollPane {

 static final Color CELL_BG = Color.WHITE;
 static final Color CELL_FG = Color.BLACK;
 static final Color GRID = new Color(0xD4, 0xD4, 0xD4);
 static final Color HEADER_BG = new Color(0xF2, 0xF2, 0xF2);
 static final Color HEADER_FG = new Color(0x61, 0x61, 0x61);
 static final Color HEADER_ACTIVE_BG = new Color(0xD3, 0xD3, 0xD3);
 static final Color HEADER_BORDER = new Color(0xC6, 0xC6, 0xC6);
 static final Color GREEN = new Color(0x21, 0x73, 0x46);
 static final Color SELECTION_BG = new Color(0xE7, 0xEF, 0xE9);
 static final Color FILTERED_ROW_NUMBER = new Color(0x00, 0x66, 0xCC);
 static final Color DROPDOWN_BG = new Color(0xF5, 0xF5, 0xF5);
 static final int ROW_HEIGHT = 20;
 static final int DROPDOWN_W = 16;
 static final Font CELL_FONT = pickFont();
 static final Font BOLD_FONT = CELL_FONT.deriveFont(Font.BOLD);

 private final SheetModel model;
 private final Grid table;
 private final JList<String> rowHeader;

 public SpreadsheetTable(XlsxInspector.Sheet sheet) {
 this.model = new SheetModel(sheet);
 this.table = new Grid(model);
 setViewportView(table);
 getViewport().setBackground(CELL_BG);
 setBorder(BorderFactory.createEmptyBorder());

 rowHeader = new JList<>(new javax.swing.AbstractListModel<>() {
            @Override public int getSize() { return model.getRowCount(); }
            @Override public String getElementAt(int i) { return String.valueOf(model.displayRowNumber(i)); }
        });
 rowHeader.setFixedCellHeight(ROW_HEIGHT);
 rowHeader.setFixedCellWidth(Math.max(36, 14 + 8 * String.valueOf(sheet.rows().size()).length()));
 rowHeader.setCellRenderer(new RowHeaderRenderer(table, model));
 rowHeader.setFocusable(false);
 rowHeader.setBackground(HEADER_BG);
 setRowHeaderView(rowHeader);
 table.getSelectionModel().addListSelectionListener(e -> rowHeader.repaint());
 table.getColumnModel().getSelectionModel().addListSelectionListener(e -> table.getTableHeader().repaint());
 model.addTableModelListener(e -> { rowHeader.revalidate(); rowHeader.repaint(); });

        JLabel corner = new JLabel();
 corner.setOpaque(true);
 corner.setBackground(HEADER_BG);
 corner.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, HEADER_BORDER));
 setCorner(JScrollPane.UPPER_LEFT_CORNER, corner);

 rowHeader.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) menu(e); else selectRow(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) menu(e); }
 private void selectRow(MouseEvent e) {
 int r = rowHeader.locationToIndex(e.getPoint());
 if (r >= 0) { table.setRowSelectionInterval(r, r); table.setColumnSelectionInterval(0, table.getColumnCount() - 1); }
            }
 private void menu(MouseEvent e) {
 int r = rowHeader.locationToIndex(e.getPoint());
 if (r >= 0) rowHeaderMenu(r, e.getPoint());
            }
        });
 table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) menu(e); else selectColumn(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) menu(e); }
 private void selectColumn(MouseEvent e) {
 int c = table.columnAtPoint(e.getPoint());
 if (c >= 0 && table.getRowCount() > 0) { table.setColumnSelectionInterval(c, c); table.setRowSelectionInterval(0, table.getRowCount() - 1); }
            }
 private void menu(MouseEvent e) {
 int c = table.columnAtPoint(e.getPoint());
 if (c >= 0) columnHeaderMenu(c, e.getPoint());
            }
        });
 table.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { maybeDropdown(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeDropdown(e); }
 private void maybeDropdown(MouseEvent e) {
 if (!e.isPopupTrigger() && e.getButton() != MouseEvent.BUTTON1) return;
                Point p = e.getPoint();
 int r = table.rowAtPoint(p), viewCol = table.columnAtPoint(p);
 if (r < 0 || viewCol < 0) return;
 boolean header = model.isHeaderRowIndex(r);
                Rectangle cell = table.getCellRect(r, viewCol, false);
 boolean onArrow = header && p.x >= cell.x + cell.width - DROPDOWN_W - 2;
 if (onArrow || (header && e.isPopupTrigger())) {
 e.consume();
 showFilterMenu(table.convertColumnIndexToModel(viewCol), cell);
                }
            }
        });
    }

 public JTable getTable() { return table; }
 public SheetModel getModel() { return model; }

    /** Commit any in-progress cell edit (before saving). */
 public void commitEdits() {
 if (table.isEditing()) table.getCellEditor().stopCellEditing();
    }

 private void rowHeaderMenu(int row, java.awt.Point at) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem above = new JMenuItem("Insert row above"), below = new JMenuItem("Insert row below"), del = new JMenuItem("Delete row");
 above.addActionListener(e -> model.insertRow(row, true));
 below.addActionListener(e -> model.insertRow(row, false));
 del.addActionListener(e -> model.deleteRow(row));
 del.setEnabled(!model.isHeaderRowIndex(row));
 for (JMenuItem i : List.of(above, below, del)) { i.setFont(CELL_FONT); m.add(i); }
 m.show(rowHeader, at.x, at.y);
    }

 private void columnHeaderMenu(int viewCol, java.awt.Point at) {
 int col = table.convertColumnIndexToModel(viewCol);
        JPopupMenu m = new JPopupMenu();
        JMenuItem left = new JMenuItem("Insert column left"), right = new JMenuItem("Insert column right"), del = new JMenuItem("Delete column");
 left.addActionListener(e -> model.insertColumn(col, true));
 right.addActionListener(e -> model.insertColumn(col, false));
 del.addActionListener(e -> model.deleteColumn(col));
 for (JMenuItem i : List.of(left, right, del)) { i.setFont(CELL_FONT); m.add(i); }
 m.show(table.getTableHeader(), at.x, at.y);
    }

    /** Excel's AutoFilter menu: sort, clear, value checklist with (Select All). */
 private void showFilterMenu(int col, Rectangle anchor) {
        JPopupMenu menu = new JPopupMenu();
 menu.setBackground(Color.WHITE);
        JMenuItem asc = new JMenuItem("\u2191  Sort A to Z");
        JMenuItem desc = new JMenuItem("\u2193  Sort Z to A");
        JMenuItem clear = new JMenuItem("Clear Filter From \"" + model.headerText(col) + "\"");
 clear.setEnabled(model.isFiltered(col));
 asc.addActionListener(e -> model.sort(col, SheetModel.Sort.ASC));
 desc.addActionListener(e -> model.sort(col, SheetModel.Sort.DESC));
 clear.addActionListener(e -> model.filter(col, null));
 for (JMenuItem m : List.of(asc, desc, clear)) m.setFont(CELL_FONT);
 menu.add(asc); menu.add(desc); menu.addSeparator(); menu.add(clear); menu.addSeparator();

        List<String> values = model.distinctValues(col);
        List<JCheckBox> boxes = new ArrayList<>();
        JPanel list = new JPanel();
 list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
 list.setBackground(Color.WHITE);
        JCheckBox all = new JCheckBox("(Select All)", true);
 all.setFont(BOLD_FONT); all.setBackground(Color.WHITE);
 list.add(all);
 int shown = 0;
 for (String v : values) {
 if (shown++ >= 500) { JLabel more = new JLabel("  ... " + (values.size() - 500) + " more"); more.setFont(CELL_FONT); list.add(more); break; }
            JCheckBox cb = new JCheckBox(v.isEmpty() ? "(Blanks)" : v, true);
 cb.putClientProperty("value", v);
 cb.setFont(CELL_FONT); cb.setBackground(Color.WHITE);
 boxes.add(cb); list.add(cb);
        }
 all.addActionListener(e -> boxes.forEach(b -> b.setSelected(all.isSelected())));
 boxes.forEach(b -> b.addActionListener(e -> all.setSelected(boxes.stream().allMatch(JCheckBox::isSelected))));
        JScrollPane sp = new JScrollPane(list);
 sp.setPreferredSize(new Dimension(240, Math.min(260, 24 * (boxes.size() + 1) + 8)));
 sp.setBorder(BorderFactory.createLineBorder(HEADER_BORDER));
 menu.add(sp);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
 buttons.setBackground(Color.WHITE);
        JButton ok = new JButton("OK"), cancel = new JButton("Cancel");
 ok.setFont(CELL_FONT); cancel.setFont(CELL_FONT);
 ok.addActionListener(e -> {
            Set<String> allowed = new LinkedHashSet<>();
 for (JCheckBox b : boxes) if (b.isSelected()) allowed.add((String) b.getClientProperty("value"));
 model.filter(col, allowed.size() == boxes.size() ? null : allowed);
 menu.setVisible(false);
        });
 cancel.addActionListener(e -> menu.setVisible(false));
 buttons.add(ok); buttons.add(cancel);
 menu.add(buttons);
 menu.show(table, anchor.x, anchor.y + anchor.height);
    }

 private static Font pickFont() {
        String[] families = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
 for (String f : new String[] { "Calibri", "Aptos", "Segoe UI", "Helvetica Neue", "Arial" }) {
 if (Arrays.asList(families).contains(f)) return new Font(f, Font.PLAIN, 13);
        }
 return new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    }

    /** The grid: Excel colours, drag-reorder/resize columns, green active-cell outline drawn on top. */
 private static final class Grid extends JTable {
 private final SheetModel model;
        Grid(SheetModel m) {
 super(m);
 this.model = m;
 setAutoResizeMode(AUTO_RESIZE_OFF);
 setShowGrid(true);
 setGridColor(GRID);
 setIntercellSpacing(new Dimension(1, 1));
 setRowHeight(ROW_HEIGHT);
 setFont(CELL_FONT);
 setBackground(CELL_BG);
 setForeground(CELL_FG);
 setSelectionBackground(SELECTION_BG);
 setSelectionForeground(CELL_FG);
 setCellSelectionEnabled(true);
 setFillsViewportHeight(true);
 setDefaultRenderer(Object.class, new CellRenderer(m));
 setSurrendersFocusOnKeystroke(true);
 putClientProperty("JTable.autoStartsEdit", Boolean.TRUE);
 javax.swing.JTextField editorField = new javax.swing.JTextField();
 editorField.setFont(CELL_FONT);
 editorField.setBorder(BorderFactory.createLineBorder(GREEN, 2));
 setDefaultEditor(Object.class, new javax.swing.DefaultCellEditor(editorField) {
                @Override public boolean stopCellEditing() {
                    // Excel: Enter commits and moves down; Tab commits and moves right (JTable already does that)
 return super.stopCellEditing();
                }
            });
            JTableHeader h = getTableHeader();
 h.setReorderingAllowed(true);   // drag column letters to reorder, like moving columns in Excel
 h.setResizingAllowed(true);
 h.setDefaultRenderer(new ColumnHeaderRenderer(this));
 h.setPreferredSize(new Dimension(h.getPreferredSize().width, ROW_HEIGHT + 2));
 for (int i = 0; i < getColumnCount(); i++) getColumnModel().getColumn(i).setPreferredWidth(104);
 setToolTipText("");
        }

        @Override
 public String getToolTipText(MouseEvent e) {
 int r = rowAtPoint(e.getPoint()), c = columnAtPoint(e.getPoint());
 if (r >= 0 && c >= 0 && model.isHeaderRowIndex(r)) return "Click \u25BE to sort or filter this column";
 return null;
        }

        @Override
 protected void paintComponent(Graphics g) {
 super.paintComponent(g);
 int r = getSelectionModel().getLeadSelectionIndex();
 int c = getColumnModel().getSelectionModel().getLeadSelectionIndex();
 if (r < 0 || c < 0 || r >= getRowCount() || c >= getColumnCount()) return;
            Rectangle cell = getCellRect(r, c, true);
            Graphics2D g2 = (Graphics2D) g.create();
 g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
 g2.setColor(GREEN);
 g2.setStroke(new BasicStroke(2f));
 g2.drawRect(cell.x, cell.y, cell.width - 1, cell.height - 1);
 g2.fillRect(cell.x + cell.width - 4, cell.y + cell.height - 4, 5, 5);
 g2.dispose();
        }
    }

    /** White cells; header row gets a dropdown arrow and sort/filter glyphs; numbers right-aligned. */
 private static final class CellRenderer extends DefaultTableCellRenderer {
 private final SheetModel model;
 private boolean header;
 private String glyph = "";
        CellRenderer(SheetModel m) { this.model = m; }

        @Override
 public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            String s = v == null ? "" : v.toString();
 super.getTableCellRendererComponent(t, s, sel, false, r, c);
 header = model.isHeaderRowIndex(r);
 int mc = t.convertColumnIndexToModel(c);
 setFont(header ? BOLD_FONT : CELL_FONT);
 setBackground(sel ? SELECTION_BG : CELL_BG);
 setForeground(CELL_FG);
 setBorder(BorderFactory.createEmptyBorder(0, 4, 0, header ? DROPDOWN_W + 4 : 4));
 setHorizontalAlignment(!header && SheetModel.isNumeric(s) ? SwingConstants.RIGHT : SwingConstants.LEFT);
 glyph = "";
 if (header) {
 if (model.isFiltered(mc)) glyph = "\u25BC";                          // filled funnel-ish
 else if (model.getSortColumn() == mc) glyph = model.getSort() == SheetModel.Sort.ASC ? "\u25B4" : "\u25BE";
            }
 return this;
        }

        @Override
 protected void paintComponent(Graphics g) {
 super.paintComponent(g);
 if (!header) return;
            Graphics2D g2 = (Graphics2D) g.create();
 g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
 int x = getWidth() - DROPDOWN_W - 2, y = 2, h = getHeight() - 4;
 g2.setColor(DROPDOWN_BG);
 g2.fillRect(x, y, DROPDOWN_W, h);
 g2.setColor(HEADER_BORDER);
 g2.drawRect(x, y, DROPDOWN_W, h);
 g2.setColor(glyph.isEmpty() ? HEADER_FG : GREEN);
 g2.setFont(CELL_FONT.deriveFont(9f));
            String mark = glyph.isEmpty() ? "\u25BC" : glyph;
 int w = g2.getFontMetrics().stringWidth(mark);
 g2.drawString(mark, x + (DROPDOWN_W - w) / 2, y + h / 2 + 4);
 g2.dispose();
        }
    }

    /** Grey column letters; selected column darker with green underline. */
 private static final class ColumnHeaderRenderer extends JLabel implements TableCellRenderer {
 private final JTable table;
        ColumnHeaderRenderer(JTable table) {
 this.table = table;
 setOpaque(true);
 setHorizontalAlignment(CENTER);
 setFont(CELL_FONT);
        }
        @Override
 public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
 boolean active = table.isColumnSelected(c);
 setText(v == null ? "" : v.toString());
 setBackground(active ? HEADER_ACTIVE_BG : HEADER_BG);
 setForeground(active ? GREEN : HEADER_FG);
 setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, active ? 2 : 1, 1, active ? GREEN : HEADER_BORDER),
                    BorderFactory.createEmptyBorder(0, 2, 0, 2)));
 return this;
        }
    }

    /** Row numbers; selected row darker with green edge; filtered rows' numbers in blue like Excel. */
 private static final class RowHeaderRenderer extends JLabel implements ListCellRenderer<String> {
 private final JTable table;
 private final SheetModel model;
        RowHeaderRenderer(JTable table, SheetModel model) {
 this.table = table;
 this.model = model;
 setOpaque(true);
 setHorizontalAlignment(CENTER);
 setFont(CELL_FONT);
        }
        @Override
 public Component getListCellRendererComponent(JList<? extends String> l, String v, int i, boolean sel, boolean foc) {
 boolean active = table.isRowSelected(i);
 setText(v);
 setBackground(active ? HEADER_ACTIVE_BG : HEADER_BG);
 setForeground(active ? GREEN : (model.isAnyFilter() && !model.isHeaderRowIndex(i) ? FILTERED_ROW_NUMBER : HEADER_FG));
 setBorder(BorderFactory.createMatteBorder(0, 0, 1, active ? 2 : 1, active ? GREEN : HEADER_BORDER));
 return this;
        }
    }
}
