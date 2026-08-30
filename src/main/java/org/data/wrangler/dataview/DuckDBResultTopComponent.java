package org.data.wrangler.dataview;

import java.awt.BorderLayout;
import java.awt.Component;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import org.openide.util.RequestProcessor;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.openide.util.NbBundle.Messages;

/** Output window tab that shows a result set with JSON-rendered nested columns. */
@TopComponent.Description(preferredID = "DuckDBResultTopComponent", persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "output", openAtStartup = false, position = 2950)
@Messages("CTL_DuckDBResultView=DuckDB Results")
public final class DuckDBResultTopComponent extends TopComponent {

 private static final RequestProcessor RP = new RequestProcessor("DuckDB result viewer", 1, true);
 private static final int MAX_ROWS = 5_000;
 private final JTable table = new JTable();
 private final JLabel status = new JLabel(" ");

 public DuckDBResultTopComponent() {
 setLayout(new BorderLayout());
 setName(org.openide.util.NbBundle.getMessage(DuckDBResultTopComponent.class, "CTL_DuckDBResultView"));
 table.setAutoCreateRowSorter(true);
 table.setDefaultRenderer(Object.class, new JsonAwareRenderer());
 add(new JScrollPane(table), BorderLayout.CENTER);
 add(status, BorderLayout.SOUTH);
    }

    /** Safe to call from any thread: window creation is marshalled to the EDT, the query runs in the background. */
 public static void run(Connection conn, String query) {
 if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> run(conn, query));
 return;
        }
        DuckDBResultTopComponent tc = new DuckDBResultTopComponent();
 tc.setDisplayName(shortName(query));
 try {
 org.openide.windows.Mode m = WindowManager.getDefault().findMode("output");
 if (m != null) m.dockInto(tc);
        } catch (RuntimeException ignore) { }
 tc.open();
 tc.requestActive();
 tc.status.setText("Running...");
        RP.post(() -> tc.execute(conn, query));
    }

 private static String shortName(String q) {
        String s = q.strip().replaceAll("\\s+", " ");
 return "DuckDB: " + (s.length() > 40 ? s.substring(0, 37) + "..." : s);
    }

 private void execute(Connection conn, String query) {
 try {
            String sql;
 boolean returnsRows;
 try {
 sql = NestedTypeRewriter.rewrite(query, NestedTypeRewriter.describe(conn, query));
 returnsRows = true;
            } catch (SQLException describeFailed) {
 sql = query;           // not DESCRIBE-able: DDL, DML, PRAGMA, ... -> run as-is
 returnsRows = false;
            }
            List<String> names = new ArrayList<>();
            List<Object[]> rows = new ArrayList<>();
 long updateCount = -1;
 synchronized (conn) {
 try (Statement st = conn.createStatement()) {
 boolean hasResultSet = st.execute(sql);
 if (hasResultSet) {
 try (ResultSet rs = st.getResultSet()) {
                            ResultSetMetaData md = rs.getMetaData();
 for (int i = 1; i <= md.getColumnCount(); i++) names.add(md.getColumnLabel(i));
 while (rs.next() && rows.size() < MAX_ROWS) {
                                Object[] row = new Object[names.size()];
 for (int i = 0; i < row.length; i++) row[i] = rs.getObject(i + 1);
 rows.add(row);
                            }
                        }
                    } else {
 updateCount = st.getUpdateCount();
                    }
                }
            }
 long count = updateCount;
 boolean tabular = returnsRows || !names.isEmpty();
            SwingUtilities.invokeLater(() -> {
 if (tabular) {
 table.setModel(new DefaultTableModel(rows.toArray(Object[][]::new), names.toArray()) {
                        @Override public boolean isCellEditable(int r, int c) { return false; }
                    });
 status.setText(rows.size() + (rows.size() == MAX_ROWS ? "+ rows (truncated)" : " rows"));
                } else {
 table.setModel(new DefaultTableModel(new Object[][] { { count < 0 ? "OK" : count } }, new Object[] { "Rows affected" }) {
                        @Override public boolean isCellEditable(int r, int c) { return false; }
                    });
 status.setText(count < 0 ? "Statement executed" : count + " row" + (count == 1 ? "" : "s") + " affected");
                }
            });
        } catch (SQLException | RuntimeException ex) {
            // A failing statement is a normal outcome here: show it in the tab, do not raise an exception notification.
            String msg = String.valueOf(ex.getMessage());
            SwingUtilities.invokeLater(() -> {
 removeAll();
                JTextArea err = new JTextArea(msg);
 err.setEditable(false);
 err.setLineWrap(true);
 err.setWrapStyleWord(true);
 add(new JScrollPane(err), BorderLayout.CENTER);
 status.setText("Failed");
 add(status, BorderLayout.SOUTH);
 revalidate();
 repaint();
            });
        }
    }

    /** Shows compact JSON in the cell and pretty JSON in the tooltip. */
 private static final class JsonAwareRenderer extends DefaultTableCellRenderer {
        @Override
 public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
 super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            String s = v == null ? "NULL" : v.toString();
 if (s.startsWith("{") || s.startsWith("[")) {
 setToolTipText("<html><pre>" + escape(pretty(s)) + "</pre></html>");
            } else {
 setToolTipText(null);
            }
 if (v == null) setText("NULL");
 return this;
        }

 private static String pretty(String json) {
            StringBuilder sb = new StringBuilder();
 int indent = 0; boolean inStr = false;
 for (int i = 0; i < json.length(); i++) {
 char ch = json.charAt(i);
 if (ch == '"' && (i == 0 || json.charAt(i - 1) != '\\')) inStr = !inStr;
 if (inStr) { sb.append(ch); continue; }
 switch (ch) {
 case '{', '[' -> { sb.append(ch).append('\n'); indent++; sb.append("  ".repeat(indent)); }
 case '}', ']' -> { sb.append('\n'); indent--; sb.append("  ".repeat(indent)).append(ch); }
 case ',' -> sb.append(",\n").append("  ".repeat(indent));
 case ':' -> sb.append(": ");
 default -> { if (!Character.isWhitespace(ch)) sb.append(ch); }
                }
            }
 return sb.toString();
        }

 private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    }

 static void ensureUiReady(Runnable r) { WindowManager.getDefault().invokeWhenUIReady(r); }
}
