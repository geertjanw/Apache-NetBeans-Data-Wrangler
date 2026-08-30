package org.data.wrangler.driver;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * JDBC driver registered with NetBeans instead of {@code org.duckdb.DuckDBDriver}.
 * <p>
 * DuckDB's {@code Connection.createStatement()} returns a
 * {@code DuckDBPreparedStatement}, a single class that implements both
 * {@code Statement} and {@code PreparedStatement}. NetBeans' SQL execution
 * (db.dataview {@code SQLExecutionHelper}) decides how to run a statement with
 * {@code stmt instanceof PreparedStatement} and then calls the no-arg
 * {@code execute()}, which for a plain statement fails with
 * "Query to execute was not specified".
 * <p>
 * This wrapper delegates everything to the real driver but hands NetBeans a
 * {@link Connection} whose {@code createStatement(...)} results are proxied to
 * expose <em>only</em> the {@link Statement} interface. Prepared and callable
 * statements are returned unchanged.
 * <p>
 * This class is loaded by NetBeans' driver class loader (the module jar is
 * registered as a driver URL alongside duckdb_jdbc.jar), so it must only use
 * JDK classes.
 */
public final class NbDuckDBDriver implements Driver {

 public static final String REAL_DRIVER = "org.duckdb.DuckDBDriver";
 private static final Logger LOG = Logger.getLogger(NbDuckDBDriver.class.getName());
 private final Driver delegate;

 public NbDuckDBDriver() throws SQLException {
 try {
 delegate = (Driver) Class.forName(REAL_DRIVER, true, NbDuckDBDriver.class.getClassLoader())
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
 throw new SQLException("DuckDB JDBC driver not found on driver class path", ex);
        }
    }

    @Override
 public Connection connect(String url, Properties info) throws SQLException {
        Connection c = delegate.connect(url, info);
 return c == null ? null : wrapConnection(c);
    }

    @Override public boolean acceptsURL(String url) throws SQLException { return delegate.acceptsURL(url); }
    @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException { return delegate.getPropertyInfo(url, info); }
    @Override public int getMajorVersion() { return delegate.getMajorVersion(); }
    @Override public int getMinorVersion() { return delegate.getMinorVersion(); }
    @Override public boolean jdbcCompliant() { return delegate.jdbcCompliant(); }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return delegate.getParentLogger(); }

 static Connection wrapConnection(Connection real) {
 return (Connection) Proxy.newProxyInstance(NbDuckDBDriver.class.getClassLoader(),
 new Class<?>[] { Connection.class }, new ConnectionHandler(real));
    }

 private static final class ConnectionHandler implements InvocationHandler {
 private final Connection real;
        ConnectionHandler(Connection real) { this.real = real; }

        @Override
 public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
            String name = m.getName();
 try {
 switch (name) {
 case "createStatement": {
                        Statement s = (Statement) m.invoke(real, args);
 return statementOnly(s);
                    }
 case "unwrap": {
                        Class<?> iface = (Class<?>) args[0];
 if (iface.isInstance(real)) return real;
 return m.invoke(real, args);
                    }
 case "isWrapperFor": {
                        Class<?> iface = (Class<?>) args[0];
 return iface.isInstance(real) || (Boolean) m.invoke(real, args);
                    }
 case "equals": return proxy == args[0];
 case "hashCode": return System.identityHashCode(proxy);
 case "toString": return "NbDuckDB(" + real + ")";
 default: return m.invoke(real, args);
                }
            } catch (InvocationTargetException ex) {
 throw ex.getCause();
            }
        }
    }

    /** Proxy that implements only java.sql.Statement, delegating to the DuckDB statement. */
 static Statement statementOnly(Statement real) {
 return (Statement) Proxy.newProxyInstance(NbDuckDBDriver.class.getClassLoader(),
 new Class<?>[] { Statement.class }, (proxy, m, args) -> {
 try {
 switch (m.getName()) {
 case "unwrap": {
                                Class<?> iface = (Class<?>) args[0];
 return iface.isInstance(real) ? real : m.invoke(real, args);
                            }
 case "isWrapperFor": {
                                Class<?> iface = (Class<?>) args[0];
 return iface.isInstance(real) || (Boolean) m.invoke(real, args);
                            }
 case "equals": return proxy == args[0];
 case "hashCode": return System.identityHashCode(proxy);
 case "toString": return "NbDuckDBStatement(" + real + ")";
 default: return m.invoke(real, args);
                        }
                    } catch (InvocationTargetException ex) {
 throw ex.getCause();
                    }
                });
    }
}
