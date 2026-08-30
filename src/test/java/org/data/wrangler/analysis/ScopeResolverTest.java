package org.data.wrangler.analysis;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScopeResolverTest {

    @Test
    void resolvesAliasesTableFunctionsSubqueriesAndCtes() {
        String sql = "WITH c AS (SELECT 1 AS x) SELECT a.id, b.v FROM orders AS a "
                   + "JOIN read_csv('f.csv') b ON a.id = b.id, (SELECT 2 AS y) s LEFT JOIN c ON true WHERE a.id > 1";
        ScopeResolver.Scope sc = ScopeResolver.resolve(sql);
        assertEquals("orders", sc.sourceFor("a"));
        assertEquals("read_csv('f.csv')", sc.sourceFor("b"));
        assertEquals("(SELECT 2 AS y)", sc.sourceFor("s"));
        assertEquals("c", sc.sourceFor("c"));
        assertTrue(sc.withPrefix().startsWith("WITH c AS (SELECT 1 AS x)"));
    }

    @Test
    void commasOutsideFromAreIgnored() {
        ScopeResolver.Scope sc = ScopeResolver.resolve("SELECT a, b, c FROM t WHERE x IN (1, 2) ORDER BY a, b");
        assertEquals(java.util.Set.of("t"), sc.sources().keySet());
        sc = ScopeResolver.resolve("SELECT * FROM t1, t2 x WHERE t1.id = x.id");
        assertEquals(java.util.Set.of("t1", "x"), sc.sources().keySet());
    }

    @Test
    void aliasBeforeCaret() {
        String t = "SELECT a.na FROM t a";
        assertEquals("a", ScopeResolver.aliasBeforeCaret(t, t.indexOf("na")));
        assertNull(ScopeResolver.aliasBeforeCaret(t, 7));
    }

    @Test
    void columnsComeFromDuckDB() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:")) {
            try (Statement st = c.createStatement()) { st.execute("CREATE TABLE orders(id INT, total DECIMAL(10,2))"); }
            ScopeResolver.Scope sc = ScopeResolver.resolve("WITH big AS (SELECT id FROM orders WHERE total > 10) SELECT * FROM orders o JOIN big ON true");
            List<ColumnCatalog.Column> o = ColumnCatalog.columnsOf(c, sc, "o", sc.sourceFor("o"));
            assertEquals(List.of("id", "total"), o.stream().map(ColumnCatalog.Column::name).toList());
            List<ColumnCatalog.Column> cte = ColumnCatalog.columnsOf(c, sc, "big", sc.sourceFor("big"));
            assertEquals(List.of("id"), cte.stream().map(ColumnCatalog.Column::name).toList());
        }
    }
}
