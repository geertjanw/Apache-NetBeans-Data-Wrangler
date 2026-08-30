package org.data.wrangler.completion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Runs against a real in-memory DuckDB via the bundled JDBC driver. */
class FunctionCatalogTest {

    @Test
    void readsFunctionsFromCatalog() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:")) {
            List<FunctionInfo> fns = FunctionCatalog.load(c);
            assertTrue(fns.stream().anyMatch(f -> f.name().equals("read_csv")), "read_csv should be listed");
            assertTrue(fns.stream().anyMatch(f -> f.name().equals("list_transform")));
        }
    }

    @Test
    void extensionFunctionsAppearAfterLoad() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:")) {
            // json is a core extension that is normally auto-loadable; force it.
            try (Statement st = c.createStatement()) { st.execute("LOAD json"); }
            List<FunctionInfo> fns = FunctionCatalog.load(c);
            assertTrue(fns.stream().anyMatch(f -> f.name().equals("json_extract")),
                    "json_extract should be visible once json is loaded");
        }
    }

    @Test
    void wordStartFindsIdentifierBoundary() {
        assertEquals(14, DuckDBCompletionProvider.wordStart("SELECT * FROM read_pa", 21));
        assertEquals(0, DuckDBCompletionProvider.wordStart("qual", 4));
        assertEquals(7, DuckDBCompletionProvider.wordStart("SELECT ", 7));
    }
}
