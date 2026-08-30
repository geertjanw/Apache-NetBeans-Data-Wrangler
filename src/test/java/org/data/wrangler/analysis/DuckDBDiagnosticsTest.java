package org.data.wrangler.analysis;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real-grammar checks against an in-memory DuckDB. */
class DuckDBDiagnosticsTest {

    @Test
    void duckdbSyntaxIsAcceptedAndBadSyntaxIsFlagged() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:")) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE t(a INT, b INT, c VARCHAR)");
            }
            String good = "SELECT * EXCLUDE (c) FROM t QUALIFY row_number() OVER (ORDER BY a) = 1;\n"
                        + "FROM t SELECT a, b;\n"
                        + "PIVOT t ON c USING sum(a);";
            assertTrue(DuckDBDiagnostics.analyze(c, good).isEmpty(), "DuckDB-only syntax must not be flagged");

            List<DuckDBDiagnostics.Result> bad = DuckDBDiagnostics.analyze(c, "SELECT * FROMM t; SELECT zz FROM t;");
            assertEquals(2, bad.size());
            assertEquals(DuckDBErrorParser.Severity.ERROR, bad.get(0).diagnostic().severity(), bad.get(0).diagnostic().message());
            assertEquals(DuckDBErrorParser.Severity.WARNING, bad.get(1).diagnostic().severity(), bad.get(1).diagnostic().message());
            assertTrue(bad.get(1).start() > bad.get(0).end());
            String text = "SELECT * FROMM t; SELECT zz FROM t;";
            // DuckDB reports the syntax error "at or near" the token it could not parse; for
            // "SELECT * FROMM t" that is "t" (FROMM lexes as a possible alias). Assert only that
            // the underline is inside the statement past the SELECT keyword and covers one token.
            int stmtEnd = text.indexOf(';');
            assertTrue(bad.get(0).start() >= text.indexOf("FROMM") && bad.get(0).end() <= stmtEnd,
                    "underline " + bad.get(0).start() + ".." + bad.get(0).end() + " must be within the first statement");
            assertTrue(bad.get(0).end() > bad.get(0).start());
            assertEquals(text.indexOf("zz"), bad.get(1).start(), "unresolved column underline must start at zz");
        }
    }

    @Test
    void extensionFunctionsAreNotFlaggedOnceLoaded() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:")) {
            try (Statement st = c.createStatement()) { st.execute("LOAD json"); }
            assertTrue(DuckDBDiagnostics.analyze(c, "SELECT json_extract('{\"a\":1}', '$.a')").isEmpty());
        }
    }

    @Test
    void objectsCreatedEarlierInTheScriptAreNotFlagged() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:")) {
            String script = "CREATE TABLE t(a INT, b VARCHAR);\n"
                          + "INSERT INTO t VALUES (1, 'one');\n"
                          + "CREATE OR REPLACE VIEW v AS SELECT * FROM t;\n"
                          + "SELECT * FROM v;\n"
                          + "SELECT * FROM never_created;";
            List<DuckDBDiagnostics.Result> r = DuckDBDiagnostics.analyze(c, script);
            assertEquals(1, r.size(), r.toString());
            assertEquals("never_created", r.get(0).diagnostic().referencedName());
            // no LINE/caret from DuckDB for this shape -> we still underline just the name
            String stmt = r.get(0).statement().sql();
            assertEquals(stmt.indexOf("never_created"), r.get(0).diagnostic().start());
            assertEquals(stmt.indexOf("never_created") + "never_created".length(), r.get(0).diagnostic().end());
        }
    }

    @Test
    void createdNamesAreExtracted() {
        assertEquals(java.util.Set.of("t"), DuckDBDiagnostics.createdNames("CREATE OR REPLACE TEMP TABLE IF NOT EXISTS main.t(a INT)"));
        assertEquals(java.util.Set.of("m"), DuckDBDiagnostics.createdNames("CREATE MACRO m(x) AS x + 1"));
        assertEquals(java.util.Set.of("old"), DuckDBDiagnostics.createdNames("ATTACH 'old.duckdb' AS old (READ_ONLY)"));
        assertTrue(DuckDBDiagnostics.createdNames("SELECT 1").isEmpty());
    }

    @Test
    void skipsSideEffectStatements() {
        assertTrue(DuckDBDiagnostics.shouldSkip("INSTALL spatial"));
        assertTrue(DuckDBDiagnostics.shouldSkip("-- c\n SET threads = 4"));
        assertFalse(DuckDBDiagnostics.shouldSkip("SELECT 1"));
    }
}
