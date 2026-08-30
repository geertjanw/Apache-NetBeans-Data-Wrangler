package org.data.wrangler.diff;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompareSelectionTest {

    private final File csv = new File("a.csv");
    private final File tsv = new File("b.tsv");
    private final File pq = new File("c.parquet");
    private final File xlsx = new File("d.xlsx");

    @Test
    void oneOrTwoFilesOfTheSameKindAreComparable() {
        assertNull(CompareSelection.problem(List.of(csv)));                       // the other is picked in a chooser
        assertNull(CompareSelection.problem(List.of(csv, tsv)));                  // CSV and TSV are the same kind
        assertNull(CompareSelection.problem(List.of(pq, new File("e.pq"))));
        assertNull(CompareSelection.problem(List.of(xlsx, new File("f.xlsm"))));
    }

    @Test
    void otherSelectionsAreNot() {
        assertNotNull(CompareSelection.problem(List.of()));
        String twoKinds = CompareSelection.problem(List.of(csv, pq));
        assertNotNull(twoKinds);
        assertTrue(twoKinds.contains("CSV") && twoKinds.contains("Parquet"), twoKinds);
        String three = CompareSelection.problem(List.of(csv, tsv, pq));
        assertNotNull(three);
        assertTrue(three.contains("3"), three);
    }

    @Test
    void kinds() {
        assertEquals("Parquet", CompareSelection.kind(pq));
        assertEquals("JSON", CompareSelection.kind(new File("g.ndjson")));
        assertEquals("other", CompareSelection.kind(new File("h.txt")));
        assertTrue(CompareSelection.sameKind(csv, tsv));
        assertFalse(CompareSelection.sameKind(csv, xlsx));
    }
}
