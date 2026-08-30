package org.data.wrangler.completion;

import java.util.List;
import java.util.Locale;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.data.wrangler.DuckDB;
import org.data.wrangler.SqlEditorBridge;
import org.data.wrangler.analysis.AnalysisConnection;
import org.data.wrangler.analysis.ColumnCatalog;
import org.data.wrangler.analysis.ScopeResolver;
import org.data.wrangler.analysis.StatementSplitter;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;

/**
 * Adds DuckDB keywords, types, live catalog functions, and schema-aware column /
 * table completion (alias- and CTE-aware, resolved by DuckDB via DESCRIBE) to
 * Ctrl+Space in the SQL editor. It is registered for {@code text/x-sql} but only contributes when
 * the editor's active connection is a DuckDB connection, so other databases are
 * unaffected. The built-in NetBeans SQL completion (tables, columns, ANSI
 * keywords) keeps running alongside; this provider is additive.
 */
@MimeRegistration(mimeType = "text/x-sql", service = CompletionProvider.class, position = 150)
public final class DuckDBCompletionProvider implements CompletionProvider {

    @Override
 public CompletionTask createTask(int queryType, JTextComponent component) {
 if (queryType != COMPLETION_QUERY_TYPE && queryType != COMPLETION_ALL_QUERY_TYPE) return null;
 return new AsyncCompletionTask(new Query(), component);
    }

    @Override
 public int getAutoQueryTypes(JTextComponent component, String typedText) {
 return 0; // never auto-pop on its own; the db.sql.editor completion handles that
    }

    /** Resolve the SQL editor's currently selected connection. */
 public static DatabaseConnection connectionFor(Document doc) {
 return SqlEditorBridge.connectionFor(doc);
    }

    /** Offset where the identifier under the caret begins. Package-private for tests. */
 static int wordStart(CharSequence text, int caret) {
 int i = caret;
 while (i > 0) {
 char c = text.charAt(i - 1);
 if (!(Character.isLetterOrDigit(c) || c == '_')) break;
 i--;
        }
 return i;
    }

 private static final class Query extends AsyncCompletionQuery {
        @Override
 protected void query(CompletionResultSet rs, Document doc, int caret) {
 try {
                DatabaseConnection dc = connectionFor(doc);
 boolean offline = dc == null;
 if (!offline && !DuckDB.isDuckDB(dc)) return;

                String text = doc.getText(0, doc.getLength());
 int start = wordStart(text, caret);
                String prefix = text.substring(start, caret).toLowerCase(Locale.ROOT);
                String alias = ScopeResolver.aliasBeforeCaret(text, start);
 java.sql.Connection conn = offline ? null : AnalysisConnection.get(dc);

                // 1. alias.<caret>  -> only that source's columns
 if (alias != null) {
 if (conn != null) addColumnsForAlias(rs, conn, text, caret, alias, prefix, start);
 return;
                }

                // 2. bare identifier: code templates on top, then columns in scope, tables/views, DuckDB vocabulary
                if (!prefix.isEmpty()) {
                    for (org.netbeans.lib.editor.codetemplates.api.CodeTemplate ct
                            : org.netbeans.lib.editor.codetemplates.api.CodeTemplateManager.get(doc).getCodeTemplates()) {
                        String abbr = ct.getAbbreviation();
                        if (abbr != null && abbr.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            rs.addItem(new TemplateCompletionItem(ct, start, caret));
                        }
                    }
                }
 if (conn != null) {
                    StatementSplitter.Statement stmt = StatementSplitter.at(text, caret);
 if (stmt != null) {
                        ScopeResolver.Scope scope = ScopeResolver.resolve(stmt.sql());
                        ColumnCatalog.allColumns(conn, scope).forEach((a, cols) -> {
 for (ColumnCatalog.Column c : cols)
 if (c.name().toLowerCase(Locale.ROOT).startsWith(prefix))
 rs.addItem(DuckDBCompletionItem.column(c, start, caret));
                        });
 for (String cte : scope.sources().keySet())
 if (cte.toLowerCase(Locale.ROOT).startsWith(prefix))
 rs.addItem(DuckDBCompletionItem.alias(cte, start, caret));
                    }
 for (ColumnCatalog.Relation r : ColumnCatalog.relations(conn))
 if (r.name().toLowerCase(Locale.ROOT).startsWith(prefix))
 rs.addItem(DuckDBCompletionItem.relation(r, start, caret));
                }

 for (String kw : DuckDBKeywords.KEYWORDS)
 if (kw.toLowerCase(Locale.ROOT).startsWith(prefix)) rs.addItem(DuckDBCompletionItem.keyword(kw, start, caret));
 for (String t : DuckDBKeywords.TYPES)
 if (t.toLowerCase(Locale.ROOT).startsWith(prefix)) rs.addItem(DuckDBCompletionItem.type(t, start, caret));
                List<FunctionInfo> fns = offline ? DuckDBKeywords.OFFLINE_FUNCTIONS : FunctionCatalog.getDefault().functions(dc);
 if (fns.isEmpty()) fns = DuckDBKeywords.OFFLINE_FUNCTIONS;
 for (FunctionInfo f : fns)
 if (f.name().toLowerCase(Locale.ROOT).startsWith(prefix)) rs.addItem(DuckDBCompletionItem.function(f, start, caret));
            } catch (BadLocationException ignore) {
                // nothing to complete
            } finally {
 rs.finish();
            }
        }

 private static void addColumnsForAlias(CompletionResultSet rs, java.sql.Connection conn, String text,
 int caret, String alias, String prefix, int start) {
            StatementSplitter.Statement stmt = StatementSplitter.at(text, caret);
 if (stmt == null) return;
            ScopeResolver.Scope scope = ScopeResolver.resolve(stmt.sql());
            String src = scope.sourceFor(alias);
 if (src == null) src = alias; // maybe a table name used without alias, or a schema
 for (ColumnCatalog.Column c : ColumnCatalog.columnsOf(conn, scope, alias, src))
 if (c.name().toLowerCase(Locale.ROOT).startsWith(prefix))
 rs.addItem(DuckDBCompletionItem.column(c, start, caret));
        }
    }
}
