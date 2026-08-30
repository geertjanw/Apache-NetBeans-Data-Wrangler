package org.netbeans.modules.db.sql.editor.spi;

import java.util.Set;
import org.netbeans.api.db.explorer.DatabaseConnection;

/**
 * SPI for vendor-specific SQL dialects.
 *
 * NetBeans currently ships one generic SQL lexer/keyword table; there is no hook
 * for a per-vendor dialect the way JetBrains' DataGrip engine has. This
 * interface is what that hook would look like. Today it lives in this module and
 * is consumed only by DuckDB code; the intent is to upstream it into
 * {@code org.netbeans.modules.db.sql.editor} so that any database module can
 * register one via {@code @ServiceProvider}.
 */
public interface SqlDialect {

    /** Return true if this dialect should be used for the given connection. */
    boolean appliesTo(DatabaseConnection connection);

    /** Additional reserved words the lexer should color as keywords. */
    Set<String> extraKeywords();

    /** Additional type names. */
    Set<String> extraTypes();

    /** Identifier quote character (DuckDB and PostgreSQL use double quotes). */
    default char identifierQuote() { return '"'; }

    /** Whether {@code FROM x SELECT ...} (FROM-first syntax) is valid. */
    default boolean supportsFromFirst() { return false; }

    /** Operators the lexer should not flag, e.g. {@code ->>} for JSON, {@code **} for power. */
    Set<String> extraOperators();
}
