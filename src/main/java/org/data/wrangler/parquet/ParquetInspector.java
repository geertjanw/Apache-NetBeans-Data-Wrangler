package org.data.wrangler.parquet;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.data.wrangler.dataview.NestedTypeRewriter;

/**
 * Reads everything the Visual tab shows, using DuckDB's parquet_* table
 * functions. NetBeans-free so it can be unit-tested against sample.parquet.
 */
public final class ParquetInspector {

 public record FileInfo(String path, long sizeBytes, long rows, int rowGroups, String createdBy, long formatVersion,
 long compressedBytes, long uncompressedBytes) {
 public double ratio() { return compressedBytes == 0 ? 0 : (double) uncompressedBytes / compressedBytes; }
    }

 public record ColumnInfo(String name, String sqlType, String physicalType, String encodings, String compression,
 long compressedBytes, long uncompressedBytes, String min, String max, long nullCount) {}

 public record RowGroupInfo(int id, long rows, long compressedBytes, long uncompressedBytes) {}

 public record Preview(List<String> columns, List<Object[]> rows) {}

 public record Report(FileInfo file, List<ColumnInfo> columns, List<RowGroupInfo> rowGroups, Preview preview) {}

 private final Connection conn;

 public ParquetInspector(Connection conn) {
 this.conn = conn;
    }

 public Report inspect(File file, int previewRows) throws SQLException {
        String p = file.getAbsolutePath().replace("'", "''");
 synchronized (conn) {
 return new Report(fileInfo(file, p), columns(p), rowGroups(p), preview(p, previewRows));
        }
    }

 private FileInfo fileInfo(File file, String p) throws SQLException {
        String sql = "SELECT f.num_rows, f.num_row_groups, f.created_by, f.format_version, "
                   + "(SELECT sum(total_compressed_size) FROM parquet_metadata('" + p + "')), "
                   + "(SELECT sum(total_uncompressed_size) FROM parquet_metadata('" + p + "')) "
                   + "FROM parquet_file_metadata('" + p + "') f";
 try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
 rs.next();
 return new FileInfo(file.getAbsolutePath(), file.length(), rs.getLong(1), rs.getInt(2),
 rs.getString(3), rs.getLong(4), rs.getLong(5), rs.getLong(6));
        }
    }

 private List<ColumnInfo> columns(String p) throws SQLException {
        // logical types from DESCRIBE; physical/storage facts from parquet_metadata, summed over row groups
        Map<String, String> sqlTypes = new LinkedHashMap<>();
 try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("DESCRIBE SELECT * FROM '" + p + "'")) {
 while (rs.next()) sqlTypes.put(rs.getString("column_name"), rs.getString("column_type"));
        }
        String sql = "SELECT path_in_schema, any_value(type), any_value(encodings), any_value(compression), "
                   + "sum(total_compressed_size), sum(total_uncompressed_size), min(stats_min), max(stats_max), sum(stats_null_count) "
                   + "FROM parquet_metadata('" + p + "') GROUP BY path_in_schema, column_id ORDER BY column_id";
        List<ColumnInfo> out = new ArrayList<>();
 try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
 while (rs.next()) {
                String path = rs.getString(1);
                String top = path.contains(",") ? path.substring(0, path.indexOf(',')).trim() : path;
                String sqlType = sqlTypes.getOrDefault(top, "");
 if (!top.equals(path)) sqlType = sqlType + "  \u2192 " + path.substring(path.indexOf(',') + 1).trim();
 out.add(new ColumnInfo(path, sqlType, rs.getString(2), String.valueOf(rs.getObject(3)), rs.getString(4),
 rs.getLong(5), rs.getLong(6), rs.getString(7), rs.getString(8), rs.getLong(9)));
            }
        }
 return out;
    }

 private List<RowGroupInfo> rowGroups(String p) throws SQLException {
        String sql = "SELECT row_group_id, any_value(row_group_num_rows), sum(total_compressed_size), sum(total_uncompressed_size) "
                   + "FROM parquet_metadata('" + p + "') GROUP BY row_group_id ORDER BY row_group_id";
        List<RowGroupInfo> out = new ArrayList<>();
 try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
 while (rs.next()) out.add(new RowGroupInfo(rs.getInt(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)));
        }
 return out;
    }

 private Preview preview(String p, int limit) throws SQLException {
        String base = "SELECT * FROM '" + p + "' LIMIT " + limit;
        String sql;
 try {
 sql = NestedTypeRewriter.rewrite(base, NestedTypeRewriter.describe(conn, base));
        } catch (SQLException e) {
 sql = base;
        }
        List<String> cols = new ArrayList<>();
        List<Object[]> rows = new ArrayList<>();
 try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
 for (int i = 1; i <= md.getColumnCount(); i++) cols.add(md.getColumnLabel(i));
 while (rs.next()) {
                Object[] row = new Object[cols.size()];
 for (int i = 0; i < row.length; i++) row[i] = rs.getObject(i + 1);
 rows.add(row);
            }
        }
 return new Preview(cols, rows);
    }

 public static String humanBytes(long b) {
 if (b < 1024) return b + " B";
 if (b < 1024 * 1024) return String.format("%.1f KB", b / 1024.0);
 if (b < 1024L * 1024 * 1024) return String.format("%.1f MB", b / (1024.0 * 1024));
 return String.format("%.2f GB", b / (1024.0 * 1024 * 1024));
    }
}
