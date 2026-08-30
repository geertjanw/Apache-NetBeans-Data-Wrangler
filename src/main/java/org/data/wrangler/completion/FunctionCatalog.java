package org.data.wrangler.completion;

import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.db.explorer.DatabaseConnection;

/**
 * Per-connection cache of DuckDB's function catalog. Because the list is read
 * from {@code duckdb_functions()} at completion time, functions contributed by
 * extensions (spatial, json, httpfs, ...) appear as soon as they are loaded.
 * This is the piece that fixes the "extension functions are red" complaint.
 */
public final class FunctionCatalog {

 private static final Logger LOG = Logger.getLogger(FunctionCatalog.class.getName());
 private static final FunctionCatalog INSTANCE = new FunctionCatalog();

 private final Map<DatabaseConnection, List<FunctionInfo>> cache = new ConcurrentHashMap<>();

 public static FunctionCatalog getDefault() { return INSTANCE; }

 public void invalidate(DatabaseConnection dc) {
 if (dc != null) cache.remove(dc);
    }

 public List<FunctionInfo> functions(DatabaseConnection dc) {
 if (dc == null) return List.of();
        List<FunctionInfo> cached = cache.get(dc);
 if (cached != null && !cached.isEmpty()) return cached;
 cache.remove(dc);
 return cache.computeIfAbsent(dc, c -> {
            Connection jdbc = org.data.wrangler.analysis.AnalysisConnection.get(c);
 if (jdbc == null) return List.of();
 try {
 return load(jdbc);
            } catch (SQLException ex) {
                LOG.log(Level.FINE, "Cannot read duckdb_functions()", ex);
 return List.of();
            }
        });
    }

    /** Public and NetBeans-free so it can be unit-tested with a raw JDBC connection. */
 public static List<FunctionInfo> load(Connection jdbc) throws SQLException {
        String sql = "SELECT function_name, function_type, parameters, return_type, description "
                   + "FROM duckdb_functions() WHERE NOT internal OR function_type <> 'pragma' "
                   + "ORDER BY function_name, length(parameters)";
        List<FunctionInfo> out = new ArrayList<>();
 try (Statement st = jdbc.createStatement(); ResultSet rs = st.executeQuery(sql)) {
 while (rs.next()) {
 out.add(new FunctionInfo(
 rs.getString(1), rs.getString(2), toList(rs.getArray(3)),
 rs.getString(4), rs.getString(5)));
            }
        }
 return Collections.unmodifiableList(out);
    }

 private static List<String> toList(Array arr) throws SQLException {
 if (arr == null) return List.of();
        Object raw = arr.getArray();
 if (raw instanceof Object[] objs) {
 return Arrays.stream(objs).map(o -> Objects.toString(o, "?")).toList();
        }
 return List.of();
    }
}
