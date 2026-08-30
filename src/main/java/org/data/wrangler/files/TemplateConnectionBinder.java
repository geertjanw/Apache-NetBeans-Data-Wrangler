package org.data.wrangler.files;

import java.util.Arrays;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.api.db.explorer.DatabaseException;
import org.netbeans.api.db.explorer.JDBCDriver;
import org.data.wrangler.DuckDB;
import org.data.wrangler.SqlEditorBridge;
import org.data.wrangler.driver.DuckDBDriverInstaller;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataLoaderPool;
import org.openide.loaders.DataObject;
import org.openide.loaders.OperationAdapter;
import org.openide.loaders.OperationEvent;
import org.openide.modules.OnStart;
import org.openide.util.Exceptions;

/**
 * When a file is created from one of our Analytics templates, bind it to a
 * DuckDB connection so the editor opens with completion, docs and diagnostics
 * active, instead of an unbound SQL editor where none of that works.
 */
@OnStart
public final class TemplateConnectionBinder implements Runnable {

 private static final org.openide.util.RequestProcessor RP = new org.openide.util.RequestProcessor("DuckDB connect", 1);

    @Override
 public void run() {
        DataLoaderPool.getDefault().addOperationListener(new OperationAdapter() {
            @Override
 public void operationCreateFromTemplate(OperationEvent.Copy ev) {
                FileObject template = ev.getOriginalDataObject() == null ? null : ev.getOriginalDataObject().getPrimaryFile();
 if (template == null || !template.getPath().startsWith("Templates/Analytics/")) return;
                DataObject created = ev.getObject();
 if (created == null || !"sql".equalsIgnoreCase(created.getPrimaryFile().getExt())) return;
                DatabaseConnection dc = duckDBConnection();
 if (dc != null) {
                    SqlEditorBridge.setConnection(created, dc);
                    RP.post(() -> org.data.wrangler.analysis.AnalysisConnection.get(dc)); // connect now, off the EDT
                }
            }
        });
    }

 public static DatabaseConnection duckDBConnection() {
        DatabaseConnection dc = Arrays.stream(ConnectionManager.getDefault().getConnections())
                .filter(DuckDB::isDuckDB).findFirst().orElse(null);
 if (dc != null) return repairIfNoUser(dc);
        DatabaseConnection created = createInMemory();
 if (created == null) return null;
        // Use the instance the ConnectionManager holds, not the one we built: the manager
        // may wrap connections, and the JDBC connection lives on its instance.
 return Arrays.stream(ConnectionManager.getDefault().getConnections())
                .filter(c -> c.getName().equals(created.getName()) || c.getDatabaseURL().equals(created.getDatabaseURL()) && DuckDB.isDuckDB(c))
                .findFirst().orElse(created);
    }

    /**
     * Connections made by earlier builds have an empty user name, which makes
     * ConnectionManager.connect() a silent no-op. Replace such a connection with an
     * identical one that has the nominal user.
     */
 static DatabaseConnection repairIfNoUser(DatabaseConnection dc) {
 if (dc.getUser() != null && !dc.getUser().isBlank()) return dc;
 try {
            DatabaseConnection fixed = DatabaseConnection.create(dc.getJDBCDriver(), dc.getDatabaseURL(), DuckDB.NOMINAL_USER,
 dc.getSchema(), "", true, dc.getDisplayName(), dc.getConnectionProperties());
            ConnectionManager.getDefault().removeConnection(dc);
            ConnectionManager.getDefault().addConnection(fixed);
 return Arrays.stream(ConnectionManager.getDefault().getConnections())
                    .filter(c -> c.getDisplayName().equals(fixed.getDisplayName()) && DuckDB.isDuckDB(c)).findFirst().orElse(fixed);
        } catch (DatabaseException ex) {
            Exceptions.printStackTrace(ex);
 return dc;
        }
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
