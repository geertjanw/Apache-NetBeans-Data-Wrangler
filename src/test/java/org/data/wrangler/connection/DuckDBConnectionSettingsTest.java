package org.data.wrangler.connection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DuckDBConnectionSettingsTest {

    @Test
    void inMemoryUrlIsBarePrefix() {
        DuckDBConnectionSettings s = new DuckDBConnectionSettings();
        assertEquals("jdbc:duckdb:", s.buildUrl());
        assertEquals("DuckDB (in-memory)", s.effectiveDisplayName());
        assertNull(s.validate());
    }

    @Test
    void fileUrlAndProperties() {
        DuckDBConnectionSettings s = new DuckDBConnectionSettings();
        s.setMode(DuckDBConnectionSettings.Mode.FILE);
        s.setDatabasePath("/data/sales.duckdb");
        s.setReadOnly(true);
        s.setThreads(4);
        s.setMemoryLimit("4GB");
        assertEquals("jdbc:duckdb:/data/sales.duckdb", s.buildUrl());
        assertEquals("DuckDB - sales.duckdb", s.effectiveDisplayName());
        assertEquals("true", s.buildProperties().getProperty("duckdb.read_only"));
        assertEquals("4", s.buildProperties().getProperty("threads"));
        assertEquals("4GB", s.buildProperties().getProperty("memory_limit"));
        assertNull(s.validate());
    }

    @Test
    void validationCatchesMissingFileAndBadLimit() {
        DuckDBConnectionSettings s = new DuckDBConnectionSettings();
        s.setMode(DuckDBConnectionSettings.Mode.FILE);
        assertNotNull(s.validate());
        s.setDatabasePath("x.db");
        s.setMemoryLimit("lots");
        assertNotNull(s.validate());
    }
}
