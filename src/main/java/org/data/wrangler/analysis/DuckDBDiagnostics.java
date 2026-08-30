package org.data.wrangler.analysis;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.data.wrangler.analysis.DuckDBErrorParser.Diagnostic;
import org.data.wrangler.analysis.StatementSplitter.Statement;

/**
 * Real DuckDB grammar checking. For every statement we run
 * {@code EXPLAIN <stmt>} on the analysis connection: EXPLAIN parses and binds but
 * never executes, so it reports syntax errors, unknown tables/columns/functions,
 * type mismatches, and - for duckdb-java#51 - it accepts every
 * extension function that is actually loaded, because it *is* DuckDB.
 */
public final class DuckDBDiagnostics {

    /** Statements EXPLAIN cannot plan or that we do not want to touch. */
 private static final Set<String> SKIP_LEADING = Set.of(
            "INSTALL", "LOAD", "SET", "RESET", "PRAGMA", "ATTACH", "DETACH", "USE", "CHECKPOINT",
            "BEGIN", "COMMIT", "ROLLBACK", "EXPORT", "IMPORT", "CALL", "EXPLAIN", "PREPARE",
            "EXECUTE", "DEALLOCATE", "VACUUM", "ANALYZE", "FORCE", "COPY");

 private DuckDBDiagnostics() {}

 public record Result(Statement statement, Diagnostic diagnostic) {
 public int start() { return statement.start() + diagnostic.start(); }
 public int end() { return statement.start() + diagnostic.end(); }
    }

 public static List<Result> analyze(Connection conn, CharSequence text) {
        List<Result> out = new ArrayList<>();
        Set<String> createdSoFar = new java.util.HashSet<>();
 for (Statement s : StatementSplitter.split(text)) {
 if (!shouldSkip(s.sql())) {
                Diagnostic d = check(conn, s.sql());
 if (d != null && !isSatisfiedByScript(d, createdSoFar)) out.add(new Result(s, d));
            }
 createdSoFar.addAll(createdNames(s.sql()));
        }
 return out;
    }

    /**
     * Objects the statement creates, so later statements in the same script that
     * reference them are not flagged "does not exist" before the script has run.
     * Purely textual: CREATE [OR REPLACE] [TEMP] TABLE|VIEW|MACRO|... [IF NOT EXISTS] name,
     * ATTACH ... AS name.
     */
 static Set<String> createdNames(String sql) {
        Set<String> names = new java.util.HashSet<>();
        List<SqlScanner.Token> toks = SqlScanner.scan(sql).stream()
                .filter(t -> t.kind() != SqlScanner.Kind.WS && t.kind() != SqlScanner.Kind.COMMENT).toList();
 if (toks.isEmpty()) return names;
 if (toks.get(0).is("CREATE")) {
 int i = 1;
 while (i < toks.size() && !isCreatableKind(toks.get(i))) i++;   // skip OR REPLACE / TEMP / TEMPORARY
 i++;
 if (i + 2 < toks.size() && toks.get(i).is("IF") && toks.get(i + 1).is("NOT") && toks.get(i + 2).is("EXISTS")) i += 3;
 if (i < toks.size()) names.add(unquote(lastPart(toks, i)));
        } else if (toks.get(0).is("ATTACH")) {
 for (int i = 1; i + 1 < toks.size(); i++) if (toks.get(i).is("AS")) { names.add(unquote(toks.get(i + 1).text())); break; }
        }
 return names;
    }

 private static boolean isCreatableKind(SqlScanner.Token t) {
 return t.is("TABLE") || t.is("VIEW") || t.is("MACRO") || t.is("FUNCTION") || t.is("SEQUENCE")
                || t.is("TYPE") || t.is("SCHEMA") || t.is("INDEX") || t.is("SECRET");
    }

    /** name, schema.name or catalog.schema.name starting at i -> the final identifier. */
 private static String lastPart(List<SqlScanner.Token> toks, int i) {
        String last = toks.get(i).text();
 while (i + 2 < toks.size() && toks.get(i + 1).kind() == SqlScanner.Kind.DOT) { i += 2; last = toks.get(i).text(); }
 return last;
    }

 private static String unquote(String s) {
 return s.length() >= 2 && s.startsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }

 static boolean isSatisfiedByScript(Diagnostic d, Set<String> created) {
 if (d.severity() != DuckDBErrorParser.Severity.WARNING || d.referencedName() == null) return false;
        String n = d.referencedName();
        String bare = n.contains(".") ? n.substring(n.lastIndexOf('.') + 1) : n;
        String lower = d.message().toLowerCase(Locale.ROOT);
 boolean missing = lower.contains("does not exist") || lower.contains("not found");
 return missing && created.stream().anyMatch(c -> c.equalsIgnoreCase(bare));
    }

 static boolean shouldSkip(String sql) {
        String first = firstWord(sql).toUpperCase(Locale.ROOT);
 return first.isEmpty() || SKIP_LEADING.contains(first);
    }

 private static final String PREFIX = "EXPLAIN ";

 static Diagnostic check(Connection conn, String sql) {
 synchronized (conn) {
 try (java.sql.Statement st = conn.createStatement()) {
 st.execute(PREFIX + sql);
 return null;
            } catch (SQLException ex) {
                String msg = ex.getMessage();
 if (msg == null) return null;
                // Prepared-parameter placeholders are fine in an editor; don't nag.
 if (msg.contains("Values were not provided") || msg.contains("parameter")) return null;
                // DuckDB's LINE/caret positions refer to the text it was given, i.e. including
                // the EXPLAIN prefix; parse against that and shift back to statement offsets.
                Diagnostic d = DuckDBErrorParser.parse(msg, PREFIX + sql);
 int start = Math.max(0, d.start() - PREFIX.length());
 int end = Math.max(start + 1, Math.min(sql.length(), d.end() - PREFIX.length()));
 return new Diagnostic(d.severity(), d.message(), start, end, d.referencedName());
            }
        }
    }

 private static String firstWord(String sql) {
 for (SqlScanner.Token t : SqlScanner.scan(sql)) {
 if (t.kind() == SqlScanner.Kind.WORD) return t.text();
 if (t.kind() != SqlScanner.Kind.WS && t.kind() != SqlScanner.Kind.COMMENT) return "";
        }
 return "";
    }
}
