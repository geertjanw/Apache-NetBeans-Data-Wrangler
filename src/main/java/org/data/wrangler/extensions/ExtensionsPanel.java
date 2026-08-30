package org.data.wrangler.extensions;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.RequestProcessor;

/** Table of extensions with Install / Load buttons. */
public final class ExtensionsPanel extends JPanel {

 private static final RequestProcessor RP = new RequestProcessor("DataWrangler extensions", 1);
 private final ExtensionService service;
 private final Model model = new Model();
 private final JTable table = new JTable(model);
 private final Runnable onChange;

 public ExtensionsPanel(Connection conn, Runnable onChange) {
 super(new BorderLayout());
 this.service = new ExtensionService(conn);
 this.onChange = onChange;
 table.setAutoCreateRowSorter(true);
 add(new JScrollPane(table), BorderLayout.CENTER);

        JButton install = new JButton("Install");
        JButton load = new JButton("Load");
        JButton refresh = new JButton("Refresh");
 install.addActionListener(e -> withSelected(service::install));
 load.addActionListener(e -> withSelected(service::load));
 refresh.addActionListener(e -> refresh());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
 buttons.add(install); buttons.add(load); buttons.add(refresh);
 add(buttons, BorderLayout.SOUTH);
 refresh();
    }

 private interface SqlOp { void run(String name) throws SQLException; }

 private void withSelected(SqlOp op) {
 int row = table.getSelectedRow();
 if (row < 0) return;
        String name = model.rows.get(table.convertRowIndexToModel(row)).name();
        RP.post(() -> {
 try {
 op.run(name);
 if (onChange != null) onChange.run();
            } catch (SQLException | IllegalArgumentException ex) {
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
 new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE)));
            }
 refresh();
        });
    }

 private void refresh() {
        RP.post(() -> {
 try {
                List<DuckDBExtension> rows = service.list();
                SwingUtilities.invokeLater(() -> model.set(rows));
            } catch (SQLException ex) {
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(
 new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE)));
            }
        });
    }

 private static final class Model extends AbstractTableModel {
 private static final String[] COLS = { "Extension", "Installed", "Loaded", "Version", "Description" };
 private List<DuckDBExtension> rows = List.of();

 void set(List<DuckDBExtension> r) { rows = r; fireTableDataChanged(); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }
        @Override public Class<?> getColumnClass(int c) { return c == 1 || c == 2 ? Boolean.class : String.class; }
        @Override public Object getValueAt(int r, int c) {
            DuckDBExtension e = rows.get(r);
 return switch (c) {
 case 0 -> e.name();
 case 1 -> e.installed();
 case 2 -> e.loaded();
 case 3 -> e.version();
 default -> e.description();
            };
        }
    }
}
