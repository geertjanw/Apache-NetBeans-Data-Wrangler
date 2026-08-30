package org.data.wrangler.completion;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import javax.swing.ImageIcon;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.spi.editor.completion.CompletionDocumentation;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.data.wrangler.analysis.ColumnCatalog;

/** Completion entry for a DuckDB keyword, type or function. */
public final class DuckDBCompletionItem implements CompletionItem {

 public enum Kind { COLUMN, ALIAS, RELATION, FUNCTION, KEYWORD, TYPE }

 private static final ImageIcon ICON = ImageUtilities.loadImageIcon("org/data/wrangler/duckdb.png", false);

 private final Kind kind;
 private final String insertText;
 private final String leftHtml;
 private final String rightHtml;
 private final String documentationHtml;
 private final int startOffset;
 private final int caretOffset;
 private java.net.URL docUrl;

 private DuckDBCompletionItem(Kind kind, String insertText, String leftHtml, String rightHtml,
            String documentationHtml, int startOffset, int caretOffset) {
 this.kind = kind;
 this.insertText = insertText;
 this.leftHtml = leftHtml;
 this.rightHtml = rightHtml;
 this.documentationHtml = documentationHtml;
 this.startOffset = startOffset;
 this.caretOffset = caretOffset;
    }

 public static DuckDBCompletionItem keyword(String kw, int start, int caret) {
        SyntaxDocs.Doc doc = SyntaxDocs.lookup(kw).orElse(null);
        String right = doc != null && doc.summary() != null ? shorten(doc.summary(), 48) : "DuckDB keyword";
        DuckDBCompletionItem item = new DuckDBCompletionItem(Kind.KEYWORD, kw, "<b>" + kw + "</b>", right,
 doc != null ? doc.html() : "<b>" + kw + "</b><br>DuckDB-specific SQL syntax.", start, caret);
 item.docUrl = doc != null ? doc.url() : null;
 return item;
    }

 public static DuckDBCompletionItem type(String type, int start, int caret) {
        SyntaxDocs.Doc doc = SyntaxDocs.lookup(type).orElse(null);
        DuckDBCompletionItem item = new DuckDBCompletionItem(Kind.TYPE, type, type, "DuckDB type",
 doc != null ? doc.html() : "<b>" + type + "</b><br>DuckDB data type.", start, caret);
 item.docUrl = doc != null ? doc.url() : null;
 return item;
    }

 private static String shorten(String s, int max) {
 return s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
    }

 public static DuckDBCompletionItem function(FunctionInfo f, int start, int caret) {
        String left = "<font color='#0000B2'>" + f.name() + "</font>(" + String.join(", ", f.parameters()) + ")";
        String right = (f.returnType() == null ? f.type() : f.returnType());
        String doc = "<b>" + escape(f.signature()) + "</b>"
                + (f.returnType() != null ? " &rarr; " + escape(f.returnType()) : "")
                + "<br><i>" + f.type() + " function</i>"
                + (f.description() != null ? "<p>" + escape(f.description()) + "</p>" : "");
 return new DuckDBCompletionItem(Kind.FUNCTION, f.name() + "()", left, right, doc, start, caret);
    }

 public static DuckDBCompletionItem column(ColumnCatalog.Column c, int start, int caret) {
        String left = "<font color='#006400'>" + escape(c.name()) + "</font>";
        String right = escape(c.type()) + (c.source() != null ? "  <i>" + escape(c.source()) + "</i>" : "");
 return new DuckDBCompletionItem(Kind.COLUMN, quoteIfNeeded(c.name()), left, right,
                "<b>" + escape(c.name()) + "</b> : " + escape(c.type()) + "<br>from <i>" + escape(String.valueOf(c.source())) + "</i>", start, caret);
    }

 public static DuckDBCompletionItem alias(String name, int start, int caret) {
 return new DuckDBCompletionItem(Kind.ALIAS, quoteIfNeeded(name), escape(name), "in scope", "<b>" + escape(name) + "</b>", start, caret);
    }

 public static DuckDBCompletionItem relation(ColumnCatalog.Relation r, int start, int caret) {
        String qualified = "main".equals(r.schema()) ? r.name() : r.schema() + "." + r.name();
 return new DuckDBCompletionItem(Kind.RELATION, quoteIfNeeded(qualified), escape(r.name()), r.kind() + " (" + escape(r.schema()) + ")",
                "<b>" + escape(qualified) + "</b><br>" + r.kind(), start, caret);
    }

 private static String quoteIfNeeded(String ident) {
 return ident.matches("[A-Za-z_][A-Za-z0-9_.]*") ? ident : "\"" + ident.replace("\"", "\"\"") + "\"";
    }

 public Kind getKind() { return kind; }

    @Override
 public void defaultAction(JTextComponent component) {
 try {
            StyledDocument doc = (StyledDocument) component.getDocument();
 doc.remove(startOffset, caretOffset - startOffset);
 doc.insertString(startOffset, insertText, null);
 if (kind == Kind.FUNCTION) {
 component.setCaretPosition(startOffset + insertText.length() - 1); // inside the parentheses
            }
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
        Completion.get().hideAll();
    }

    @Override public void processKeyEvent(KeyEvent evt) {}

    @Override
 public int getPreferredWidth(Graphics g, Font font) {
 return CompletionUtilities.getPreferredWidth(leftHtml, rightHtml, g, font);
    }

    @Override
 public void render(Graphics g, Font defaultFont, Color defaultColor, Color backgroundColor,
 int width, int height, boolean selected) {
        CompletionUtilities.renderHtml(ICON, leftHtml, rightHtml, g, defaultFont, defaultColor, width, height, selected);
    }

    @Override
 public CompletionTask createDocumentationTask() {
 return new AsyncCompletionTask(new AsyncCompletionQuery() {
            @Override
 protected void query(CompletionResultSet rs, javax.swing.text.Document d, int caret) {
 rs.setDocumentation(new Doc(documentationHtml, docUrl));
 rs.finish();
            }
        });
    }

    @Override public CompletionTask createToolTipTask() { return null; }
    @Override public boolean instantSubstitution(JTextComponent component) { return false; }
    /** Columns in scope first, then relations, functions, keywords, types. */
    @Override public int getSortPriority() { return 10 + 10 * kind.ordinal(); }
    @Override public CharSequence getSortText() { return insertText; }
    @Override public CharSequence getInsertPrefix() { return insertText; }

 private static String escape(String s) {
 return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** getURL() non-null enables the "open in browser" button of the doc popup. */
 private record Doc(String html, java.net.URL url) implements CompletionDocumentation {
        @Override public String getText() { return html; }
        @Override public java.net.URL getURL() { return url; }
        @Override public CompletionDocumentation resolveLink(String link) { return null; }
        @Override public javax.swing.Action getGotoSourceAction() { return null; }
    }
}
