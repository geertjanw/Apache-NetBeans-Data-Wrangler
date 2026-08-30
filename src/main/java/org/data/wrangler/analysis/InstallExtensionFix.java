package org.data.wrangler.analysis;

import java.sql.Connection;
import java.sql.SQLException;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.data.wrangler.completion.FunctionCatalog;
import org.data.wrangler.extensions.ExtensionService;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.Fix;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.RequestProcessor;

/** Alt-Enter fix: INSTALL + LOAD the extension DuckDB said is missing. */
public final class InstallExtensionFix implements Fix {

 private static final RequestProcessor RP = new RequestProcessor("DuckDB extension install", 1);
 private final DatabaseConnection dc;
 private final String extension;
 private final Document doc;

 public InstallExtensionFix(DatabaseConnection dc, String extension, Document doc) {
 this.dc = dc;
 this.extension = ExtensionService.checked(extension);
 this.doc = doc;
    }

    @Override
 public String getText() {
 return "Install and load DuckDB extension '" + extension + "'";
    }

    @Override
 public ChangeInfo implement() {
        NotifyDescriptor.Confirmation q = new NotifyDescriptor.Confirmation(
                "Install and load the DuckDB extension '" + extension + "'?\n\n"
              + "This downloads the extension for DuckDB " + duckdbVersion() + " into your local extension directory\n"
              + "and loads it into the current database.",
                "Install DuckDB Extension", NotifyDescriptor.YES_NO_OPTION);
 if (DialogDisplayer.getDefault().notify(q) != NotifyDescriptor.YES_OPTION) return null;
        RP.post(() -> {
            Connection conn = AnalysisConnection.get(dc);
 if (conn == null) return;
 try {
                ExtensionService svc = new ExtensionService(conn);
 svc.install(extension);
 svc.load(extension);
                FunctionCatalog.getDefault().invalidate(dc);
                DuckDBEditorSupport.reanalyze(doc);
            } catch (SQLException | RuntimeException ex) {
                SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                        "Could not install '" + extension + "': " + ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE)));
            }
        });
 return null;
    }

 private String duckdbVersion() {
        Connection conn = AnalysisConnection.get(dc);
 if (conn == null) return "";
 try { return conn.getMetaData().getDatabaseProductVersion(); } catch (SQLException e) { return ""; }
    }
}
