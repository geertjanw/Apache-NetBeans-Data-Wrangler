package org.data.wrangler.driver;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NbDuckDBDriverTest {

    @Test
    void createStatementIsNotAPreparedStatement() throws Exception {
        NbDuckDBDriver d = new NbDuckDBDriver();
        assertTrue(d.acceptsURL("jdbc:duckdb:"));
        try (Connection c = d.connect("jdbc:duckdb:", new java.util.Properties())) {
            try (Statement s = c.createStatement()) {
                assertFalse(s instanceof PreparedStatement, "NetBeans must take the execute(sql) branch");
                assertTrue(s.execute("SELECT 42 AS x"));
                try (ResultSet rs = s.getResultSet()) {
                    assertTrue(rs.next());
                    assertEquals(42, rs.getInt("x"));
                }
            }
            try (PreparedStatement p = c.prepareStatement("SELECT ? + 1")) {
                p.setInt(1, 1);
                try (ResultSet rs = p.executeQuery()) { assertTrue(rs.next()); assertEquals(2, rs.getInt(1)); }
            }
            // unwrap exposes the real DuckDB connection (used by AnalysisConnection.duplicate())
            assertEquals("org.duckdb.DuckDBConnection", c.unwrap(Connection.class).getClass().getName());
        }
    }
}
