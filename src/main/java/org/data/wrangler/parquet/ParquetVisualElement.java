package org.data.wrangler.parquet;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.core.spi.multiview.CloseOperationState;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.MultiViewElementCallback;
import org.data.wrangler.analysis.AnalysisConnection;
import org.data.wrangler.files.TemplateConnectionBinder;
import org.data.wrangler.parquet.ParquetInspector.ColumnInfo;
import org.data.wrangler.parquet.ParquetInspector.Report;
import org.data.wrangler.parquet.ParquetInspector.RowGroupInfo;
import org.openide.awt.UndoRedo;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.windows.TopComponent;

/**
 * "Visual" tab of the Parquet editor: file summary, columns with storage
 * breakdown, row groups, and a data preview. Everything is read through DuckDB.
 */
@MultiViewElement.Registration(
 displayName = "#LBL_ParquetVisual",
 iconBase = "org/data/wrangler/parquet.png",
 mimeType = "application/x-parquet",
 persistenceType = TopComponent.PERSISTENCE_NEVER,
 preferredID = "ParquetVisual",
 position = 1000)
@Messages("LBL_ParquetVisual=Visual")
public final class ParquetVisualElement extends JPanel implements MultiViewElement {

 private static final RequestProcessor RP = new RequestProcessor("Parquet inspector", 1, true);
 private static final int PREVIEW_ROWS = 50;

 private final DataObject dobj;
 private final File file;
 private final JToolBar toolbar = new JToolBar();
 private final JLabel status = new JLabel(" ");
 private final JPanel summary = new JPanel();
 private final ColumnsModel columnsModel = new ColumnsModel();
 private final JTable columnsTable = new JTable(columnsModel);
 private final JTable rowGroupsTable = new JTable();
 private final JTable previewTable = new JTable();
 private MultiViewElementCallback callback;
 private boolean loaded;

 public ParquetVisualElement(Lookup lkp) {
 this.dobj = lkp.lookup(DataObject.class);
 this.file = dobj == null ? null : FileUtil.toFile(dobj.getPrimaryFile());
 setLayout(new BorderLayout());

 toolbar.setFloatable(false);
        JButton convert = new JButton("Convert \u25BE");
 convert.addActionListener(e -> { if (dobj != null) org.data.wrangler.convert.ConvertWithDuckDBAction.popupFor(dobj).show(convert, 0, convert.getHeight()); });
        JButton refresh = new JButton("Refresh");
 refresh.addActionListener(e -> { loaded = false; load(); });
 toolbar.add(convert);
 toolbar.add(refresh);

 summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
 summary.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

 columnsTable.setAutoCreateRowSorter(true);
 columnsTable.setDefaultRenderer(StorageBar.class, new StorageBarRenderer());
 rowGroupsTable.setAutoCreateRowSorter(true);
 previewTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        JPanel top = new JPanel(new BorderLayout());
 top.add(summary, BorderLayout.NORTH);
        JSplitPane meta = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
 titled("Columns", new JScrollPane(columnsTable)),
 titled("Row groups", new JScrollPane(rowGroupsTable)));
 meta.setResizeWeight(0.75);
 top.add(meta, BorderLayout.CENTER);

        JSplitPane all = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top,
 titled("Preview (first " + PREVIEW_ROWS + " rows, nested values as JSON)", new JScrollPane(previewTable)));
 all.setResizeWeight(0.6);
 add(all, BorderLayout.CENTER);
 add(status, BorderLayout.SOUTH);
    }

 private static JComponent titled(String title, JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
 p.setBorder(BorderFactory.createTitledBorder(title));
 p.add(c, BorderLayout.CENTER);
 return p;
    }

 private void load() {
 if (loaded || file == null) return;
 loaded = true;
 status.setText("Reading " + file.getName() + " with DuckDB...");
        RP.post(() -> {
 try {
                DatabaseConnection dc = TemplateConnectionBinder.duckDBConnection();
 if (dc == null) throw new SQLException("No DuckDB connection available");
 if (dc.getJDBCConnection() == null) ConnectionManager.getDefault().connect(dc);
                Connection conn = AnalysisConnection.get(dc);
 if (conn == null) throw new SQLException("DuckDB connection \"" + dc.getDisplayName() + "\" could not be opened (see the IDE log)");
                Report r = new ParquetInspector(conn).inspect(file, PREVIEW_ROWS);
                SwingUtilities.invokeLater(() -> show(r));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> status.setText("Failed: " + ex.getMessage()));
            }
        });
    }

 private void show(Report r) {
 summary.removeAll();
        ParquetInspector.FileInfo f = r.file();
 summary.add(bold(file.getName()));
 summary.add(line(String.format("%,d rows  \u00b7  %d row group%s  \u00b7  %d columns  \u00b7  %s on disk",
 f.rows(), f.rowGroups(), f.rowGroups() == 1 ? "" : "s", r.columns().size(), ParquetInspector.humanBytes(f.sizeBytes()))));
 summary.add(line(String.format("Column data: %s compressed from %s (%.1fx)  \u00b7 written by %s  \u00b7 format v%d",
                ParquetInspector.humanBytes(f.compressedBytes()), ParquetInspector.humanBytes(f.uncompressedBytes()),
 f.ratio(), f.createdBy(), f.formatVersion())));
 summary.add(line(file.getAbsolutePath()));
 summary.revalidate();

 columnsModel.set(r.columns());
 columnsTable.getColumnModel().getColumn(5).setPreferredWidth(160);

        DefaultTableModel rg = new DefaultTableModel(new Object[] { "Row group", "Rows", "Compressed", "Uncompressed", "Ratio" }, 0) {
            @Override public boolean isCellEditable(int a, int b) { return false; }
        };
 for (RowGroupInfo g : r.rowGroups()) {
 rg.addRow(new Object[] { g.id(), g.rows(), ParquetInspector.humanBytes(g.compressedBytes()),
                    ParquetInspector.humanBytes(g.uncompressedBytes()),
 g.compressedBytes() == 0 ? "" : String.format("%.1fx", (double) g.uncompressedBytes() / g.compressedBytes()) });
        }
 rowGroupsTable.setModel(rg);

 previewTable.setModel(new DefaultTableModel(r.preview().rows().toArray(Object[][]::new), r.preview().columns().toArray()) {
            @Override public boolean isCellEditable(int a, int b) { return false; }
        });
 for (int i = 0; i < previewTable.getColumnCount(); i++) previewTable.getColumnModel().getColumn(i).setPreferredWidth(140);
 status.setText(" ");
    }

 private static JLabel bold(String s) {
        JLabel l = new JLabel(s);
 l.setFont(l.getFont().deriveFont(java.awt.Font.BOLD, l.getFont().getSize() + 2f));
 return l;
    }

 private static JLabel line(String s) {
        JLabel l = new JLabel(s);
 l.setForeground(UIManager.getColor("Label.disabledForeground"));
 return l;
    }

    /** Marker value type so the storage column gets the bar renderer. */
 record StorageBar(long bytes, long max) {}

 private static final class ColumnsModel extends AbstractTableModel {
 private static final String[] COLS = { "Column", "SQL type", "Physical", "Encoding", "Codec", "Compressed", "Uncompressed", "Min", "Max", "Nulls" };
 private List<ColumnInfo> rows = List.of();
 private long max = 1;

 void set(List<ColumnInfo> r) {
 rows = r;
 max = Math.max(1, r.stream().mapToLong(ColumnInfo::compressedBytes).max().orElse(1));
 fireTableStructureChanged();
        }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }
        @Override public Class<?> getColumnClass(int c) { return c == 5 ? StorageBar.class : c == 9 ? Long.class : String.class; }
        @Override public Object getValueAt(int r, int c) {
            ColumnInfo x = rows.get(r);
 return switch (c) {
 case 0 -> x.name();
 case 1 -> x.sqlType();
 case 2 -> x.physicalType();
 case 3 -> x.encodings() == null ? "" : x.encodings().replace("[", "").replace("]", "");
 case 4 -> x.compression();
 case 5 -> new StorageBar(x.compressedBytes(), max);
 case 6 -> ParquetInspector.humanBytes(x.uncompressedBytes());
 case 7 -> x.min();
 case 8 -> x.max();
 default -> x.nullCount();
            };
        }
    }

    /** Draws a proportional bar behind the byte count so the biggest columns stand out. */
 private static final class StorageBarRenderer extends DefaultTableCellRenderer {
 private double fraction;
        @Override
 public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            StorageBar b = (StorageBar) v;
 fraction = b == null ? 0 : (double) b.bytes() / b.max();
 super.getTableCellRendererComponent(t, b == null ? "" : ParquetInspector.humanBytes(b.bytes()), sel, foc, r, c);
 return this;
        }
        @Override
 protected void paintComponent(Graphics g) {
 super.paintComponent(g);
 int w = (int) (getWidth() * fraction);
            Color base = UIManager.getColor("Component.accentColor");
 if (base == null) base = new Color(40, 90, 180);
 g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 60));
 g.fillRect(0, 2, w, getHeight() - 4);
            // re-paint the text over the bar
 g.setColor(getForeground());
 g.setFont(getFont());
 int textY = (getHeight() + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2;
 g.drawString(getText(), 4, textY);
        }
        @Override public Dimension getPreferredSize() { return new Dimension(160, super.getPreferredSize().height); }
    }

    // ---- MultiViewElement ----
    @Override public JComponent getVisualRepresentation() { return this; }
    @Override public JComponent getToolbarRepresentation() { return toolbar; }
    @Override public Action[] getActions() { return callback == null ? new Action[0] : callback.createDefaultActions(); }
    @Override public Lookup getLookup() { return dobj == null ? Lookup.EMPTY : dobj.getLookup(); }
 private final java.beans.PropertyChangeListener renameListener = ev -> {
 if (DataObject.PROP_NAME.equals(ev.getPropertyName())) SwingUtilities.invokeLater(this::updateTitle);
    };

 private void updateTitle() {
 if (callback == null || dobj == null) return;
        String name = dobj.getPrimaryFile().getNameExt();
 callback.updateTitle(name);
        TopComponent tc = callback.getTopComponent();
 if (tc != null) {
 tc.setDisplayName(name);
 tc.setToolTipText(dobj.getPrimaryFile().getPath());
        }
    }

    @Override public void componentOpened() {
 if (dobj != null) dobj.addPropertyChangeListener(renameListener);
 updateTitle();
    }
    @Override public void componentClosed() {
 if (dobj != null) dobj.removePropertyChangeListener(renameListener);
    }
    @Override public void componentShowing() { updateTitle(); load(); }
    @Override public void componentHidden() {}
    @Override public void componentActivated() {}
    @Override public void componentDeactivated() {}
    @Override public UndoRedo getUndoRedo() { return UndoRedo.NONE; }
    @Override public void setMultiViewCallback(MultiViewElementCallback cb) { this.callback = cb; updateTitle(); }
    @Override public CloseOperationState canCloseElement() { return CloseOperationState.STATE_OK; }
}
