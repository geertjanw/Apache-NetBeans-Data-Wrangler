package org.data.wrangler;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.loaders.DataObject;
import org.openide.util.Lookup;

/**
 * Access to the SQL editor's selected connection. The class that holds it,
 * {@code org.netbeans.modules.db.api.sql.execute.SQLExecuteCookie}, lives in
 * db.core, which is a friend API and not published to Maven Central. We therefore
 * find the cookie instance in the DataObject's Lookup by class name and call it
 * reflectively; no compile-time or module dependency on db.core is required.
 */
public final class SqlEditorBridge {

 private static final Logger LOG = Logger.getLogger(SqlEditorBridge.class.getName());
 private static final String COOKIE = "org.netbeans.modules.db.api.sql.execute.SQLExecuteCookie";

 private SqlEditorBridge() {}

 public static DatabaseConnection connectionFor(Document doc) {
        DataObject dobj = NbEditorUtilities.getDataObject(doc);
 return dobj == null ? null : connectionFor(dobj);
    }

 public static DatabaseConnection connectionFor(DataObject dobj) {
        Object cookie = findCookie(dobj.getLookup());
 if (cookie == null) return null;
 try {
            Method m = cookie.getClass().getMethod("getDatabaseConnection");
 m.setAccessible(true);
            Object dc = m.invoke(cookie);
 return dc instanceof DatabaseConnection c ? c : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOG.log(Level.FINE, "SQLExecuteCookie.getDatabaseConnection failed", ex);
 return null;
        }
    }

 public static boolean setConnection(DataObject dobj, DatabaseConnection dc) {
        Object cookie = findCookie(dobj.getLookup());
 if (cookie == null) return false;
 try {
            Method m = cookie.getClass().getMethod("setDatabaseConnection", DatabaseConnection.class);
 m.setAccessible(true);
 m.invoke(cookie, dc);
 return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOG.log(Level.FINE, "SQLExecuteCookie.setDatabaseConnection failed", ex);
 return false;
        }
    }

 private static Object findCookie(Lookup lookup) {
 for (Object o : lookup.lookupAll(Object.class)) {
 if (implementsByName(o.getClass())) return o;
        }
 return null;
    }

 private static boolean implementsByName(Class<?> c) {
 for (Class<?> k = c; k != null; k = k.getSuperclass()) {
 if (k.getName().equals(COOKIE)) return true;
 for (Class<?> i : k.getInterfaces()) if (i.getName().equals(COOKIE) || implementsByName(i)) return true;
        }
 return false;
    }
}
