package org.data.wrangler.analysis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DuckDBErrorParserTest {

    @Test
    void parserErrorMapsToOffset() {
        String stmt = "SELECT *\nFROMM t";
        String msg = "Parser Error: syntax error at or near \"FROMM\"\nLINE 2: FROMM t\n        ^";
        DuckDBErrorParser.Diagnostic d = DuckDBErrorParser.parse(msg, stmt);
        assertEquals(DuckDBErrorParser.Severity.ERROR, d.severity());
        assertEquals(stmt.indexOf("FROMM"), d.start());
        assertEquals(stmt.indexOf("FROMM") + 5, d.end());
        assertTrue(d.message().startsWith("Syntax error: syntax error at or near \"FROMM\""), d.message());
        assertFalse(d.message().contains("LINE"));
    }

    @Test
    void prefixedAndUnknownShapesStillClassify() {
        DuckDBErrorParser.Diagnostic d = DuckDBErrorParser.parse("java.sql.SQLException: Binder Error: Referenced column \"zz\" not found in FROM clause!", "SELECT zz FROM t");
        assertEquals(DuckDBErrorParser.Severity.WARNING, d.severity());
        d = DuckDBErrorParser.parse("Referenced column \"zz\" not found in FROM clause!\nCandidate bindings: \"a\"", "SELECT zz FROM t");
        assertEquals(DuckDBErrorParser.Severity.WARNING, d.severity());
        d = DuckDBErrorParser.parse("syntax error at or near \"FROMM\"", "SELECT * FROMM t");
        assertEquals(DuckDBErrorParser.Severity.ERROR, d.severity());
    }

    @Test
    void duckdb15PendingQueryEnvelopeIsUnwrapped() {
        String stmt = "SELECT zz FROM t";
        String msg = "Invalid Input error: Attempting to execute an unsuccessful or closed pending query result\n"
                   + "Error: Binder Error: Referenced column \"zz\" not found in FROM clause!\n"
                   + "Candidate bindings: \"a\"\nLINE 1: SELECT zz FROM t\n               ^";
        DuckDBErrorParser.Diagnostic d = DuckDBErrorParser.parse(msg, stmt);
        assertEquals(DuckDBErrorParser.Severity.WARNING, d.severity());
        assertEquals("Unresolved: Referenced column \"zz\" not found in FROM clause! Candidate bindings: \"a\"", d.message());
        assertEquals(stmt.indexOf("zz"), d.start());
    }

    @Test
    void referencedNameIsExtracted() {
        assertEquals("t", DuckDBErrorParser.referencedName("Table with name t does not exist!"));
        assertEquals("zz", DuckDBErrorParser.referencedName("Referenced column \"zz\" not found in FROM clause!"));
        assertEquals("foo", DuckDBErrorParser.referencedName("Scalar Function with name foo does not exist!"));
        assertNull(DuckDBErrorParser.referencedName("syntax error at or near \"x\""));
    }

    @Test
    void fixHintsAreExtracted() {
        String m = "Scalar Function with name \"st_point\" is not in the catalog, but it exists in the spatial extension. "
                 + "Please try installing and loading the spatial extension: INSTALL spatial; LOAD spatial;";
        assertEquals("spatial", DuckDBErrorParser.suggestedExtension(m));
        assertEquals("sales", DuckDBErrorParser.didYouMean("Table with name sale does not exist! Did you mean \"sales\"?"));
        assertNull(DuckDBErrorParser.suggestedExtension("syntax error"));
        assertNull(DuckDBErrorParser.didYouMean("syntax error"));
    }

    @Test
    void catalogErrorIsWarning() {
        String stmt = "SELECT * FROM nope";
        String msg = "Catalog Error: Table with name nope does not exist!\nDid you mean \"nope2\"?\nLINE 1: SELECT * FROM nope\n                      ^";
        DuckDBErrorParser.Diagnostic d = DuckDBErrorParser.parse(msg, stmt);
        assertEquals(DuckDBErrorParser.Severity.WARNING, d.severity());
        assertEquals(stmt.indexOf("nope"), d.start());
        assertTrue(d.message().startsWith("Unknown object: Table with name nope does not exist!"), d.message());
        assertTrue(d.message().contains("Did you mean \"nope2\"?"), d.message());
        assertFalse(d.message().contains("LINE"), d.message());
    }
}
