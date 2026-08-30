package org.data.wrangler.files;

import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.StyledDocument;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.core.spi.multiview.CloseOperationState;
import org.netbeans.core.spi.multiview.MultiViewElement;
import org.netbeans.core.spi.multiview.MultiViewElementCallback;
import org.data.wrangler.SqlEditorBridge;
import org.data.wrangler.analysis.AnalysisConnection;
import org.data.wrangler.analysis.StatementSplitter;
import org.data.wrangler.dataview.DuckDBResultTopComponent;
import org.data.wrangler.dataview.NestedTypeRewriter;
import org.openide.awt.UndoRedo;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.text.CloneableEditorSupport;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.windows.TopComponent;

/**
 * "Query" tab of a data-file window (Parquet, Excel): a real NetBeans SQL editor (so DuckDB
 * completion, docs and diagnostics all work) over a per-file query document,
 * bound to the DuckDB connection, with Run sending results to the nested-type
 * result viewer. The document is the same cached query file the context-menu
 * action used to open in a separate tab, so edits persist per Parquet file.
 */
public class DataFileQueryElement extends JPanel implements MultiViewElement {

 private static final RequestProcessor RP = new RequestProcessor("Parquet query", 1, true);

 private final DataObject dobj;
 private final File file;
 private final JToolBar toolbar = new JToolBar();
 private final JEditorPane pane = new JEditorPane();
 private final JLabel status = new JLabel(" ");
 private MultiViewElementCallback callback;
 private EditorCookie editorCookie;
 private DatabaseConnection dc;
 private boolean initialized;

 public DataFileQueryElement(Lookup lkp) {
 this.dobj = lkp.lookup(DataObject.class);
 this.file = dobj == null ? null : FileUtil.toFile(dobj.getPrimaryFile());
 setLayout(new BorderLayout());

 toolbar.setFloatable(false);
        JButton run = new JButton("Run (Ctrl+Shift+E)");
 run.addActionListener(e -> run(false));
        JButton runSel = new JButton("Run selection");
 runSel.addActionListener(e -> run(true));
 toolbar.add(run);
 toolbar.add(runSel);
 toolbar.addSeparator();
 toolbar.add(status);

 pane.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "duckdb.run");
 pane.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.META_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), "duckdb.run");
 pane.getActionMap().put("duckdb.run", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { run(pane.getSelectedText() != null); }
        });
 add(new JScrollPane(pane), BorderLayout.CENTER);
    }

 private void init() {
 if (initialized || file == null) return;
 initialized = true;
 try {
            FileObject q = SqlEditorOpener.queryFileFor(file);
 if (q.getSize() == 0) {
 try (OutputStream os = q.getOutputStream()) {
 os.write(QueryTemplates.forFile(file.getAbsolutePath()).getBytes(StandardCharsets.UTF_8));
                }
            }
            DataObject qd = DataObject.find(q);
 dc = TemplateConnectionBinder.duckDBConnection();
 if (dc != null) SqlEditorBridge.setConnection(qd, dc);
 editorCookie = qd.getLookup().lookup(EditorCookie.class);
            StyledDocument doc = editorCookie.openDocument();
 pane.setEditorKit(CloneableEditorSupport.getEditorKit("text/x-sql"));
 pane.setDocument(doc);
 status.setText(dc == null ? "No DuckDB connection" : "Connection: " + dc.getDisplayName());
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
 status.setText("Cannot open query: " + ex.getMessage());
        }
    }

 private void run(boolean selectionOnly) {
 if (dc == null) { status.setText("No DuckDB connection"); return; }
        String sql = selectionOnly ? pane.getSelectedText() : pane.getText();
 if (sql == null || sql.isBlank()) { status.setText("Nothing to run"); return; }
        List<StatementSplitter.Statement> stmts = StatementSplitter.split(sql);
 status.setText("Running " + stmts.size() + " statement" + (stmts.size() == 1 ? "" : "s") + "...");
        RP.post(() -> {
 try {
 if (dc.getJDBCConnection() == null) ConnectionManager.getDefault().connect(dc);
                Connection conn = AnalysisConnection.get(dc);
 if (conn == null) throw new SQLException("not connected");
 int ok = 0;
 for (StatementSplitter.Statement s : stmts) {
 if (returnsRows(conn, s.sql())) {
                        DuckDBResultTopComponent.run(conn, s.sql());
                    } else {
 synchronized (conn) {
 try (java.sql.Statement st = conn.createStatement()) { st.execute(s.sql()); }
                        }
                    }
 ok++;
                }
 int done = ok;
                SwingUtilities.invokeLater(() -> status.setText(done + " statement" + (done == 1 ? "" : "s") + " executed"));
            } catch (SQLException | org.netbeans.api.db.explorer.DatabaseException ex) {
                SwingUtilities.invokeLater(() -> status.setText("Error: " + firstLine(ex.getMessage())));
            }
        });
    }

 private static boolean returnsRows(Connection conn, String sql) {
 try {
            NestedTypeRewriter.describe(conn, sql);
 return true;
        } catch (SQLException e) {
 return false;
        }
    }

 private static String firstLine(String s) {
 if (s == null) return "";
 int i = s.indexOf('\n');
 return i < 0 ? s : s.substring(0, i);
    }

    // ---- MultiViewElement ----
    @Override public JComponent getVisualRepresentation() { return this; }
    @Override public JComponent getToolbarRepresentation() { return toolbar; }
    @Override public Action[] getActions() { return callback == null ? new Action[0] : callback.createDefaultActions(); }
    @Override public Lookup getLookup() { return dobj == null ? Lookup.EMPTY : dobj.getLookup(); }
    @Override public void componentOpened() {}
    @Override public void componentClosed() {
 if (editorCookie != null) {
 try { editorCookie.saveDocument(); } catch (IOException ex) { Exceptions.printStackTrace(ex); }
        }
    }
    @Override public void componentShowing() { init(); }
    @Override public void componentHidden() {
 if (editorCookie != null) {
 try { editorCookie.saveDocument(); } catch (IOException ignore) { }
        }
    }
    @Override public void componentActivated() { pane.requestFocusInWindow(); }
    @Override public void componentDeactivated() {}
    @Override public UndoRedo getUndoRedo() { return UndoRedo.NONE; }
    @Override public void setMultiViewCallback(MultiViewElementCallback cb) { this.callback = cb; }
    @Override public CloseOperationState canCloseElement() { return CloseOperationState.STATE_OK; }
}
