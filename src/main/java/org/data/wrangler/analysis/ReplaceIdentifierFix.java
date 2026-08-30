package org.data.wrangler.analysis;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Position;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.Fix;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;

/** Alt-Enter fix: replace the underlined identifier with DuckDB's "Did you mean" suggestion. */
public final class ReplaceIdentifierFix implements Fix {

 private final Document doc;
 private final Position start, end;
 private final String replacement;

 public ReplaceIdentifierFix(Document doc, Position start, Position end, String replacement) {
 this.doc = doc;
 this.start = start;
 this.end = end;
 this.replacement = replacement;
    }

    @Override
 public String getText() {
 return "Replace with '" + replacement + "'";
    }

    @Override
 public ChangeInfo implement() {
        Runnable r = () -> {
 try {
 int s = start.getOffset(), e = end.getOffset();
 doc.remove(s, e - s);
 doc.insertString(s, replacement, null);
            } catch (BadLocationException ex) {
                Exceptions.printStackTrace(ex);
            }
        };
 if (doc instanceof javax.swing.text.StyledDocument sd) NbDocument.runAtomic(sd, r); else r.run();
        DuckDBEditorSupport.reanalyze(doc);
 return null;
    }
}
