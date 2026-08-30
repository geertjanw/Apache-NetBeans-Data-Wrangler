package org.data.wrangler.excel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.core.spi.multiview.CloseOperationState;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.MultiViewElementCallback;
import org.netbeans.core.spi.multiview.MultiViewFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.io.IOException;
import org.data.wrangler.analysis.AnalysisConnection;
import org.data.wrangler.convert.ConvertWithDuckDBAction;
import org.data.wrangler.files.TemplateConnectionBinder;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.UndoRedo;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.TopComponent;

/**
 * "Visual" tab for Excel workbooks: name box + formula bar on top, one
 * spreadsheet grid per sheet, Excel-style sheet tabs along the bottom.
 * Cells come from DuckDB's read_xlsx.
 */
@MultiViewElement.Registration(
 displayName = "#LBL_XlsxVisual",
 iconBase = XlsxDataObject.ICON,
 mimeType = XlsxDataObject.MIME,
 persistenceType = TopComponent.PERSISTENCE_NEVER,
 preferredID = "XlsxVisual",
 position = 1000)
@Messages("LBL_XlsxVisual=Visual")
public final class XlsxVisualElement extends JPanel implements MultiViewElement {

 private static final RequestProcessor RP = new RequestProcessor("Excel inspector", 1, true);
 private static final int MAX_ROWS = 10_000;

 private final DataObject dobj;
 private final File file;
 private final JToolBar toolbar = new JToolBar();
 private final JCheckBox headerRow = new JCheckBox("First row is header", true);
 private final JTextField nameBox = new JTextField("A1", 6);
 private final JTextField formulaBar = new JTextField();
 private final SheetTabStrip sheets = new SheetTabStrip();
 private final Map<String, SpreadsheetTable> tables = new LinkedHashMap<>();
 private final JButton save = new JButton("Save");
 private final JLabel status = new JLabel(" ");
 private MultiViewElementCallback callback;
 private boolean loaded;

 public XlsxVisualElement(Lookup lkp) {
 this.dobj = lkp.lookup(DataObject.class);
 this.file = dobj == null ? null : FileUtil.toFile(dobj.getPrimaryFile());
 setLayout(new BorderLayout());

 toolbar.setFloatable(false);
        JButton convert = new JButton("Convert \u25BE");
 convert.addActionListener(e -> { if (dobj != null) ConvertWithDuckDBAction.popupFor(dobj).show(convert, 0, convert.getHeight()); });
        JButton refresh = new JButton("Refresh");
 refresh.addActionListener(e -> { loaded = false; sheets.clear(); load(); });
        JButton clearFilters = new JButton("Clear all filters");
 clearFilters.addActionListener(e -> { SpreadsheetTable t = current(); if (t != null) t.getModel().clearFilters(); });
 headerRow.addActionListener(e -> { SpreadsheetTable t = current(); if (t != null) t.getModel().setHeaderRow(headerRow.isSelected()); });
 save.setEnabled(false);
 save.addActionListener(e -> save());
 toolbar.add(save); toolbar.add(convert); toolbar.add(refresh); toolbar.addSeparator(); toolbar.add(headerRow); toolbar.add(clearFilters);
 getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(javax.swing.KeyStroke.getKeyStroke("ctrl S"), "duckdb.save");
 getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(javax.swing.KeyStroke.getKeyStroke("meta S"), "duckdb.save");
 getActionMap().put("duckdb.save", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { save(); }
        });

        // Excel's name box + formula bar
        JPanel bar = new JPanel(new BorderLayout(6, 0));
 bar.setBackground(Color.WHITE);
 bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, SpreadsheetTable.HEADER_BORDER),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
 nameBox.setEditable(false);
 nameBox.setFont(SpreadsheetTable.CELL_FONT);
 nameBox.setHorizontalAlignment(JTextField.CENTER);
 nameBox.setPreferredSize(new Dimension(70, 24));
 formulaBar.setFont(SpreadsheetTable.CELL_FONT);
 formulaBar.addActionListener(e -> {                       // Enter in the formula bar commits to the active cell
            SpreadsheetTable t = current();
 if (t == null) return;
            JTable table = t.getTable();
 int r = table.getSelectionModel().getLeadSelectionIndex();
 int c = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
 if (r >= 0 && c >= 0) { table.setValueAt(formulaBar.getText(), r, c); table.requestFocusInWindow(); }
        });
        JLabel fx = new JLabel(" fx  ");
 fx.setFont(SpreadsheetTable.CELL_FONT.deriveFont(java.awt.Font.ITALIC));
 fx.setForeground(SpreadsheetTable.HEADER_FG);
        JPanel left = new JPanel(new BorderLayout());
 left.setOpaque(false);
 left.add(nameBox, BorderLayout.WEST);
 left.add(fx, BorderLayout.EAST);
 bar.add(left, BorderLayout.WEST);
 bar.add(formulaBar, BorderLayout.CENTER);

 add(bar, BorderLayout.NORTH);
 add(sheets, BorderLayout.CENTER);
 add(status, BorderLayout.SOUTH);
    }

 private SpreadsheetTable current() {
        Component c = sheets.currentSheet();
 return c instanceof SpreadsheetTable t ? t : null;
    }

 private void wire(SpreadsheetTable t) {
        JTable table = t.getTable();
 t.getModel().addModifiedListener(this::updateModified);
 t.getModel().addTableModelListener(e -> updateModified());
        Runnable sync = () -> {
 int r = table.getSelectionModel().getLeadSelectionIndex();
 int c = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
 if (r < 0 || c < 0 || r >= table.getRowCount() || c >= table.getColumnCount()) return;
 int mc = table.convertColumnIndexToModel(c);
 nameBox.setText(XlsxSheets.columnLabel(mc) + t.getModel().displayRowNumber(r));
            Object v = table.getValueAt(r, c);
 formulaBar.setText(v == null ? "" : v.toString());
        };
 table.getSelectionModel().addListSelectionListener(e -> sync.run());
 table.getColumnModel().getSelectionModel().addListSelectionListener(e -> sync.run());
    }

 private boolean anyModified() {
 return tables.values().stream().anyMatch(t -> t.getModel().isModified());
    }

 private void updateModified() {
 boolean m = anyModified();
 save.setEnabled(m);
 if (callback != null && dobj != null) callback.updateTitle(dobj.getPrimaryFile().getNameExt() + (m ? " *" : ""));
    }

 private void save() {
 if (file == null) return;
 tables.values().forEach(SpreadsheetTable::commitEdits);
        List<XlsxWriter.SheetData> data = new ArrayList<>();
 tables.forEach((name, t) -> data.add(new XlsxWriter.SheetData(name, t.getModel().rowsForSave())));
 try {
            // Write through the FileObject so NetBeans sees an IDE-initiated save:
            // Local History records a version, and other views get the change event.
 org.openide.filesystems.FileObject fo = dobj.getPrimaryFile();
 org.openide.filesystems.FileLock lock = fo.lock();
 try (java.io.OutputStream os = fo.getOutputStream(lock)) {
                XlsxWriter.write(os, data);
            } finally {
 lock.releaseLock();
            }
 tables.values().forEach(t -> t.getModel().setModified(false));
 updateModified();
 status.setText("Saved " + file.getName() + " (values only: formulas and formatting are not preserved)");
        } catch (IOException ex) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message("Save failed: " + ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
        }
    }

 private void load() {
 if (loaded || file == null) return;
 loaded = true;
 tables.clear();
 status.setText("Reading " + file.getName() + " with DuckDB (excel extension)...");
        RP.post(() -> {
 try {
                List<String> names = XlsxSheets.sheetNames(file);
                DatabaseConnection dc = TemplateConnectionBinder.duckDBConnection();
 if (dc == null) throw new SQLException("No DuckDB connection available");
 if (dc.getJDBCConnection() == null) ConnectionManager.getDefault().connect(dc);
                Connection conn = AnalysisConnection.get(dc);
 if (conn == null) throw new SQLException("DuckDB connection \"" + dc.getDisplayName() + "\" could not be opened (see the IDE log)");
                XlsxInspector insp = new XlsxInspector(conn);
 try {
 insp.ensureExtension();
                } catch (SQLException ex) {
 throw new SQLException("DuckDB excel extension could not be loaded (network needed once): " + firstLine(ex.getMessage()), ex);
                }
 for (String name : names) {
                    XlsxInspector.Sheet sheet = insp.read(file, name, MAX_ROWS);
                    SwingUtilities.invokeLater(() -> {
                        SpreadsheetTable t = new SpreadsheetTable(sheet);
 wire(t);
 tables.put(name, t);
 sheets.addSheet(name, t);
 if (sheets.currentSheet() == t) {
 headerRow.setSelected(t.getModel().isHeaderRow());
 t.getTable().changeSelection(0, 0, false, false);
                        }
                    });
                }
 int n = names.size();
                SwingUtilities.invokeLater(() -> status.setText(n + " sheet" + (n == 1 ? "" : "s") + "  \u00b7 up to " + String.format("%,d", MAX_ROWS) + " rows per sheet  \u00b7 click \u25BE in a header cell to sort or filter"));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
 status.setText("Failed: " + firstLine(ex.getMessage()));
                    DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
                });
            }
        });
    }

 private static String firstLine(String s) {
 if (s == null) return "";
 int i = s.indexOf('\n');
 return i < 0 ? s : s.substring(0, i);
    }

 private void updateTitle() {
 if (callback == null || dobj == null) return;
 callback.updateTitle(dobj.getPrimaryFile().getNameExt());
    }

    @Override public JComponent getVisualRepresentation() { return this; }
    @Override public JComponent getToolbarRepresentation() { return toolbar; }
    @Override public Action[] getActions() { return callback == null ? new Action[0] : callback.createDefaultActions(); }
    @Override public Lookup getLookup() { return dobj == null ? Lookup.EMPTY : dobj.getLookup(); }
    @Override public void componentOpened() { updateTitle(); }
    @Override public void componentClosed() {}
    @Override public void componentShowing() { updateTitle(); load(); }
    @Override public void componentHidden() {}
    @Override public void componentActivated() {}
    @Override public void componentDeactivated() {}
    @Override public UndoRedo getUndoRedo() { return UndoRedo.NONE; }
    @Override public void setMultiViewCallback(MultiViewElementCallback cb) { this.callback = cb; updateTitle(); }
    @Override public CloseOperationState canCloseElement() {
 if (!anyModified()) return CloseOperationState.STATE_OK;
 javax.swing.AbstractAction proceed = new javax.swing.AbstractAction("Save") {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { save(); }
        };
 javax.swing.AbstractAction discard = new javax.swing.AbstractAction("Discard") {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { tables.values().forEach(t -> t.getModel().setModified(false)); }
        };
 return MultiViewFactory.createUnsafeCloseState("xlsx-modified", proceed, discard);
    }
}
