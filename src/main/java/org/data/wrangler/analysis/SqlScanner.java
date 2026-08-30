package org.data.wrangler.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal SQL tokenizer: enough to find statement boundaries, skip strings and
 * comments, and locate identifiers / parentheses. It knows nothing
 * about grammar; DuckDB itself is the grammar (see {@link DuckDBDiagnostics}).
 */
public final class SqlScanner {

 public enum Kind { WORD, NUMBER, STRING, QUOTED_IDENT, COMMENT, LPAREN, RPAREN, COMMA, DOT, SEMI, OP, WS }

 public record Token(Kind kind, int start, int end, String text) {
 public boolean is(String word) { return kind == Kind.WORD && text.equalsIgnoreCase(word); }
    }

 private SqlScanner() {}

 public static List<Token> scan(CharSequence s) {
        List<Token> out = new ArrayList<>();
 int i = 0, n = s.length();
 while (i < n) {
 char c = s.charAt(i);
 int start = i;
 if (Character.isWhitespace(c)) {
 while (i < n && Character.isWhitespace(s.charAt(i))) i++;
 out.add(tok(Kind.WS, start, i, s));
            } else if (c == '-' && i + 1 < n && s.charAt(i + 1) == '-') {
 while (i < n && s.charAt(i) != '\n') i++;
 out.add(tok(Kind.COMMENT, start, i, s));
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
 i += 2;
 while (i < n && !(s.charAt(i) == '*' && i + 1 < n && s.charAt(i + 1) == '/')) i++;
 i = Math.min(n, i + 2);
 out.add(tok(Kind.COMMENT, start, i, s));
            } else if (c == '\'') {
 i++;
 while (i < n) {
 if (s.charAt(i) == '\'') {
 if (i + 1 < n && s.charAt(i + 1) == '\'') { i += 2; continue; }
 i++; break;
                    }
 i++;
                }
 out.add(tok(Kind.STRING, start, i, s));
            } else if (c == '$' && i + 1 < n && s.charAt(i + 1) == '$') {      // $$ dollar quoting
 int close = indexOf(s, "$$", i + 2);
 i = close < 0 ? n : close + 2;
 out.add(tok(Kind.STRING, start, i, s));
            } else if (c == '"') {
 i++;
 while (i < n && s.charAt(i) != '"') i++;
 i = Math.min(n, i + 1);
 out.add(tok(Kind.QUOTED_IDENT, start, i, s));
            } else if (Character.isLetter(c) || c == '_') {
 while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
 out.add(tok(Kind.WORD, start, i, s));
            } else if (Character.isDigit(c)) {
 while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
 out.add(tok(Kind.NUMBER, start, i, s));
            } else {
 i++;
                Kind k = switch (c) {
 case '(' -> Kind.LPAREN; case ')' -> Kind.RPAREN; case ',' -> Kind.COMMA;
 case '.' -> Kind.DOT; case ';' -> Kind.SEMI; default -> Kind.OP;
                };
 out.add(tok(k, start, i, s));
            }
        }
 return out;
    }

 private static Token tok(Kind k, int a, int b, CharSequence s) {
 return new Token(k, a, b, s.subSequence(a, b).toString());
    }

 private static int indexOf(CharSequence s, String needle, int from) {
 return s.toString().indexOf(needle, from);
    }
}
