package org.data.wrangler.convert;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConversionServiceTest {

    private static File sample() throws Exception {
        File f = Files.createTempFile("sample-", ".parquet").toFile();
        try (InputStream in = ConversionServiceTest.class.getResourceAsStream("/org/data/wrangler/files/templates/sample.parquet")) {
            Files.copy(in, f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return f;
    }

    @Test
    void parquetToCsvJsonAndBack() throws Exception {
        File src = sample();
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:")) {
            ConversionService svc = new ConversionService(c);

            File csv = ConversionService.suggestOutput(src, ConversionFormat.CSV);
            assertTrue(csv.getName().endsWith(".csv"));
            assertEquals(200, svc.convert(src, ConversionFormat.CSV, csv).rows());
            assertTrue(Files.readString(csv.toPath()).startsWith("order_id,customer,city"));

            File jsonl = ConversionService.suggestOutput(src, ConversionFormat.JSON_LINES);
            assertEquals(200, svc.convert(src, ConversionFormat.JSON_LINES, jsonl).rows());
            assertEquals(200, Files.readAllLines(jsonl.toPath()).stream().filter(l -> l.startsWith("{")).count());

            File arr = ConversionService.suggestOutput(src, ConversionFormat.JSON_ARRAY);
            svc.convert(src, ConversionFormat.JSON_ARRAY, arr);
            assertTrue(Files.readString(arr.toPath()).strip().startsWith("["));

            // same-format conversion must not overwrite the source
            File pq = ConversionService.suggestOutput(src, ConversionFormat.PARQUET);
            assertTrue(pq.getName().endsWith("_converted.parquet"));
            svc.convert(src, ConversionFormat.PARQUET, pq);

            File db = ConversionService.suggestOutput(src, ConversionFormat.DUCKDB);
            svc.convert(src, ConversionFormat.DUCKDB, db);
            try (Statement st = c.createStatement()) {
                st.execute("ATTACH '" + db.getAbsolutePath() + "' AS chk (READ_ONLY)");
                try (ResultSet rs = st.executeQuery("SELECT count(*) FROM chk." + ConversionService.tableName(src))) {
                    assertTrue(rs.next());
                    assertEquals(200, rs.getLong(1));
                }
            }
        }
    }

    @Test
    void jsonSampleRoundTrips() throws Exception {
        File json = Files.createTempFile("orders-", ".json").toFile();
        try (InputStream in = getClass().getResourceAsStream("/org/data/wrangler/files/templates/orders.json")) {
            Files.copy(in, json.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:")) {
            File pq = ConversionService.suggestOutput(json, ConversionFormat.PARQUET);
            assertEquals(8, new ConversionService(c).convert(json, ConversionFormat.PARQUET, pq).rows());
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT customer.city, count(*) FROM '" + pq.getAbsolutePath() + "' GROUP BY 1 ORDER BY 1")) {
                assertTrue(rs.next()); assertEquals("Amsterdam", rs.getString(1)); assertEquals(2, rs.getLong(2));
            }
        }
    }

    /** Excel stores numbers as doubles; whole-number columns must come out as integers. Needs the excel extension. */
    @Test
    void excelIntegersStayIntegers() throws Exception {
        File xlsx = Files.createTempFile("sample-", ".xlsx").toFile();
        try (InputStream in = getClass().getResourceAsStream("/org/data/wrangler/files/templates/sample.xlsx")) {
            Files.copy(in, xlsx.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:")) {
            try {
                new org.data.wrangler.extensions.ExtensionService(c).load("excel");
            } catch (java.sql.SQLException noExt) {
                try { new org.data.wrangler.extensions.ExtensionService(c).install("excel"); new org.data.wrangler.extensions.ExtensionService(c).load("excel"); }
                catch (java.sql.SQLException offline) { org.junit.jupiter.api.Assumptions.assumeTrue(false, "excel extension unavailable"); }
            }
            File jsonl = ConversionService.suggestOutput(xlsx, ConversionFormat.JSON_LINES);
            new ConversionService(c).convert(xlsx, ConversionFormat.JSON_LINES, jsonl);
            String first = Files.readAllLines(jsonl.toPath()).get(0);
            assertTrue(first.contains("\"order_id\":1001,"), first);
            assertTrue(first.contains("\"quantity\":1,"), first);
            assertTrue(first.contains("\"unit_price\":49.99"), first);
        }
    }

    @Test
    void tableNames() {
        assertEquals("my_data", ConversionService.tableName(new File("/x/My Data.csv")));
        assertEquals("t_2026", ConversionService.tableName(new File("/x/2026.parquet")));
    }
}
