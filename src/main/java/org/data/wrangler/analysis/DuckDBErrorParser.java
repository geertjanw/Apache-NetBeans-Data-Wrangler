package org.data.wrangler.analysis;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a DuckDB exception message into a {@link Diagnostic} with an offset.
 * DuckDB reports errors like
 * <pre>
 * Parser Error: syntax error at or near "FROMM"
 * LINE 2: SELECT * FROMM t
 *                  ^
 * </pre>
 * The caret line tells us the column; LINE n tells us the line within the statement.
 */
public final class DuckDBErrorParser {

 public enum Severity { ERROR, WARNING, HINT }

 public record Diagnostic(Severity severity, String message, int start, int end, String referencedName) {
 public Diagnostic(Severity severity, String message, int start, int end) { this(severity, message, start, end, null); }
    }

    /** "Parser Error: ...", "Binder Error: ...", also when prefixed (e.g. by the JDBC layer). */
 private static final Pattern HEAD = Pattern.compile("(Parser|Syntax|Binder|Catalog|Conversion|Invalid Input|Constraint|Permission|Not implemented|IO|Out of Range|Type|Dependency|Serialization|Transaction|Internal|Invalid|Unknown|HTTP|Settings)\\s+Error:\\s*(.*)$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
 private static final Pattern LINE = Pattern.compile("^LINE (\\d+): (.*)$", Pattern.MULTILINE);

 private DuckDBErrorParser() {}

    /** @param stmt the statement text that was analysed; offsets returned are relative to it */
 public static Diagnostic parse(String message, String stmt) {
 if (message == null) return null;
        // DuckDB >= 1.5 may wrap the real error in a pending-query envelope:
        //   "Invalid Input error: Attempting to execute ... pending query result\nError: Binder Error: ..."
        // so we take the LAST "<Category> Error:" head, which is the innermost one.
        Matcher h = HEAD.matcher(message);
        String category = "Error", text = message.strip();
 int headEnd = -1;
 while (h.find()) {
 category = h.group(1);
 text = h.group(2).strip();
 headEnd = h.end();
        }

        Severity sev = classify(category, text);

 int start = 0, end = Math.max(1, stmt.length());
 boolean positioned = false;
        Matcher l = LINE.matcher(message);
 if (l.find()) {
 int lineNo = Integer.parseInt(l.group(1));
            String[] lines = message.split("\n", -1);
 for (int i = 0; i < lines.length; i++) {
 if (lines[i].startsWith("LINE ") && i + 1 < lines.length && lines[i + 1].contains("^")) {
 int caretCol = lines[i + 1].indexOf('^') - ("LINE " + lineNo + ": ").length();
 int lineStart = offsetOfLine(stmt, lineNo);
 if (lineStart >= 0 && caretCol >= 0) {
 start = Math.min(stmt.length(), lineStart + caretCol);
 end = tokenEnd(stmt, start);
 positioned = true;
                    }
 break;
                }
            }
        }
        // Message = everything DuckDB said except the LINE/caret trailer, so
        // "Did you mean ...?" and "Candidate bindings: ..." hints are kept.
        StringBuilder msg = new StringBuilder(text);
 if (headEnd >= 0) {
 for (String line : message.substring(headEnd).split("\n")) {
                String extra = line.strip();
 if (extra.isEmpty() || extra.startsWith("LINE ") || extra.replace("^", "").isBlank()) continue;
 if (extra.equals(text)) continue;
 msg.append(' ').append(extra);
            }
        }
        String label = switch (sev) {
 case ERROR -> "Syntax error";
 case WARNING -> category.toLowerCase(java.util.Locale.ROOT).startsWith("catalog") ? "Unknown object" : "Unresolved";
 default -> category + " error";
        };
 if (!positioned) {
            String name = referencedName(text);
 if (name != null) {
 int[] span = findIdentifier(stmt, name);
 if (span != null) { start = span[0]; end = span[1]; }
            }
        }
 return new Diagnostic(sev, label + ": " + msg, start, end, referencedName(text));
    }

 private static final Pattern NAMED = Pattern.compile(
            "(?:Table|View|Column|Function|Schema|Catalog|Macro|Sequence|Type|Index)\\s+(?:with name|named)?\\s*\"?([A-Za-z_][A-Za-z0-9_.]*)\"?|Referenced column \"([^\"]+)\"|Catalog \"([^\"]+)\" does not exist",
            Pattern.CASE_INSENSITIVE);

 private static final Pattern EXT = Pattern.compile("exists in the ([A-Za-z0-9_]+) extension|INSTALL ([A-Za-z0-9_]+);\\s*LOAD", Pattern.CASE_INSENSITIVE);
 private static final Pattern DYM = Pattern.compile("Did you mean \"([^\"]+)\"");

    /** Extension DuckDB says would provide the missing function/type, or null. */
 public static String suggestedExtension(String message) {
 if (message == null) return null;
        Matcher m = EXT.matcher(message);
 if (!m.find()) return null;
 return m.group(1) != null ? m.group(1) : m.group(2);
    }

    /** DuckDB's "Did you mean" suggestion, or null. */
 public static String didYouMean(String message) {
 if (message == null) return null;
        Matcher m = DYM.matcher(message);
 return m.find() ? m.group(1) : null;
    }

    /** The object name DuckDB is complaining about, if the message names one. */
 public static String referencedName(String text) {
 if (text == null) return null;
        Matcher m = NAMED.matcher(text);
 if (!m.find()) return null;
 for (int g = 1; g <= m.groupCount(); g++) if (m.group(g) != null) return m.group(g);
 return null;
    }

    /** Offsets of the first whole-word, case-insensitive occurrence of name in stmt (outside strings). */
 static int[] findIdentifier(String stmt, String name) {
        String bare = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
 for (SqlScanner.Token t : SqlScanner.scan(stmt)) {
 if ((t.kind() == SqlScanner.Kind.WORD && t.text().equalsIgnoreCase(bare))
                    || (t.kind() == SqlScanner.Kind.QUOTED_IDENT && t.text().equalsIgnoreCase("\"" + bare + "\""))) {
 return new int[] { t.start(), t.end() };
            }
        }
 return null;
    }

    /** Category from the message head, with content-based fallback for unrecognised shapes. */
 static Severity classify(String category, String text) {
        String c = category.toLowerCase(java.util.Locale.ROOT);
 if (c.startsWith("parser") || c.startsWith("syntax")) return Severity.ERROR;
 if (c.startsWith("binder") || c.startsWith("catalog")) return Severity.WARNING;
        String t = text.toLowerCase(java.util.Locale.ROOT);
 if (t.contains("syntax error")) return Severity.ERROR;
 if (t.contains("not found") || t.contains("does not exist") || t.contains("referenced column")
                || t.contains("unknown column") || t.contains("no function matches") || t.contains("candidate")) return Severity.WARNING;
 return Severity.HINT;
    }

 private static int offsetOfLine(String s, int lineNo) {
 int off = 0;
 for (int i = 1; i < lineNo; i++) {
 off = s.indexOf('\n', off);
 if (off < 0) return -1;
 off++;
        }
 return off;
    }

 private static int tokenEnd(String s, int start) {
 int i = start;
 while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '"' || s.charAt(i) == '\'')) i++;
 return Math.max(start + 1, i);
    }
}
