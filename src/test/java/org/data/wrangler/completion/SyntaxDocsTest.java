package org.data.wrangler.completion;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SyntaxDocsTest {

    @Test
    void headlineKeywordsAreDocumented() {
        for (String kw : List.of("QUALIFY", "EXCLUDE", "PIVOT", "ASOF", "ATTACH", "MACRO", "HUGEINT", "STRUCT")) {
            SyntaxDocs.Doc d = SyntaxDocs.lookup(kw).orElseThrow(() -> new AssertionError(kw + " undocumented"));
            assertNotNull(d.summary());
            assertNotNull(d.syntax());
            assertNotNull(d.example());
            assertNotNull(d.url());
            assertTrue(d.url().toString().startsWith(SyntaxDocs.BASE_URL));
        }
    }

    @Test
    void standardKeywordsHaveDuckDBFlavouredDocs() {
        for (String kw : DuckDBKeywords.DOCUMENTED_STANDARD) {
            assertTrue(SyntaxDocs.has(kw), kw + " undocumented");
        }
        assertTrue(SyntaxDocs.lookup("FROM").orElseThrow().summary().contains("start with FROM"));
    }

    @Test
    void multiWordKeywordsFallBackToLastWord() {
        assertEquals("MACRO", SyntaxDocs.lookup("CREATE MACRO").orElseThrow().keyword().replace("CREATE ", ""));
        assertTrue(SyntaxDocs.has("USING SAMPLE"));
        assertTrue(SyntaxDocs.has("GROUP BY ALL"));
        assertTrue(SyntaxDocs.has("FORCE CHECKPOINT"));
    }

    @Test
    void htmlIsEscaped() {
        String html = SyntaxDocs.lookup("EXCLUDE").orElseThrow().html();
        assertTrue(html.contains("&lt;") || !html.contains("<expr>"));
        assertTrue(html.contains("<pre>"));
    }

    @Test
    void mostKeywordsHaveDocs() {
        long documented = DuckDBKeywords.KEYWORDS.stream().filter(SyntaxDocs::has).count();
        assertTrue(documented >= DuckDBKeywords.KEYWORDS.size() * 0.7,
                "only " + documented + "/" + DuckDBKeywords.KEYWORDS.size() + " keywords documented");
    }
}
