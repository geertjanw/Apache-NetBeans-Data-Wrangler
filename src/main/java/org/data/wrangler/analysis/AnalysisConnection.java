package org.data.wrangler.analysis;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.db.explorer.DatabaseConnection;

/**
 * Background analysis must not fight the user's query for the explorer's JDBC
 * connection, and for in-memory databases a second {@code jdbc:duckdb:} would be
 * a different database. DuckDB's JDBC driver has {@code DuckDBConnection.duplicate()}
 * which opens a new connection to the same database instance; we call it via
 * reflection because the driver is loaded by NetBeans' driver class loader, not ours.
 */
public final class AnalysisConnection {

 private static final Logger LOG = Logger.getLogger(AnalysisConnection.class.getName());
 private static final Map<DatabaseConnection, Connection> DUPES = new WeakHashMap<>();

 private AnalysisConnection() {}

    /**
     * The analysis connection for {@code dc}, connecting it first if it is not open.
     * Connecting is done here, on the caller's background thread, so a file that was
     * just created or an editor that was just bound to a connection works immediately
     * instead of waiting for the user to press Connect. Never connects on the EDT.
     */
 public static synchronized Connection get(DatabaseConnection dc) {
 if (dc == null) return null;
        Connection main = dc.getJDBCConnection();
 if (main == null) {
 if (javax.swing.SwingUtilities.isEventDispatchThread()) return null; // connecting may block; callers on the EDT retry later
 try {
 boolean attempted = org.netbeans.api.db.explorer.ConnectionManager.getDefault().connect(dc);
 if (!attempted) {
                    LOG.log(Level.WARNING, "ConnectionManager refused to connect {0}: user name or URL is empty (user=''{1}'')",
 new Object[] { dc.getDisplayName(), dc.getUser() });
                }
            } catch (org.netbeans.api.db.explorer.DatabaseException ex) {
                LOG.log(Level.INFO, "Could not connect " + dc.getDisplayName(), ex);
 return null;
            }
 main = dc.getJDBCConnection();
 if (main == null) {
                // connect() may have connected the manager's instance rather than this one
 for (DatabaseConnection c : org.netbeans.api.db.explorer.ConnectionManager.getDefault().getConnections()) {
 if (c.getName().equals(dc.getName()) && c.getJDBCConnection() != null) { main = c.getJDBCConnection(); break; }
                }
            }
 if (main == null) {
                LOG.log(Level.INFO, "connect() returned but {0} has no JDBC connection", dc.getDisplayName());
 return null;
            }
        }
        Connection dupe = DUPES.get(dc);
 try {
 if (dupe != null && !dupe.isClosed()) return dupe;
        } catch (SQLException ignore) { /* re-create */ }
 try {
            Connection raw = main;
 try {
                Connection u = main.unwrap(Connection.class);
 if (u != null) raw = u;
            } catch (SQLException | RuntimeException ignore) { }
 dupe = (Connection) raw.getClass().getMethod("duplicate").invoke(raw);
 dupe.setAutoCommit(true);
        } catch (ReflectiveOperationException | SQLException | RuntimeException ex) {
            LOG.log(Level.FINE, "duplicate() unavailable, sharing explorer connection", ex);
 dupe = main;
        }
        DUPES.put(dc, dupe);
 return dupe;
    }
}
