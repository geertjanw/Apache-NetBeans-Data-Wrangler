package org.data.wrangler.files;

import java.io.IOException;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.util.NbBundle.Messages;

/**
 * NetBeans has no built-in CSV file type; this gives .csv/.tsv files an icon, a
 * text editor, standard file actions, a New File template, and the
 * "Query with DuckDB" action (registered on Loaders/text/csv/Actions).
 */
@Messages("LBL_CsvFile=CSV Files")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_CsvFile", mimeType = CsvDataObject.MIME, extension = { "csv", "tsv" }, position = 12)
@DataObject.Registration(mimeType = CsvDataObject.MIME, iconBase = "org/data/wrangler/csv.png", displayName = "#LBL_CsvFile", position = 100)
public final class CsvDataObject extends MultiDataObject {

 public static final String MIME = "text/csv";

 public CsvDataObject(FileObject pf, MultiFileLoader loader) throws DataObjectExistsException, IOException {
 super(pf, loader);
 registerEditor(MIME, false);
    }

    @Override
 protected int associateLookup() {
 return 1;
    }
}
