package org.data.wrangler.analysis;

import java.util.ArrayList;
import java.util.List;
import org.data.wrangler.analysis.SqlScanner.Kind;
import org.data.wrangler.analysis.SqlScanner.Token;

/** Splits editor text into statements (offsets are document offsets). */
public final class StatementSplitter {

 public record Statement(int start, int end, String sql) {
 public boolean contains(int offset) { return offset >= start && offset <= end; }
    }

 private StatementSplitter() {}

 public static List<Statement> split(CharSequence text) {
        List<Statement> out = new ArrayList<>();
 int stmtStart = -1, lastNonWs = -1;
 for (Token t : SqlScanner.scan(text)) {
 if (t.kind() == Kind.SEMI) {
 if (stmtStart >= 0) out.add(new Statement(stmtStart, lastNonWs, text.subSequence(stmtStart, lastNonWs).toString()));
 stmtStart = -1;
            } else if (t.kind() != Kind.WS && t.kind() != Kind.COMMENT) {
 if (stmtStart < 0) stmtStart = t.start();
 lastNonWs = t.end();
            }
        }
 if (stmtStart >= 0) out.add(new Statement(stmtStart, lastNonWs, text.subSequence(stmtStart, lastNonWs).toString()));
 return out;
    }

 public static Statement at(CharSequence text, int offset) {
 for (Statement s : split(text)) if (s.contains(offset)) return s;
 return null;
    }
}
