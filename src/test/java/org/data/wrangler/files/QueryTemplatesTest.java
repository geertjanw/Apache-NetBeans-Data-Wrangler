package org.data.wrangler.files;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QueryTemplatesTest {

    @Test
    void picksReaderByExtension() {
        assertTrue(QueryTemplates.forFile("/d/a.parquet").contains("read_parquet('/d/a.parquet')"));
        assertTrue(QueryTemplates.forFile("C:\\d\\a.csv").contains("read_csv('C:/d/a.csv'"));
        assertTrue(QueryTemplates.forFile("/d/a.jsonl").contains("read_json('/d/a.jsonl', format = 'newline_delimited')"));
        assertTrue(QueryTemplates.forFile("/d/a.ndjson").contains("format = 'newline_delimited'"));
        assertTrue(QueryTemplates.forFile("/d/a.json").contains("read_json_auto('/d/a.json')"));
    }

    @Test
    void bundledSampleParquetIsAValidFile() throws Exception {
        // The template resource must be a real Parquet file: magic bytes "PAR1" at both ends,
        // and DuckDB must be able to read the expected 200 rows from it.
        java.nio.file.Path p = java.nio.file.Files.createTempFile("sample-", ".parquet");
        try (java.io.InputStream in = getClass().getResourceAsStream("/org/data/wrangler/files/templates/sample.parquet")) {
            assertNotNull(in, "sample.parquet missing from resources");
            java.nio.file.Files.copy(in, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        byte[] b = java.nio.file.Files.readAllBytes(p);
        assertEquals("PAR1", new String(b, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals("PAR1", new String(b, b.length - 4, 4, java.nio.charset.StandardCharsets.US_ASCII));
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:duckdb:");
             java.sql.Statement st = c.createStatement();
             java.sql.ResultSet rs = st.executeQuery("SELECT count(*), sum(quantity * unit_price) FROM '" + p.toString().replace("'", "''") + "'")) {
            assertTrue(rs.next());
            assertEquals(200, rs.getLong(1));
            assertEquals(new java.math.BigDecimal("61311.71"), rs.getBigDecimal(2));
        }
    }

    @Test
    void tipsMatchFileType() {
        assertTrue(QueryTemplates.forFile("/d/a.xlsx").contains("sheet = 'Sheet2'"));
        assertTrue(QueryTemplates.forFile("/d/a.xlsx").contains("read_xlsx('/d/a.xlsx')"));
        assertTrue(QueryTemplates.forFile("/d/a.parquet").contains("parquet_metadata('/d/a.parquet')"));
        assertTrue(QueryTemplates.forFile("/d/a.parquet").contains("read_parquet('/d/*.parquet', filename = true)"));
        assertTrue(QueryTemplates.forFile("/d/a.json").contains("unnest(items, recursive := true)"));
        assertTrue(QueryTemplates.forFile("/d/a.csv").contains("sniff_csv('/d/a.csv')"));
        assertFalse(QueryTemplates.forFile("/d/a.csv").contains("read_xlsx"));
    }

    @Test
    void escapesSingleQuotes() {
        assertTrue(QueryTemplates.forFile("/d/o'brien.csv").contains("o''brien"));
    }
}
