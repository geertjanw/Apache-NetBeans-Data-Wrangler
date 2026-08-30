package org.data.wrangler.analysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.data.wrangler.analysis.SqlScanner.Kind;
import org.data.wrangler.analysis.SqlScanner.Token;

/**
 * Finds what a statement selects FROM so completion can offer columns for
 * {@code alias.} prefixes, CTE names and bare column names. We only locate the
 * sources textually; their actual columns are asked from DuckDB with DESCRIBE
 * (see {@link ColumnCatalog}), which handles tables, views, read_csv(...),
 * subqueries and CTEs uniformly.
 */
public final class ScopeResolver {

    /** alias (or source name) -> source SQL fragment usable after FROM. */
 public record Scope(String withPrefix, Map<String, String> sources) {
 public String sourceFor(String alias) {
 for (Map.Entry<String, String> e : sources.entrySet()) if (e.getKey().equalsIgnoreCase(alias)) return e.getValue();
 return null;
        }
    }

 private static final Set<String> SOURCE_INTRO = Set.of("FROM", "JOIN");
    /** Clause keywords that end the FROM clause (top-level commas after these are not sources). */
 private static final Set<String> FROM_END = Set.of("WHERE", "GROUP", "ORDER", "LIMIT", "HAVING", "QUALIFY", "WINDOW",
            "UNION", "EXCEPT", "INTERSECT", "SELECT", "SET", "VALUES", "RETURNING");
 private static final Set<String> NOT_ALIAS = Set.of("WHERE", "GROUP", "ORDER", "LIMIT", "HAVING", "QUALIFY", "WINDOW",
            "UNION", "EXCEPT", "INTERSECT", "ON", "USING", "JOIN", "LEFT", "RIGHT", "FULL", "INNER", "OUTER", "CROSS",
            "NATURAL", "ASOF", "SEMI", "ANTI", "POSITIONAL", "LATERAL", "SELECT", "SAMPLE", "TABLESAMPLE", "PIVOT", "UNPIVOT");

 private ScopeResolver() {}

 public static Scope resolve(String stmt) {
        List<Token> toks = SqlScanner.scan(stmt).stream()
                .filter(t -> t.kind() != Kind.WS && t.kind() != Kind.COMMENT).toList();
        Map<String, String> sources = new LinkedHashMap<>();
        String withPrefix = "";

        // WITH a AS (...), b AS (...) SELECT ...
 if (!toks.isEmpty() && toks.get(0).is("WITH")) {
 int i = 1;
 if (i < toks.size() && toks.get(i).is("RECURSIVE")) i++;
 int end = i;
 while (i < toks.size()) {
 if (toks.get(i).kind() != Kind.WORD && toks.get(i).kind() != Kind.QUOTED_IDENT) break;
                String cte = unquote(toks.get(i).text());
 i++;
 if (i < toks.size() && toks.get(i).kind() == Kind.LPAREN) i = skipParens(toks, i) + 1; // column list
 if (i >= toks.size() || !toks.get(i).is("AS")) break;
 i++;
 if (i < toks.size() && (toks.get(i).is("MATERIALIZED") || toks.get(i).is("NOT"))) { i++; if (toks.get(i).is("MATERIALIZED")) i++; }
 if (i >= toks.size() || toks.get(i).kind() != Kind.LPAREN) break;
 int close = skipParens(toks, i);
 sources.put(cte, cte);
 i = close + 1;
 end = i;
 if (i < toks.size() && toks.get(i).kind() == Kind.COMMA) { i++; continue; }
 break;
            }
 if (end > 0) withPrefix = stmt.substring(toks.get(0).start(), toks.get(end - 1).end()) + " ";
        }

        // Walk the statement; a source follows FROM / JOIN, and also a top-level comma
        // while we are still inside the FROM clause (e.g. after "JOIN b ON a.id = b.id, ...").
 boolean inFrom = false;
 int depth = 0;
 for (int i = 0; i < toks.size(); i++) {
            Token t = toks.get(i);
 if (t.kind() == Kind.LPAREN) { depth++; continue; }
 if (t.kind() == Kind.RPAREN) { depth--; continue; }
 if (depth != 0) continue;
 if (t.kind() == Kind.WORD) {
                String up = t.text().toUpperCase(Locale.ROOT);
 if (SOURCE_INTRO.contains(up)) {
 inFrom = true;
 i = parseSource(stmt, toks, i + 1, sources) - 1;
 continue;
                }
 if (FROM_END.contains(up)) inFrom = false;
            } else if (t.kind() == Kind.COMMA && inFrom) {
 i = parseSource(stmt, toks, i + 1, sources) - 1;
            }
        }
 return new Scope(withPrefix, sources);
    }

    /** Text before the caret: returns the alias if the caret follows {@code alias.}, else null. */
 public static String aliasBeforeCaret(CharSequence text, int wordStart) {
 int i = wordStart;
 if (i == 0 || text.charAt(i - 1) != '.') return null;
 i--;
 int e = i;
 if (i > 0 && text.charAt(i - 1) == '"') {
 int q = i - 2;
 while (q >= 0 && text.charAt(q) != '"') q--;
 return q >= 0 ? text.subSequence(q + 1, i - 1).toString() : null;
        }
 while (i > 0 && (Character.isLetterOrDigit(text.charAt(i - 1)) || text.charAt(i - 1) == '_')) i--;
 return i < e ? text.subSequence(i, e).toString() : null;
    }

    /** Parses one source at {@code j}; returns the index of the first token after it (and its alias). */
 private static int parseSource(String stmt, List<Token> toks, int j, Map<String, String> sources) {
 if (j >= toks.size()) return j;
 int srcStart = j;
 if (toks.get(j).kind() == Kind.LPAREN) {
 j = skipParens(toks, j) + 1;                                            // subquery
        } else if (toks.get(j).kind() == Kind.WORD || toks.get(j).kind() == Kind.QUOTED_IDENT || toks.get(j).kind() == Kind.STRING) {
 if (toks.get(j).kind() == Kind.WORD && NOT_ALIAS.contains(toks.get(j).text().toUpperCase(Locale.ROOT))) return j; // "JOIN LATERAL" etc.
 j++;
 while (j + 1 < toks.size() && toks.get(j).kind() == Kind.DOT) j += 2;    // schema.table
 if (j < toks.size() && toks.get(j).kind() == Kind.LPAREN) j = skipParens(toks, j) + 1; // read_csv(...)
        } else {
 return j;
        }
        String src = stmt.substring(toks.get(srcStart).start(), toks.get(j - 1).end());
        String alias = null;
 if (j < toks.size() && toks.get(j).is("AS")) j++;
 if (j < toks.size() && (toks.get(j).kind() == Kind.WORD || toks.get(j).kind() == Kind.QUOTED_IDENT)
                && !NOT_ALIAS.contains(toks.get(j).text().toUpperCase(Locale.ROOT))) {
 alias = unquote(toks.get(j).text());
 j++;
 if (j < toks.size() && toks.get(j).kind() == Kind.LPAREN) j = skipParens(toks, j) + 1;     // alias(col, ...)
        }
 sources.put(alias != null ? alias : lastName(src), src);
 return j;
    }

 private static int skipParens(List<Token> toks, int open) {
 int depth = 0;
 for (int k = open; k < toks.size(); k++) {
 if (toks.get(k).kind() == Kind.LPAREN) depth++;
 else if (toks.get(k).kind() == Kind.RPAREN && --depth == 0) return k;
        }
 return toks.size() - 1;
    }

 private static String unquote(String s) {
 return s.length() >= 2 && s.startsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }

 private static String lastName(String src) {
        String s = src;
 int p = s.indexOf('(');
 if (p > 0) s = s.substring(0, p);
 int d = s.lastIndexOf('.');
 return unquote(d >= 0 ? s.substring(d + 1) : s).trim();
    }
}
