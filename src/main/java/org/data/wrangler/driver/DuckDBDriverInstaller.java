package org.data.wrangler.driver;

import java.net.URL;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.db.explorer.DatabaseException;
import org.netbeans.api.db.explorer.JDBCDriver;
import org.netbeans.api.db.explorer.JDBCDriverManager;
import org.data.wrangler.DuckDB;
import org.openide.modules.OnStart;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;

/**
 * Registers the DuckDB JDBC driver in the Database Explorer on IDE start so
 * "DuckDB" shows up in Services &gt; Databases &gt; Drivers next to Derby, MySQL etc.
 */
@OnStart
@Messages("DuckDB.driver.displayName=DuckDB")
public final class DuckDBDriverInstaller implements Runnable {

 private static final Logger LOG = Logger.getLogger(DuckDBDriverInstaller.class.getName());

    @Override
 public void run() {
        JDBCDriverManager mgr = JDBCDriverManager.getDefault();
 if (mgr.getDrivers(DuckDB.DRIVER_CLASS).length > 0) {
 return; // already registered with the wrapper driver
        }
        Optional<URL> jar = DuckDBDriverLocator.locateDriverJar();
        Optional<URL> module = DuckDBDriverLocator.locateModuleJar();
 if (jar.isEmpty() || module.isEmpty()) {
            LOG.warning("DuckDB JDBC jar or module jar not found; driver not registered.");
 return;
        }
 try {
            // A registration of the raw driver class from an earlier version is replaced by the wrapper.
 for (String stale : new String[] { DuckDB.RAW_DRIVER_CLASS }) {
 for (JDBCDriver old : mgr.getDrivers(stale)) {
 if (DuckDB.DRIVER_NAME.equals(old.getName())) mgr.removeDriver(old);
                }
            }
            JDBCDriver driver = JDBCDriver.create(
                    DuckDB.DRIVER_NAME,
                    NbBundle.getMessage(DuckDBDriverInstaller.class, "DuckDB.driver.displayName"),
                    DuckDB.DRIVER_CLASS,
 new URL[] { module.get(), jar.get() });
 mgr.addDriver(driver);
            LOG.log(Level.INFO, "Registered DuckDB JDBC driver from {0}", jar.get());
        } catch (DatabaseException ex) {
            LOG.log(Level.WARNING, "Could not register DuckDB driver", ex);
        }
    }

    /** Convenience for other parts of the module. */
 public static JDBCDriver driver() {
        JDBCDriver[] drivers = JDBCDriverManager.getDefault().getDrivers(DuckDB.DRIVER_CLASS);
 return drivers.length == 0 ? null : drivers[0];
    }
}
