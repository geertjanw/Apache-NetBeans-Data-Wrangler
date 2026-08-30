package org.data.wrangler.analysis;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StatementSplitterTest {

    @Test
    void splitsOnSemicolonOutsideStringsAndComments() {
        String sql = "-- hello; world\nSELECT ';' AS s; SELECT 2 /* ; */;\n SELECT 3";
        List<StatementSplitter.Statement> st = StatementSplitter.split(sql);
        assertEquals(3, st.size());
        assertEquals("SELECT ';' AS s", st.get(0).sql());
        assertEquals("SELECT 2", st.get(1).sql()); // trailing comment is not part of the statement
        assertEquals("SELECT 3", st.get(2).sql());
        assertEquals(sql.indexOf("SELECT 3"), st.get(2).start());
    }

    @Test
    void atFindsStatementUnderCaret() {
        String sql = "SELECT 1;\nSELECT 2;";
        assertEquals("SELECT 2", StatementSplitter.at(sql, sql.length() - 2).sql());
    }
}
