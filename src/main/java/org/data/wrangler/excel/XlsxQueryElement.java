package org.data.wrangler.excel;

import org.netbeans.core.spi.multiview.MultiViewElement;
import org.data.wrangler.files.DataFileQueryElement;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.TopComponent;

/** Query tab for Excel workbooks. */
@MultiViewElement.Registration(
 displayName = "#LBL_XlsxQuery",
 iconBase = "org/data/wrangler/duckdb.png",
 mimeType = XlsxDataObject.MIME,
 persistenceType = TopComponent.PERSISTENCE_NEVER,
 preferredID = XlsxQueryElement.PREFERRED_ID,
 position = 2000)
@Messages("LBL_XlsxQuery=Query")
public final class XlsxQueryElement extends DataFileQueryElement {

 public static final String PREFERRED_ID = "XlsxQuery";

 public XlsxQueryElement(Lookup lkp) {
 super(lkp);
    }
}
