package org.data.wrangler.files;

import java.io.IOException;
import javax.swing.SwingUtilities;
import org.netbeans.core.api.multiview.MultiViewHandler;
import org.netbeans.core.api.multiview.MultiViewPerspective;
import org.netbeans.core.api.multiview.MultiViews;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.loaders.MultiDataObject;
import org.openide.loaders.MultiFileLoader;
import org.openide.util.ImageUtilities;
import org.openide.windows.TopComponent;

/**
 * Base for binary data files (Parquet, Excel) that open in a MultiView window
 * with a Visual tab and a Query tab. Never uses registerEditor(): that would
 * attach a text DataEditorSupport that tries to decode the file as UTF-8.
 */
public abstract class DataFileDataObject extends MultiDataObject {

 private TopComponent view;

 protected DataFileDataObject(FileObject pf, MultiFileLoader loader) throws DataObjectExistsException, IOException {
 super(pf, loader);
 getCookieSet().add((OpenCookie) this::openView);
    }

    /** MIME type the MultiView elements are registered for. */
 protected abstract String mime();

    /** Icon resource path for the window tab. */
 protected abstract String iconBase();

    /** preferredID of the Query element for this file type. */
 protected abstract String queryElementId();

 public final synchronized void openView() {
 if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::openView);
 return;
        }
 if (view == null || !view.isOpened()) {
 view = MultiViews.createMultiView(mime(), this);
 view.setDisplayName(getPrimaryFile().getNameExt());
 view.setToolTipText(getPrimaryFile().getPath());
 view.setIcon(ImageUtilities.loadImage(iconBase()));
 view.open();
        }
 view.requestActive();
    }

    /** Opens the window and switches to the Query tab (used by "Query with DuckDB"). */
 public final void openQueryTab() {
 if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::openQueryTab);
 return;
        }
 openView();
        MultiViewHandler h = MultiViews.findMultiViewHandler(view);
 if (h == null) return;
 for (MultiViewPerspective p : h.getPerspectives()) {
 if (queryElementId().equals(p.preferredID())) {
 h.requestActive(p);
 return;
            }
        }
    }

    @Override
 protected int associateLookup() {
 return 1;
    }
}
