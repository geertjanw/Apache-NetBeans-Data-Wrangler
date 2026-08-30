package org.data.wrangler.extensions;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtensionServiceTest {

    @Test
    void listsExtensions() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:")) {
            List<DuckDBExtension> ext = new ExtensionService(c).list();
            assertFalse(ext.isEmpty());
            assertTrue(ext.stream().anyMatch(e -> e.name().equals("json")));
        }
    }

    @Test
    void rejectsUnsafeNames() {
        assertThrows(IllegalArgumentException.class, () -> ExtensionService.checked("json; DROP TABLE x"));
        assertEquals("spatial", ExtensionService.checked("spatial"));
    }
}
