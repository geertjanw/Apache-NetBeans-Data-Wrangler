package org.data.wrangler.excel;

import java.io.IOException;
import org.data.wrangler.files.DataFileDataObject;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiFileLoader;
import org.openide.util.NbBundle.Messages;

/** .xlsx / .xlsm workbooks: spreadsheet Visual tab + Query tab, both through DuckDB's excel extension. */
@Messages("LBL_XlsxFile=Excel Workbooks")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_XlsxFile", mimeType = XlsxDataObject.MIME, extension = { "xlsx", "xlsm" }, position = 10)
@org.openide.loaders.DataObject.Registration(mimeType = XlsxDataObject.MIME, iconBase = XlsxDataObject.ICON, displayName = "#LBL_XlsxFile", position = 100)
public final class XlsxDataObject extends DataFileDataObject {

 public static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
 public static final String ICON = "org/data/wrangler/xlsx.png";

 public XlsxDataObject(FileObject pf, MultiFileLoader loader) throws DataObjectExistsException, IOException {
 super(pf, loader);
    }

    @Override protected String mime() { return MIME; }
    @Override protected String iconBase() { return ICON; }
    @Override protected String queryElementId() { return XlsxQueryElement.PREFERRED_ID; }
}
