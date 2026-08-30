package org.data.wrangler.completion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DuckDBKeywordsTest {

    @Test
    void containsHeadlineDuckDBSyntax() {
        assertTrue(DuckDBKeywords.KEYWORDS.contains("QUALIFY"));
        assertTrue(DuckDBKeywords.KEYWORDS.contains("EXCLUDE"));
        assertTrue(DuckDBKeywords.KEYWORDS.contains("PIVOT"));
        assertTrue(DuckDBKeywords.TYPES.contains("HUGEINT"));
    }

    @Test
    void offlineFunctionsHaveSignatures() {
        FunctionInfo f = DuckDBKeywords.OFFLINE_FUNCTIONS.stream()
                .filter(x -> x.name().equals("read_parquet")).findFirst().orElseThrow();
        assertEquals("read_parquet(path)", f.signature());
    }
}
