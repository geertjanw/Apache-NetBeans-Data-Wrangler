package org.data.wrangler.files;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.api.db.explorer.DatabaseException;
import org.netbeans.api.db.explorer.JDBCDriver;
import org.data.wrangler.DuckDB;
import org.data.wrangler.driver.DuckDBDriverInstaller;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;

/**
 * Right-click a CSV / Parquet / JSON file in Projects or Files and pick
 * "Query with DuckDB": the SQL editor opens with a read_* query bound to a
 * DuckDB connection (an in-memory one is created if the user has none).
 */
@ActionID(category = "Tools", id = "org.data.wrangler.files.QueryWithDuckDBAction")
@ActionRegistration(displayName = "#CTL_QueryWithDuckDB", iconBase = "org/data/wrangler/duckdb.png", lazy = true)
@ActionReferences({
    // file nodes in Projects / Files / Favorites
    @ActionReference(path = "Loaders/" + CsvDataObject.MIME + "/Actions", position = 250),
    @ActionReference(path = "Loaders/" + ParquetDataObject.MIME + "/Actions", position = 100),
    @ActionReference(path = "Loaders/" + org.data.wrangler.excel.XlsxDataObject.MIME + "/Actions", position = 100),
    @ActionReference(path = "Loaders/text/x-json/Actions", position = 250),
    @ActionReference(path = "Loaders/application/json/Actions", position = 250),
    // right-click inside an open CSV / JSON editor
    @ActionReference(path = "Editors/" + CsvDataObject.MIME + "/Popup", position = 100, separatorAfter = 150),
    @ActionReference(path = "Editors/text/x-json/Popup", position = 100, separatorAfter = 150),
    @ActionReference(path = "Editors/application/json/Popup", position = 100, separatorAfter = 150)
})
@Messages("CTL_QueryWithDuckDB=Query with DuckDB")
public final class QueryWithDuckDBAction implements ActionListener {

 private final List<DataObject> context;

 public QueryWithDuckDBAction(List<DataObject> context) {
 this.context = context;
    }

    @Override
 public void actionPerformed(ActionEvent e) {
 queryFiles(context);
    }

    /** Opens a DuckDB SQL editor with a read_* query for each data file. Also used by ParquetDataObject's Open. */
 public static void queryFiles(List<DataObject> files) {
        List<DataObject> others = new java.util.ArrayList<>();
 for (DataObject d : files) {
 if (d instanceof DataFileDataObject p) p.openQueryTab(); else others.add(d);
        }
 if (others.isEmpty()) return;
        DatabaseConnection dc = pickConnection();
 if (dc == null) return;
 for (DataObject d : others) {
            File f = FileUtil.toFile(d.getPrimaryFile());
 if (f == null) continue;
 try {
                SqlEditorOpener.open(QueryTemplates.forFile(f.getAbsolutePath()), dc, f);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

 private static DatabaseConnection pickConnection() {
        List<DatabaseConnection> duck = java.util.Arrays.stream(ConnectionManager.getDefault().getConnections())
                .filter(DuckDB::isDuckDB).toList();
 if (duck.isEmpty()) {
 return createInMemory();
        }
 if (duck.size() == 1) return duck.get(0);

        JComboBox<DatabaseConnection> combo = new JComboBox<>(duck.toArray(DatabaseConnection[]::new));
 combo.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> l, Object v, int i, boolean s, boolean f) {
 super.getListCellRendererComponent(l, v, i, s, f);
 if (v instanceof DatabaseConnection c) setText(c.getDisplayName());
 return this;
            }
        });
        JPanel p = new JPanel(new BorderLayout(6, 6));
 p.add(new JLabel("DuckDB connection:"), BorderLayout.WEST);
 p.add(combo, BorderLayout.CENTER);
        DialogDescriptor dd = new DialogDescriptor(p, "Query with DuckDB");
 if (DialogDisplayer.getDefault().notify(dd) != DialogDescriptor.OK_OPTION) return null;
 return (DatabaseConnection) combo.getSelectedItem();
    }

 private static DatabaseConnection createInMemory() {
        JDBCDriver driver = DuckDBDriverInstaller.driver();
 if (driver == null) return null;
 try {
            DatabaseConnection dc = DatabaseConnection.create(driver, DuckDB.IN_MEMORY_URL, DuckDB.NOMINAL_USER, null, "", true, "DuckDB (in-memory)");
            ConnectionManager.getDefault().addConnection(dc);
 return dc;
        } catch (DatabaseException ex) {
            Exceptions.printStackTrace(ex);
 return null;
        }
    }
}
