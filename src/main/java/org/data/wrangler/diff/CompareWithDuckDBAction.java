package org.data.wrangler.diff;

import java.awt.event.ActionEvent;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.data.wrangler.analysis.AnalysisConnection;
import org.data.wrangler.excel.XlsxInspector;
import org.data.wrangler.excel.XlsxSheets;
import org.data.wrangler.files.TemplateConnectionBinder;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileChooserBuilder;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.RequestProcessor;
import org.openide.util.actions.Presenter;

/**
 * "Compare with DuckDB": select two files of the same kind and compare, or
 * select one and pick the other in a file chooser. Excel workbooks are
 * compared sheet by sheet over the sheets they have in common.
 *
 * The menu item is greyed out when the selection cannot be compared (more
 * than two files, or two files of different kinds); the tooltip of the
 * disabled item says why.
 */
@ActionID(category = "Tools", id = "org.data.wrangler.diff.CompareWithDuckDBAction")
@ActionRegistration(displayName = "#CTL_CompareWithDuckDB", lazy = false)
@ActionReferences({
    @ActionReference(path = "Loaders/text/csv/Actions", position = 270),
    @ActionReference(path = "Loaders/application/x-parquet/Actions", position = 120),
    @ActionReference(path = "Loaders/application/vnd.openxmlformats-officedocument.spreadsheetml.sheet/Actions", position = 120),
    @ActionReference(path = "Loaders/text/x-json/Actions", position = 270),
    @ActionReference(path = "Loaders/application/json/Actions", position = 270)
})
@Messages("CTL_CompareWithDuckDB=Compare with DuckDB...")
public final class CompareWithDuckDBAction extends AbstractAction implements ContextAwareAction, Presenter.Popup {

    private static final RequestProcessor RP = new RequestProcessor("DuckDB compare", 1, true);
    private final List<File> files;
    private final String problem;

    public CompareWithDuckDBAction() {
        this(List.of());
    }

    private CompareWithDuckDBAction(List<DataObject> context) {
        super(Bundle.CTL_CompareWithDuckDB());
        List<File> fs = new ArrayList<>();
        for (DataObject d : context) {
            File f = FileUtil.toFile(d.getPrimaryFile());
            if (f != null) fs.add(f);
        }
        this.files = fs;
        this.problem = CompareSelection.problem(fs);
        setEnabled(problem == null);
    }

    @Override
    public Action createContextAwareInstance(Lookup ctx) {
        return new CompareWithDuckDBAction(List.copyOf(ctx.lookupAll(DataObject.class)));
    }

    @Override
    public JMenuItem getPopupPresenter() {
        JMenuItem item = new JMenuItem(this);
        if (problem != null) {
            item.setToolTipText(problem);
        }
        return item;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (problem != null) {
            return;
        }
        File a = files.get(0);
        File b;
        if (files.size() == 2) {
            b = files.get(1);
        } else {
            b = new FileChooserBuilder(CompareWithDuckDBAction.class)
                    .setTitle("Compare " + a.getName() + " with...")
                    .setFilesOnly(true)
                    .setDefaultWorkingDirectory(a.getParentFile())
                    .forceUseOfDefaultWorkingDirectory(true)
                    .setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Same kind as " + a.getName(), CompareSelection.kindExtensions(a)))
                    .showOpenDialog();
            if (b == null) return;
        }
        if (!CompareSelection.sameKind(a, b)) {
            DialogDisplayer.getDefault().notify(new NotifyDescriptor.Message(
                    "Compare files of the same kind: " + a.getName() + " and " + b.getName(), NotifyDescriptor.WARNING_MESSAGE));
            return;
        }
        File fa = a, fb = b;
        DataDiffTopComponent tc = new DataDiffTopComponent("Diff: " + a.getName() + " \u2194 " + b.getName());
        tc.open();
        tc.requestActive();
        RP.post(() -> run(fa, fb, tc));
    }

    private static void run(File a, File b, DataDiffTopComponent tc) {
        try {
            DatabaseConnection dc = TemplateConnectionBinder.duckDBConnection();
            Connection conn = dc == null ? null : AnalysisConnection.get(dc);
            if (conn == null) throw new SQLException("No DuckDB connection available");
            DiffService svc = new DiffService(conn);
            if (CompareSelection.isExcel(a)) {
                new XlsxInspector(conn).ensureExtension();
                List<String> sheetsA = XlsxSheets.sheetNames(a), sheetsB = XlsxSheets.sheetNames(b);
                List<String> common = sheetsA.stream().filter(sheetsB::contains).toList();
                if (common.isEmpty()) {
                    SwingUtilities.invokeLater(() -> tc.fail("The workbooks have no sheet names in common: " + sheetsA + " vs " + sheetsB));
                    return;
                }
                for (String sheet : common) {
                    DiffService.Result r = svc.compareSheet(a, b, sheet);
                    SwingUtilities.invokeLater(() -> tc.addResult(sheet, r));
                }
                List<String> onlyA = sheetsA.stream().filter(s -> !sheetsB.contains(s)).toList();
                List<String> onlyB = sheetsB.stream().filter(s -> !sheetsA.contains(s)).toList();
                if (!onlyA.isEmpty() || !onlyB.isEmpty()) {
                    SwingUtilities.invokeLater(() -> tc.fail("Sheets only in A: " + onlyA + "; only in B: " + onlyB));
                }
            } else {
                DiffService.Result r = svc.compareFiles(a, b);
                SwingUtilities.invokeLater(() -> tc.addResult(null, r));
            }
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> tc.fail(String.valueOf(ex.getMessage())));
        }
    }
}
