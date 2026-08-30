package org.data.wrangler.dataview;

import java.awt.event.ActionEvent;
import java.sql.Connection;
import javax.swing.AbstractAction;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.api.editor.EditorRegistry;
import org.data.wrangler.DuckDB;
import org.data.wrangler.analysis.AnalysisConnection;
import org.data.wrangler.analysis.StatementSplitter;
import org.data.wrangler.completion.DuckDBCompletionProvider;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;

/**
 * Editor popup entry for SQL files: runs the selection (or the statement under
 * the caret) in the nested-type-aware result viewer.
 */
@ActionID(category = "Database", id = "org.data.wrangler.dataview.RunInDuckDBViewerAction")
@ActionRegistration(displayName = "#CTL_RunInDuckDBViewer", lazy = false)
@ActionReference(path = "Editors/text/x-sql/Popup", position = 120)
@Messages("CTL_RunInDuckDBViewer=Run in DuckDB Result Viewer")
public final class RunInDuckDBViewerAction extends AbstractAction {

 public RunInDuckDBViewerAction() {
 super(NbBundle.getMessage(RunInDuckDBViewerAction.class, "CTL_RunInDuckDBViewer"));
    }

    @Override
 public void actionPerformed(ActionEvent e) {
 try {
 run();
        } catch (RuntimeException ex) {
 org.openide.util.Exceptions.printStackTrace(ex);
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "DuckDB Result Viewer failed: " + ex, NotifyDescriptor.ERROR_MESSAGE));
        }
    }

 private void run() {
        JTextComponent tc = EditorRegistry.lastFocusedComponent();
 if (tc == null) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message("No SQL editor is focused.", NotifyDescriptor.INFORMATION_MESSAGE));
 return;
        }
        Document doc = tc.getDocument();
        DatabaseConnection dc = DuckDBCompletionProvider.connectionFor(doc);
 if (!DuckDB.isDuckDB(dc)) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Select a DuckDB connection in the SQL editor toolbar first.", NotifyDescriptor.INFORMATION_MESSAGE));
 return;
        }
        String sql = tc.getSelectedText();
 if (sql == null || sql.isBlank()) {
 try {
                StatementSplitter.Statement s = StatementSplitter.at(doc.getText(0, doc.getLength()), tc.getCaretPosition());
 sql = s == null ? null : s.sql();
            } catch (javax.swing.text.BadLocationException ex) { sql = null; }
        }
 if (sql == null || sql.isBlank()) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message("Select a statement or place the caret inside one.", NotifyDescriptor.INFORMATION_MESSAGE));
 return;
        }
        Connection conn = AnalysisConnection.get(dc);
 if (conn == null) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message("The DuckDB connection is not connected. Connect it in the Services window first.", NotifyDescriptor.INFORMATION_MESSAGE));
 return;
        }
        DuckDBResultTopComponent.run(conn, sql);
    }
}
