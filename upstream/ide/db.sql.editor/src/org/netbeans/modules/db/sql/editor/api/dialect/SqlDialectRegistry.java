package org.netbeans.modules.db.sql.editor.api.dialect;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.modules.db.sql.editor.spi.SqlDialect;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;

/**
 * Looks up registered {@link SqlDialect}s. Two entry points:
 * <ul>
 *   <li>{@link #forConnection} — used by completion, which knows the connection;</li>
 *   <li>{@link #unionKeywords()} / {@link #unionTypes()} — used by the lexer,
 *       which colours tokens before any connection is chosen, so it accepts the
 *       union of every installed dialect's vocabulary.</li>
 * </ul>
 */
public final class SqlDialectRegistry {

    private static final Lookup.Result<SqlDialect> RESULT = Lookup.getDefault().lookupResult(SqlDialect.class);
    private static volatile Set<String> keywords, types;

    static {
        RESULT.addLookupListener(new LookupListener() {
            @Override public void resultChanged(LookupEvent ev) { keywords = null; types = null; }
        });
    }

    private SqlDialectRegistry() {}

    public static SqlDialect forConnection(DatabaseConnection dc) {
        for (SqlDialect d : RESULT.allInstances()) if (d.appliesTo(dc)) return d;
        return null;
    }

    public static Set<String> unionKeywords() {
        Set<String> k = keywords;
        if (k == null) keywords = k = collect(true);
        return k;
    }

    public static Set<String> unionTypes() {
        Set<String> t = types;
        if (t == null) types = t = collect(false);
        return t;
    }

    private static Set<String> collect(boolean kw) {
        Set<String> out = new HashSet<>();
        Collection<? extends SqlDialect> all = RESULT.allInstances();
        for (SqlDialect d : all) for (String s : kw ? d.extraKeywords() : d.extraTypes()) out.add(s.toUpperCase(Locale.ROOT));
        return Set.copyOf(out);
    }
}
