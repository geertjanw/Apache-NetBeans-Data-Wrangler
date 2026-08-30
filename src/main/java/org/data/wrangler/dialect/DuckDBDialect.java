package org.data.wrangler.dialect;

import java.util.Set;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.data.wrangler.DuckDB;
import org.data.wrangler.completion.DuckDBKeywords;
import org.data.wrangler.dialect.spi.SqlDialect;
import org.openide.util.lookup.ServiceProvider;

/** DuckDB implementation of the proposed {@link SqlDialect} SPI. */
@ServiceProvider(service = SqlDialect.class)
public final class DuckDBDialect implements SqlDialect {

    @Override public boolean appliesTo(DatabaseConnection connection) { return DuckDB.isDuckDB(connection); }
    @Override public Set<String> extraKeywords() { return DuckDBKeywords.KEYWORDS; }
    @Override public Set<String> extraTypes() { return DuckDBKeywords.TYPES; }
    @Override public boolean supportsFromFirst() { return true; }
    @Override public Set<String> extraOperators() {
 return Set.of("->", "->>", "**", "^@", "~~", "!~~", "~~*", "!~~*", "//", "::", "||", "&", "|", "<<", ">>", "@");
    }
}
