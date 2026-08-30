package org.data.wrangler.extensions;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.data.wrangler.DuckDB;
import org.data.wrangler.completion.FunctionCatalog;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;

/**
 * "Manage DuckDB Extensions..." on a connection node. Shows the output of
 * duckdb_extensions() and lets the user INSTALL / LOAD. After a LOAD the function
 * completion cache for that connection is invalidated so new functions complete.
 */
@ActionID(category = "Database", id = "org.data.wrangler.extensions.ManageExtensionsAction")
@ActionRegistration(displayName = "#CTL_ManageExtensions", lazy = true)
@ActionReference(path = "Databases/Explorer/Connection/Actions", position = 450)
@Messages("CTL_ManageExtensions=Manage DuckDB Extensions...")
public final class ManageExtensionsAction implements ActionListener {

 private final DatabaseConnection dc;

 public ManageExtensionsAction(DatabaseConnection dc) {
 this.dc = dc;
    }

    @Override
 public void actionPerformed(ActionEvent e) {
 if (!DuckDB.isDuckDB(dc)) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "This action only applies to DuckDB connections.", NotifyDescriptor.INFORMATION_MESSAGE));
 return;
        }
        Connection conn = dc.getJDBCConnection();
 if (conn == null) {
 try {
                ConnectionManager.getDefault().connect(dc); // silent; DuckDB needs no credentials
            } catch (org.netbeans.api.db.explorer.DatabaseException ex) {
                DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE));
 return;
            }
 conn = dc.getJDBCConnection();
 if (conn == null) return;
        }
        ExtensionsPanel panel = new ExtensionsPanel(conn, () -> FunctionCatalog.getDefault().invalidate(dc));
        DialogDescriptor dd = new DialogDescriptor(panel,
                NbBundle.getMessage(ManageExtensionsAction.class, "CTL_ManageExtensions"),
 false, new Object[] { DialogDescriptor.CLOSED_OPTION }, DialogDescriptor.CLOSED_OPTION,
                DialogDescriptor.DEFAULT_ALIGN, null, null);
        DialogDisplayer.getDefault().createDialog(dd).setVisible(true);
    }
}
