package org.data.wrangler.files;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.data.wrangler.SqlEditorBridge;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.modules.Places;

/**
 * Opens a DuckDB query for a data file in the SQL editor.
 * <p>
 * One query file per data file: {@code <cache>/duckdb/queries/<name>-query.sql},
 * keyed by the data file's absolute path. Opening the same data file again
 * focuses the existing editor tab instead of creating another scratch file,
 * and never overwrites a query the user has edited.
 */
public final class SqlEditorOpener {

 private SqlEditorOpener() {}

 public static void open(String sql, DatabaseConnection dc, File dataFile) throws IOException {
        FileObject fo = queryFileFor(dataFile);
 if (fo.getSize() == 0) {
 try (OutputStream os = fo.getOutputStream()) {
 os.write(sql.getBytes(StandardCharsets.UTF_8));
            }
        }
        DataObject dobj = DataObject.find(fo);
 if (dc != null) SqlEditorBridge.setConnection(dobj, dc);
        OpenCookie open = dobj.getLookup().lookup(OpenCookie.class);
 if (open != null) open.open(); // focuses the tab if it is already open
    }

    /** Stable location for the query of a given data file. */
 public static FileObject queryFileFor(File dataFile) throws IOException {
        File dir = Places.getCacheSubdirectory("duckdb/queries");
        String base = dataFile.getName();
 int dot = base.lastIndexOf('.');
 if (dot > 0) base = base.substring(0, dot);
        // include a short hash of the full path so two files with the same name do not collide
        String hash = Integer.toHexString(dataFile.getAbsolutePath().hashCode());
        String name = sanitize(base) + "-query-" + hash + ".sql";
        File f = new File(dir, name);
        FileObject folder = FileUtil.toFileObject(FileUtil.normalizeFile(dir));
        FileObject fo = folder.getFileObject(name);
 return fo != null ? fo : FileUtil.createData(FileUtil.normalizeFile(f));
    }

 private static String sanitize(String s) {
        String out = s.replaceAll("[^A-Za-z0-9_-]", "_");
 return out.isEmpty() ? "query" : out.toLowerCase(Locale.ROOT);
    }
}
