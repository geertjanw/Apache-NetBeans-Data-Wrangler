package org.data.wrangler.completion;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.util.Set;
import javax.swing.ImageIcon;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.lib.editor.codetemplates.api.CodeTemplate;
import org.netbeans.spi.editor.completion.CompletionDocumentation;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;

/**
 * A DuckDB code template in the completion list. Two groups, shown at the top
 * of the list: table templates (the dt.../dv.../dins... wrappers that create or
 * fill tables and views) and the smaller snippets. Selecting one inserts the
 * template with its parameter fields, like pressing Tab after the abbreviation.
 */
public final class TemplateCompletionItem implements CompletionItem {

    /** Templates that create or fill tables and views: listed first. */
    static final Set<String> TABLE_TEMPLATES = Set.of(
            "dtvals", "dvvals", "dinsvals", "dtsel", "dview", "dttemp", "dtcsv", "dtpq", "dtjson", "dtxlsx", "dtable");

    private static final ImageIcon ICON = ImageUtilities.loadImageIcon("org/data/wrangler/sql.png", false);

    private final CodeTemplate template;
    private final int prefixStart;
    private final int caret;
    private final boolean tableGroup;

    public TemplateCompletionItem(CodeTemplate template, int prefixStart, int caret) {
        this.template = template;
        this.prefixStart = prefixStart;
        this.caret = caret;
        this.tableGroup = TABLE_TEMPLATES.contains(template.getAbbreviation());
    }

    private String left() {
        String desc = template.getDescription();
        return "<b>" + template.getAbbreviation() + "</b>" + (desc == null ? "" : "  <font color='#606060'>" + esc(desc) + "</font>");
    }

    private String right() {
        return tableGroup ? "Table template" : "Snippet";
    }

    @Override
    public void defaultAction(JTextComponent component) {
        Completion.get().hideAll();
        Document doc = component.getDocument();
        try {
            int len = caret - prefixStart;
            if (len > 0) doc.remove(prefixStart, len);
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }
        template.insert(component);
    }

    @Override public void processKeyEvent(KeyEvent evt) {}
    @Override public int getPreferredWidth(Graphics g, Font f) { return CompletionUtilities.getPreferredWidth(left(), right(), g, f); }
    @Override public void render(Graphics g, Font f, Color c, Color bg, int w, int h, boolean sel) {
        CompletionUtilities.renderHtml(ICON, left(), right(), g, f, c, w, h, sel);
    }
    @Override public CompletionTask createDocumentationTask() {
        String html = "<html><body><b>" + template.getAbbreviation() + "</b>"
                + (template.getDescription() == null ? "" : " - " + esc(template.getDescription()))
                + "<pre>" + esc(template.getParametrizedText()) + "</pre></body></html>";
        return new AsyncCompletionTask(new AsyncCompletionQuery() {
            @Override protected void query(CompletionResultSet rs, Document d, int off) {
                rs.setDocumentation(new Doc(html));
                rs.finish();
            }
        });
    }
    @Override public CompletionTask createToolTipTask() { return null; }
    @Override public boolean instantSubstitution(JTextComponent c) { return false; }
    /** Table templates 1, snippets 2: both above columns (10) and everything else. */
    @Override public int getSortPriority() { return tableGroup ? 1 : 2; }
    @Override public CharSequence getSortText() { return template.getAbbreviation(); }
    @Override public CharSequence getInsertPrefix() { return template.getAbbreviation(); }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    private record Doc(String html) implements CompletionDocumentation {
        @Override public String getText() { return html; }
        @Override public java.net.URL getURL() { return null; }
        @Override public CompletionDocumentation resolveLink(String l) { return null; }
        @Override public javax.swing.Action getGotoSourceAction() { return null; }
    }
}
