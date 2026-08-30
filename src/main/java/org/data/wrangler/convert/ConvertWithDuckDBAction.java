package org.data.wrangler.convert;

import java.awt.event.ActionEvent;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.netbeans.api.db.explorer.ConnectionManager;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.data.wrangler.analysis.AnalysisConnection;
import org.data.wrangler.files.TemplateConnectionBinder;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileChooserBuilder;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.util.actions.Presenter;

/**
 * "Convert with DuckDB" submenu: CSV, TSV, Parquet, JSON Lines, JSON array,
 * Excel, DuckDB database. Available on data file nodes, inside open CSV/JSON
 * editors, and (via {@link #popupFor}) from the Parquet window toolbar.
 */
@ActionID(category = "Tools", id = "org.data.wrangler.convert.ConvertWithDuckDBAction")
@ActionRegistration(displayName = "#CTL_ConvertWithDuckDB", lazy = false)
@ActionReferences({
    @ActionReference(path = "Loaders/text/csv/Actions", position = 260),
    @ActionReference(path = "Loaders/application/x-parquet/Actions", position = 110),
    @ActionReference(path = "Loaders/application/vnd.openxmlformats-officedocument.spreadsheetml.sheet/Actions", position = 110),
    @ActionReference(path = "Loaders/text/x-json/Actions", position = 260),
    @ActionReference(path = "Loaders/application/json/Actions", position = 260),
    @ActionReference(path = "Editors/text/csv/Popup", position = 110),
    @ActionReference(path = "Editors/text/x-json/Popup", position = 110),
    @ActionReference(path = "Editors/application/json/Popup", position = 110)
})
@Messages("CTL_ConvertWithDuckDB=Convert with DuckDB")
public final class ConvertWithDuckDBAction extends AbstractAction implements ContextAwareAction, Presenter.Popup {

 private static final RequestProcessor RP = new RequestProcessor("DuckDB convert", 2, true);
 private final List<DataObject> files;

 public ConvertWithDuckDBAction() {
 this(List.of());
    }

 private ConvertWithDuckDBAction(List<DataObject> files) {
 super(Bundle.CTL_ConvertWithDuckDB());
 this.files = files;
    }

    @Override
 public Action createContextAwareInstance(Lookup ctx) {
 return new ConvertWithDuckDBAction(List.copyOf(ctx.lookupAll(DataObject.class)));
    }

    @Override
 public void actionPerformed(ActionEvent e) {
        // Only used if invoked without the submenu (e.g. keyboard): default to Parquet.
 convertAll(ConversionFormat.PARQUET);
    }

    @Override
 public JMenuItem getPopupPresenter() {
        JMenu menu = new JMenu(Bundle.CTL_ConvertWithDuckDB());
 menu.setEnabled(!files.isEmpty());
 fill(menu, this::convertAll);
 return menu;
    }

    /** Popup menu with the conversion items, for toolbar buttons. */
 public static JPopupMenu popupFor(DataObject d) {
        JPopupMenu m = new JPopupMenu();
        ConvertWithDuckDBAction a = new ConvertWithDuckDBAction(List.of(d));
 fill(m, a::convertAll);
 return m;
    }

 private interface FormatHandler { void run(ConversionFormat f); }

 private static void fill(javax.swing.JComponent menu, FormatHandler handler) {
 for (ConversionFormat f : ConversionFormat.values()) {
            JMenuItem item = new JMenuItem("To " + f.label + "...");
 item.addActionListener(e -> handler.run(f));
 menu.add(item);
 if (f == ConversionFormat.JSON_ARRAY) menu.add(new javax.swing.JSeparator());
        }
    }

 private void convertAll(ConversionFormat format) {
 for (DataObject d : files) {
            File src = FileUtil.toFile(d.getPrimaryFile());
 if (src == null) continue;
            File suggested = ConversionService.suggestOutput(src, format);
            File out = new FileChooserBuilder(ConvertWithDuckDBAction.class)
                    .setTitle("Convert " + src.getName() + " to " + format.label)
                    .setFilesOnly(true)
                    .setDefaultWorkingDirectory(src.getParentFile())
                    .forceUseOfDefaultWorkingDirectory(true)
                    .setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(format.label, format.extension))
                    .setSelectionApprover(files -> true)
                    .showSaveDialog();
 if (out == null) continue;
 if (!out.getName().contains(".")) out = new File(out.getParentFile(), out.getName() + "." + format.extension);
 if (out.exists()) {
                NotifyDescriptor.Confirmation q = new NotifyDescriptor.Confirmation(
 out.getName() + " exists. Overwrite?", "Convert with DuckDB", NotifyDescriptor.YES_NO_OPTION);
 if (DialogDisplayer.getDefault().notify(q) != NotifyDescriptor.YES_OPTION) continue;
 out.delete();
            }
            File target = out;
            StatusDisplayer.getDefault().setStatusText("DuckDB: converting " + src.getName() + " to " + format.label + "...");
            RP.post(() -> convert(src, format, target));
        }
    }

 private static void convert(File src, ConversionFormat format, File out) {
 try {
            DatabaseConnection dc = TemplateConnectionBinder.duckDBConnection();
 if (dc == null) throw new SQLException("No DuckDB connection available");
 if (dc.getJDBCConnection() == null) ConnectionManager.getDefault().connect(dc);
            Connection conn = AnalysisConnection.get(dc);
 if (conn == null) throw new SQLException("DuckDB connection is not connected");
            ConversionService.Result r = new ConversionService(conn).convert(src, format, out);
            FileUtil.refreshFor(out.getParentFile());
            String msg = String.format("%s: %,d rows written to %s (%s)", format.label, r.rows(), out.getName(),
 org.data.wrangler.parquet.ParquetInspector.humanBytes(out.length()));
            SwingUtilities.invokeLater(() -> StatusDisplayer.getDefault().setStatusText("DuckDB " + msg));
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Conversion failed: " + ex.getMessage(), NotifyDescriptor.ERROR_MESSAGE)));
        }
    }
}
