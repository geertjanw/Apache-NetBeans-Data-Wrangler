package org.data.wrangler.convert;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import org.data.wrangler.extensions.ExtensionService;
import org.data.wrangler.files.QueryTemplates;

/**
 * Converts a data file to another format with DuckDB's COPY ... TO.
 * NetBeans-free; tested against the bundled sample.parquet.
 */
public final class ConversionService {

 public record Result(long rows, File output) {}

 private final Connection conn;

 public ConversionService(Connection conn) {
 this.conn = conn;
    }

    /** Suggested output next to the source: data.csv -> data.parquet; data.parquet -> data.parquet? -> data_converted.parquet */
 public static File suggestOutput(File source, ConversionFormat f) {
        String base = source.getName();
 int dot = base.lastIndexOf('.');
 if (dot > 0) base = base.substring(0, dot);
        File out = new File(source.getParentFile(), base + "." + f.extension);
 if (out.getAbsolutePath().equalsIgnoreCase(source.getAbsolutePath())) {
 out = new File(source.getParentFile(), base + "_converted." + f.extension);
        }
 return out;
    }

 public Result convert(File source, ConversionFormat format, File output) throws SQLException {
        String reader = QueryTemplates.readerFor(source.getAbsolutePath());
        String out = output.getAbsolutePath().replace("'", "''");
 synchronized (conn) {
 if (format.requiredExtension != null) {
                ExtensionService ext = new ExtensionService(conn);
 ext.install(format.requiredExtension);
 ext.load(format.requiredExtension);
            }
 boolean excel = SourceQuery.isExcel(source.getName());
 if (excel) {
                ExtensionService ext = new ExtensionService(conn);
 try { ext.load("excel"); } catch (SQLException e) { ext.install("excel"); ext.load("excel"); }
            }
            String select = SourceQuery.select(conn, reader, excel);
 long rows;
 try (Statement st = conn.createStatement()) {
 if (format == ConversionFormat.DUCKDB) {
                    String table = tableName(source);
 st.execute("ATTACH '" + out + "' AS nb_convert_target");
 try {
 st.execute("CREATE OR REPLACE TABLE nb_convert_target." + table + " AS " + select);
 rows = count(st, "nb_convert_target." + table);
                    } finally {
 st.execute("DETACH nb_convert_target");
                    }
                } else {
 st.execute("COPY (" + select + ") TO '" + out + "' " + format.copyOptions);
 rows = count(st, reader);
                }
            }
 return new Result(rows, output);
        }
    }

 private static long count(Statement st, String from) throws SQLException {
 try (ResultSet rs = st.executeQuery("SELECT count(*) FROM " + from)) {
 return rs.next() ? rs.getLong(1) : -1;
        }
    }

    /** A safe SQL identifier derived from the file name: "my data.csv" -> my_data. */
 public static String tableName(File source) {
        String base = source.getName();
 int dot = base.lastIndexOf('.');
 if (dot > 0) base = base.substring(0, dot);
        String id = base.replaceAll("[^A-Za-z0-9_]", "_").toLowerCase(Locale.ROOT);
 if (id.isEmpty() || Character.isDigit(id.charAt(0))) id = "t_" + id;
 return id;
    }
}
