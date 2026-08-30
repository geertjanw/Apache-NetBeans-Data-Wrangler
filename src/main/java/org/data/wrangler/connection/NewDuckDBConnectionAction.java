package org.data.wrangler.connection;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.api.db.explorer.DatabaseException;
import org.netbeans.api.db.explorer.JDBCDriver;
import org.data.wrangler.driver.DuckDBDriverInstaller;
import org.data.wrangler.DuckDB;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;

/**
 * Adds "New DuckDB Connection..." to the context menu of the Databases root node
 * in the Services window. Unlike the generic New Connection wizard it knows about
 * in-memory mode, read-only mode and DuckDB config options.
 */
@ActionID(category = "Database", id = "org.data.wrangler.connection.NewDuckDBConnectionAction")
@ActionRegistration(displayName = "#CTL_NewDuckDBConnection", iconBase = "org/data/wrangler/duckdb.png")
@ActionReference(path = "Databases/Explorer/Root/Actions", position = 150)
@Messages("CTL_NewDuckDBConnection=Register DuckDB Database...")
public final class NewDuckDBConnectionAction implements ActionListener {

    @Override
 public void actionPerformed(ActionEvent e) {
        JDBCDriver driver = DuckDBDriverInstaller.driver();
 if (driver == null) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "DuckDB JDBC driver is not registered. Add duckdb_jdbc.jar under Services > Databases > Drivers.",
                    NotifyDescriptor.ERROR_MESSAGE));
 return;
        }
        DuckDBConnectionPanel panel = new DuckDBConnectionPanel();
        DialogDescriptor dd = new DialogDescriptor(panel,
                NbBundle.getMessage(NewDuckDBConnectionAction.class, "CTL_NewDuckDBConnection"));
 while (DialogDisplayer.getDefault().notify(dd) == DialogDescriptor.OK_OPTION) {
            DuckDBConnectionSettings s = panel.toSettings();
            String problem = s.validate();
 if (problem != null) {
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(problem, NotifyDescriptor.WARNING_MESSAGE));
 continue;
            }
 try {
                DatabaseConnection dc = DatabaseConnection.create(
 driver, s.buildUrl(), DuckDB.NOMINAL_USER, null, "", true, // no credentials: remember empty password so Connect never prompts
 s.effectiveDisplayName(), s.buildProperties());
                ConnectionManager.getDefault().addConnection(dc);
            } catch (DatabaseException ex) {
                Exceptions.printStackTrace(ex);
            }
 return;
        }
    }
}
