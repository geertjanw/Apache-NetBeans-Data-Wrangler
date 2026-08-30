package org.data.wrangler.parquet;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParquetInspectorTest {

    @Test
    void inspectsTheBundledSample() throws Exception {
        File f = Files.createTempFile("sample-", ".parquet").toFile();
        try (InputStream in = getClass().getResourceAsStream("/org/data/wrangler/files/templates/sample.parquet")) {
            Files.copy(in, f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:")) {
            ParquetInspector.Report r = new ParquetInspector(c).inspect(f, 5);
            assertEquals(200, r.file().rows());
            assertEquals(1, r.file().rowGroups());
            assertTrue(r.file().createdBy().startsWith("DuckDB"));
            assertTrue(r.file().ratio() > 1.5, "zstd should compress the sample");

            assertEquals(10, r.columns().size(), "nested columns are flattened into 10 chunks");
            ParquetInspector.ColumnInfo city = r.columns().stream().filter(x -> x.name().equals("city")).findFirst().orElseThrow();
            assertEquals("VARCHAR", city.sqlType());
            assertEquals("BYTE_ARRAY", city.physicalType());
            assertEquals("ZSTD", city.compression());
            assertEquals("Amsterdam", city.min());
            assertEquals("Utrecht", city.max());
            assertEquals(0, city.nullCount());
            ParquetInspector.ColumnInfo tags = r.columns().stream().filter(x -> x.name().startsWith("tags")).findFirst().orElseThrow();
            assertTrue(tags.sqlType().startsWith("VARCHAR[]"), tags.sqlType());

            assertEquals(1, r.rowGroups().size());
            assertEquals(200, r.rowGroups().get(0).rows());

            assertEquals(9, r.preview().columns().size());
            assertEquals(5, r.preview().rows().size());
            Object tagsCell = r.preview().rows().get(0)[7];
            assertTrue(String.valueOf(tagsCell).startsWith("["), "nested values previewed as JSON: " + tagsCell);
        }
    }

    @Test
    void humanBytes() {
        assertEquals("512 B", ParquetInspector.humanBytes(512));
        assertEquals("3.4 KB", ParquetInspector.humanBytes(3526));
        assertEquals("1.5 MB", ParquetInspector.humanBytes(1_572_864));
    }
}
