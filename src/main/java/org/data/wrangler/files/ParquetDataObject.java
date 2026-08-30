package org.data.wrangler.files;

import java.io.IOException;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.MIMEResolver;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiFileLoader;
import org.openide.util.NbBundle.Messages;

/** .parquet / .pq files: Visual tab (schema, storage, preview) + Query tab. */
@Messages("LBL_ParquetFile=Parquet Files")
@MIMEResolver.ExtensionRegistration(displayName = "#LBL_ParquetFile", mimeType = ParquetDataObject.MIME, extension = { "parquet", "pq" }, position = 11)
@org.openide.loaders.DataObject.Registration(mimeType = ParquetDataObject.MIME, iconBase = ParquetDataObject.ICON, displayName = "#LBL_ParquetFile", position = 100)
public final class ParquetDataObject extends DataFileDataObject {

 public static final String MIME = "application/x-parquet";
 public static final String ICON = "org/data/wrangler/parquet.png";

 public ParquetDataObject(FileObject pf, MultiFileLoader loader) throws DataObjectExistsException, IOException {
 super(pf, loader);
    }

    @Override protected String mime() { return MIME; }
    @Override protected String iconBase() { return ICON; }
    @Override protected String queryElementId() { return org.data.wrangler.parquet.ParquetQueryElement.PREFERRED_ID; }
}
