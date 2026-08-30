package org.data.wrangler.diff;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiffServiceTest {

    @Test
    void schemaAndRowDifferences() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:")) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE a(id INT, city VARCHAR, total DECIMAL(10,2), tags VARCHAR[])");
                st.execute("CREATE TABLE b(id INT, city VARCHAR, total DOUBLE, extra INT)");
                st.execute("INSERT INTO a VALUES (1,'Utrecht',10,['x']), (2,'Delft',20,[]), (3,'Delft',5,[])");
                st.execute("INSERT INTO b VALUES (1,'Utrecht',10,0), (2,'Delft',25,0)");
            }
            DiffService.Result r = new DiffService(c).compare("a", "a", "b", "b");
            assertFalse(r.identical());
            assertEquals(3, r.countA()); assertEquals(2, r.countB());
            assertEquals(java.util.List.of("id", "city", "total"), r.commonColumns());
            assertEquals("type differs", r.schema().stream().filter(x -> x.column().equals("total")).findFirst().orElseThrow().status());
            assertEquals("only in A", r.schema().stream().filter(x -> x.column().equals("tags")).findFirst().orElseThrow().status());
            assertEquals("only in B", r.schema().stream().filter(x -> x.column().equals("extra")).findFirst().orElseThrow().status());
            assertEquals(2, r.onlyInA().total());   // (2,'Delft',20) and (3,'Delft',5); (1,'Utrecht',10.00) matches 10.0 in B
            assertEquals(1, r.onlyInB().total());   // (2,'Delft',25)
            assertEquals(3, r.onlyInA().columns().size());
            assertTrue(r.textA().startsWith("id\tcity\ttotal\n1\tUtrecht\t10.0\n"), r.textA());
            assertEquals(3, r.textA().strip().split("\n").length - 1, "one line per row");
        }
    }

    @Test
    void numericTypesAreRecognised() {
        assertTrue(DiffService.isNumeric("DECIMAL(10,2)"));
        assertTrue(DiffService.isNumeric("BIGINT"));
        assertTrue(DiffService.isNumeric("DOUBLE"));
        assertFalse(DiffService.isNumeric("VARCHAR"));
        assertFalse(DiffService.isNumeric("TIMESTAMP"));
    }

    @Test
    void identicalSources() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:")) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE a AS SELECT range AS id, 'x' AS s FROM range(10)");
            }
            DiffService.Result r = new DiffService(c).compare("a", "a", "a", "a copy");
            assertTrue(r.identical());
            assertEquals(0, r.onlyInA().total());
        }
    }
}
