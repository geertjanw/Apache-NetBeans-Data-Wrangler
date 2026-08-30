package org.data.wrangler.files;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.util.NbBundle.Messages;

/**
 * "Copy File Path" on data files (CSV, Parquet, JSON, JSON Lines, Excel):
 * puts the absolute path on the clipboard, one per line for a multi-selection.
 * Sits right under the standard Copy item in the context menu.
 */
@ActionID(category = "Edit", id = "org.data.wrangler.files.CopyFilePathAction")
@ActionRegistration(displayName = "#CTL_CopyFilePath", lazy = true)
@ActionReferences({
    @ActionReference(path = "Loaders/text/csv/Actions", position = 450),
    @ActionReference(path = "Loaders/application/x-parquet/Actions", position = 450),
    @ActionReference(path = "Loaders/application/vnd.openxmlformats-officedocument.spreadsheetml.sheet/Actions", position = 450),
    @ActionReference(path = "Loaders/text/x-json/Actions", position = 450),
    @ActionReference(path = "Loaders/application/json/Actions", position = 450)
})
@Messages({
    "CTL_CopyFilePath=Copy File Path",
    "# {0} - the copied path",
    "MSG_CopiedPath=Copied {0}",
    "# {0} - number of paths",
    "MSG_CopiedPaths=Copied {0} file paths"
})
public final class CopyFilePathAction implements ActionListener {

 private final List<DataObject> context;

 public CopyFilePathAction(List<DataObject> context) {
 this.context = context;
    }

    @Override
 public void actionPerformed(ActionEvent e) {
        List<String> paths = context.stream()
                .map(d -> FileUtil.toFile(d.getPrimaryFile()))
                .filter(f -> f != null)
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());
 if (paths.isEmpty()) return;
        String text = String.join(System.lineSeparator(), paths);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        StatusDisplayer.getDefault().setStatusText(paths.size() == 1
                ? Bundle.MSG_CopiedPath(paths.get(0))
                : Bundle.MSG_CopiedPaths(paths.size()));
    }
}
