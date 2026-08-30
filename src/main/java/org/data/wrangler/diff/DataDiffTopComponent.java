package org.data.wrangler.diff;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import org.openide.windows.TopComponent;
import org.openide.util.NbBundle.Messages;

/** Shows the result of a data diff: summary, schema differences, rows only in A, rows only in B. */
@TopComponent.Description(preferredID = "DataDiffTopComponent", persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "output", openAtStartup = false, position = 2960)
@Messages("CTL_DataDiff=Data Diff")
public final class DataDiffTopComponent extends TopComponent {

    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel summary = new JLabel(" ");

    public DataDiffTopComponent(String title) {
        setLayout(new BorderLayout());
        setName(title);
        setDisplayName(title);
        summary.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        add(summary, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    /** Add one comparison (a file pair, or one sheet of a workbook pair). */
    public void addResult(String section, DiffService.Result r) {
        String prefix = section == null ? "" : section + ": ";
        StringBuilder sb = new StringBuilder("<html>");
        if (summary.getText().trim().length() > 0 && !summary.getText().equals(" ")) sb.append(summary.getText().replace("<html>", ""));
        sb.append(prefix).append(r.identical() ? "<b>identical</b>" : "<b>different</b>")
          .append(" &mdash; A: ").append(r.labelA()).append(" (").append(String.format("%,d", r.countA())).append(" rows)")
          .append(", B: ").append(r.labelB()).append(" (").append(String.format("%,d", r.countB())).append(" rows)")
          .append(", only in A: ").append(String.format("%,d", r.onlyInA().total()))
          .append(", only in B: ").append(String.format("%,d", r.onlyInB().total()))
          .append(", columns in common: ").append(r.commonColumns().size()).append("<br>");
        summary.setText(sb.toString());

        DefaultTableModel schema = new DefaultTableModel(new Object[] { "Column", "Type in A", "Type in B", "Status" }, 0) {
            @Override public boolean isCellEditable(int a, int b) { return false; }
        };
        for (DiffService.ColumnDiff c : r.schema()) schema.addRow(new Object[] { c.column(), c.typeA(), c.typeB(), c.status() });
        JTable st = new JTable(schema);
        st.setAutoCreateRowSorter(true);
        st.setDefaultRenderer(Object.class, new StatusRenderer());
        tabs.addTab(prefix + "Side by side", sideBySide(r));
        tabs.addTab(prefix + "Schema", new JScrollPane(st));
        tabs.addTab(prefix + "Only in A (" + r.onlyInA().total() + ")", rows(r.onlyInA()));
        tabs.addTab(prefix + "Only in B (" + r.onlyInB().total() + ")", rows(r.onlyInB()));
    }

    public void fail(String message) {
        summary.setText("<html><b>Comparison failed</b><br>" + message.replace("\n", "<br>"));
    }

    /** NetBeans' own diff view over the canonical text of both sources (common columns, sorted rows). */
    private static Component sideBySide(DiffService.Result r) {
        try {
            org.netbeans.api.diff.StreamSource a = org.netbeans.api.diff.StreamSource.createSource(
                    r.labelA(), r.labelA(), "text/plain", new java.io.StringReader(r.textA()));
            org.netbeans.api.diff.StreamSource b = org.netbeans.api.diff.StreamSource.createSource(
                    r.labelB(), r.labelB(), "text/plain", new java.io.StringReader(r.textB()));
            org.netbeans.api.diff.DiffView view = org.netbeans.api.diff.Diff.getDefault().createDiff(a, b);
            JPanel p = new JPanel(new BorderLayout());
            p.add(view.getComponent(), BorderLayout.CENTER);
            long shown = Math.min(r.countA(), DiffService.TEXT_ROWS);
            if (r.countA() > DiffService.TEXT_ROWS || r.countB() > DiffService.TEXT_ROWS) {
                p.add(new JLabel("  first " + String.format("%,d", DiffService.TEXT_ROWS) + " rows of each side; the Only in A / Only in B tabs cover all rows"), BorderLayout.SOUTH);
            }
            return p;
        } catch (java.io.IOException ex) {
            return new JLabel("Diff view unavailable: " + ex.getMessage());
        }
    }

    private static Component rows(DiffService.Rows r) {
        JTable t = new JTable(new DefaultTableModel(r.rows().toArray(Object[][]::new), r.columns().toArray()) {
            @Override public boolean isCellEditable(int a, int b) { return false; }
        });
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        t.setAutoCreateRowSorter(true);
        for (int i = 0; i < t.getColumnCount(); i++) t.getColumnModel().getColumn(i).setPreferredWidth(140);
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        if (r.total() > r.rows().size()) {
            p.add(new JLabel("  showing " + r.rows().size() + " of " + String.format("%,d", r.total()) + " rows"), BorderLayout.SOUTH);
        }
        return p;
    }

    private static final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v == null ? "" : v, sel, foc, row, col);
            String status = String.valueOf(t.getValueAt(row, 3));
            if (!sel) setForeground("same".equals(status) ? t.getForeground() : new Color(0xB0, 0x30, 0x20));
            return this;
        }
    }
}
