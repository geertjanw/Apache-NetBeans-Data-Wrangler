package org.data.wrangler.analysis;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.db.explorer.DatabaseConnection;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.data.wrangler.DuckDB;
import org.data.wrangler.completion.DuckDBCompletionProvider;
import org.data.wrangler.completion.DuckDBKeywords;
import org.data.wrangler.completion.FunctionCatalog;
import org.data.wrangler.completion.FunctionInfo;
import org.data.wrangler.completion.SyntaxDocs;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.EditorStyleConstants;
import java.util.HashMap;
import java.util.Map;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.Fix;
import javax.swing.text.Position;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.HintsController;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.util.RequestProcessor;

/**
 * Per-document DuckDB analysis, wired in through the highlighting SPI (which is
 * the only per-editor hook a module gets without touching db.sql.editor):
 * <ul>
 *   <li>a highlight layer that colours DuckDB keywords/types and catalog
 * functions using the editor's own "keyword"/"method" colours, so
 *       {@code QUALIFY} or {@code st_distance(...)} are coloured like other keywords,
 * and attaches a hover tooltip (syntax docs for keywords, signature and
 * description for functions);</li>
 *   <li>a debounced DocumentListener that runs {@link DuckDBDiagnostics} and
 * publishes results as editor hints (red/yellow underline, error stripe, tooltip).</li>
 * </ul>
 * Both only activate when the editor's connection is DuckDB.
 */
@MimeRegistration(mimeType = "text/x-sql", service = HighlightsLayerFactory.class, position = 500)
public final class DuckDBEditorSupport implements HighlightsLayerFactory {

 private static final RequestProcessor RP = new RequestProcessor("DataWrangler analysis", 2, true);
 private static final String PROP = "duckdb.editor.support";

    /** Re-run analysis for a document (used by quick fixes). */
 public static void reanalyze(Document doc) {
        Object s = doc.getProperty(PROP);
 if (s instanceof Session session) session.schedule();
    }
 private static final String HINTS_LAYER = "duckdb";
 private static final int DEBOUNCE_MS = 400;

    @Override
 public HighlightsLayer[] createLayers(Context ctx) {
        Document doc = ctx.getDocument();
        Session s = (Session) doc.getProperty(PROP);
 if (s == null) {
 s = new Session(doc);
 doc.putProperty(PROP, s);
 doc.addDocumentListener(s);
 s.schedule();
        }
 return new HighlightsLayer[] {
            HighlightsLayer.create("duckdb-syntax", ZOrder.SYNTAX_RACK.forPosition(50), true, s.bag)
        };
    }

    /** One per document. */
 private static final class Session implements DocumentListener {
 private final Document doc;
 private final OffsetsBag bag;
 private final RequestProcessor.Task task;

        Session(Document doc) {
 this.doc = doc;
 this.bag = new OffsetsBag(doc, true);
 this.task = RP.create(this::run);
        }

 void schedule() { task.schedule(DEBOUNCE_MS); }
        @Override public void insertUpdate(DocumentEvent e) { schedule(); }
        @Override public void removeUpdate(DocumentEvent e) { schedule(); }
        @Override public void changedUpdate(DocumentEvent e) {}

 private void run() {
            DatabaseConnection dc = DuckDBCompletionProvider.connectionFor(doc);
            String text;
 try {
 text = doc.getText(0, doc.getLength());
            } catch (BadLocationException e) { return; }

 if (dc == null) {
                // No connection selected: still document DuckDB-only vocabulary (it belongs to no
                // other dialect), but no catalog functions, standard-keyword docs or diagnostics.
 highlight(text, null);
                HintsController.setErrors(doc, HINTS_LAYER, List.of());
 return;
            }
 if (!DuckDB.isDuckDB(dc)) {
 bag.clear();
                HintsController.setErrors(doc, HINTS_LAYER, List.of());
 return;
            }

 highlight(text, dc);

            Connection conn = AnalysisConnection.get(dc);
 if (conn == null) return;
            List<ErrorDescription> errors = new ArrayList<>();
 for (DuckDBDiagnostics.Result r : DuckDBDiagnostics.analyze(conn, text)) {
                Severity sev = switch (r.diagnostic().severity()) {
 case ERROR -> Severity.ERROR;
 case WARNING -> Severity.WARNING;
 default -> Severity.HINT;
                };
 try {
                    Position start = doc.createPosition(r.start());
                    Position end = doc.createPosition(Math.min(doc.getLength(), r.end()));
                    List<Fix> fixes = new ArrayList<>();
                    String ext = DuckDBErrorParser.suggestedExtension(r.diagnostic().message());
 if (ext != null) fixes.add(new InstallExtensionFix(dc, ext, doc));
                    String dym = DuckDBErrorParser.didYouMean(r.diagnostic().message());
 if (dym != null) fixes.add(new ReplaceIdentifierFix(doc, start, end, dym));
 errors.add(ErrorDescriptionFactory.createErrorDescription(sev, r.diagnostic().message(), fixes, doc, start, end));
                } catch (BadLocationException ignore) { }
            }
            HintsController.setErrors(doc, HINTS_LAYER, errors);
        }

 private void highlight(String text, DatabaseConnection dc) {
            FontColorSettings fcs = MimeLookup.getLookup("text/x-sql").lookup(FontColorSettings.class);
            AttributeSet keyword = fcs == null ? null : fcs.getTokenFontColors("keyword");
            AttributeSet method = fcs == null ? null : fcs.getTokenFontColors("method");
 if (method == null) method = fcs == null ? null : fcs.getTokenFontColors("identifier");
 if (keyword == null) return;

            Map<String, FunctionInfo> fns = new HashMap<>();
 if (dc != null) {
 for (FunctionInfo f : FunctionCatalog.getDefault().functions(dc)) fns.putIfAbsent(f.name().toLowerCase(Locale.ROOT), f);
            }
 boolean connected = dc != null;

            OffsetsBag fresh = new OffsetsBag(doc, true);
            List<SqlScanner.Token> toks = SqlScanner.scan(text);
 for (int i = 0; i < toks.size(); i++) {
                SqlScanner.Token t = toks.get(i);
 if (t.kind() != SqlScanner.Kind.WORD) continue;
                String up = t.text().toUpperCase(Locale.ROOT);
 if (DuckDBKeywords.KEYWORDS.contains(up) || DuckDBKeywords.TYPES.contains(up)) {
 fresh.addHighlight(t.start(), t.end(), withTooltip(keyword,
                            SyntaxDocs.lookup(up).map(SyntaxDocs.Doc::tooltipHtml).orElse(null)));
                } else if (connected && DuckDBKeywords.DOCUMENTED_STANDARD.contains(up)) {
                    // already coloured by the SQL lexer; only add the tooltip
                    String tip = SyntaxDocs.lookup(up).map(SyntaxDocs.Doc::tooltipHtml).orElse(null);
 if (tip != null) fresh.addHighlight(t.start(), t.end(),
                            AttributesUtilities.createImmutable(EditorStyleConstants.Tooltip, tip));
                } else if (method != null && fns.containsKey(t.text().toLowerCase(Locale.ROOT)) && nextNonWsIsParen(toks, i)) {
 fresh.addHighlight(t.start(), t.end(), withTooltip(method, functionTooltip(fns.get(t.text().toLowerCase(Locale.ROOT)))));
                }
            }
 bag.setHighlights(fresh);
        }

 private static AttributeSet withTooltip(AttributeSet base, String html) {
 if (html == null) return base;
 return AttributesUtilities.createComposite(
                    AttributesUtilities.createImmutable(EditorStyleConstants.Tooltip, html), base);
        }

 private static String functionTooltip(FunctionInfo f) {
            StringBuilder sb = new StringBuilder("<html><body style='width:" + SyntaxDocs.TOOLTIP_WIDTH_PX + "px'><b>")
                    .append(SyntaxDocs.esc(f.signature())).append("</b>");
 if (f.returnType() != null) sb.append(" &rarr; ").append(SyntaxDocs.esc(f.returnType()));
 sb.append("<br><i>").append(f.type()).append(" function</i>");
 if (f.description() != null && !f.description().isBlank()) sb.append("<br>").append(SyntaxDocs.esc(f.description()));
 return sb.append("</body></html>").toString();
        }

 private static boolean nextNonWsIsParen(List<SqlScanner.Token> toks, int i) {
 for (int k = i + 1; k < toks.size(); k++) {
 if (toks.get(k).kind() == SqlScanner.Kind.WS) continue;
 return toks.get(k).kind() == SqlScanner.Kind.LPAREN;
            }
 return false;
        }
    }
}
