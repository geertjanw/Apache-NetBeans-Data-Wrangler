package org.data.wrangler.parquet;

import org.netbeans.core.spi.multiview.MultiViewElement;
import org.data.wrangler.files.DataFileQueryElement;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/** Query tab for Parquet files. */
@MultiViewElement.Registration(
 displayName = "#LBL_ParquetQuery",
 iconBase = "org/data/wrangler/duckdb.png",
 mimeType = "application/x-parquet",
 persistenceType = TopComponent.PERSISTENCE_NEVER,
 preferredID = ParquetQueryElement.PREFERRED_ID,
 position = 2000)
@Messages("LBL_ParquetQuery=Query")
public final class ParquetQueryElement extends DataFileQueryElement {

 public static final String PREFERRED_ID = "ParquetQuery";

 public ParquetQueryElement(Lookup lkp) {
 super(lkp);
    }
}
