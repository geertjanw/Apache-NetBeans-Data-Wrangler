package org.data.wrangler.dataview;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NestedTypeRewriterTest {

    @Test
    void wrapsNestedColumnsOnly() {
        List<NestedTypeRewriter.Col> cols = List.of(
                new NestedTypeRewriter.Col("id", "INTEGER"),
                new NestedTypeRewriter.Col("tags", "VARCHAR[]"),
                new NestedTypeRewriter.Col("addr", "STRUCT(city VARCHAR)"));
        String out = NestedTypeRewriter.rewrite("SELECT * FROM t;", cols);
        assertEquals("SELECT \"id\", to_json(\"tags\") AS \"tags\", to_json(\"addr\") AS \"addr\" FROM (SELECT * FROM t) AS duckdb_nb_q", out);
        assertEquals("SELECT 1", NestedTypeRewriter.rewrite("SELECT 1", List.of(new NestedTypeRewriter.Col("1", "INTEGER"))));
    }

    @Test
    void nestedValuesArriveAsJson() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:")) {
            String q = "SELECT 1 AS id, [1,2] AS l, {'a': 'b'} AS s";
            String sql = NestedTypeRewriter.rewrite(q, NestedTypeRewriter.describe(c, q));
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                assertTrue(rs.next());
                assertEquals("[1,2]", rs.getString("l"));
                assertEquals("{\"a\":\"b\"}", rs.getString("s"));
            }
        }
    }
}
