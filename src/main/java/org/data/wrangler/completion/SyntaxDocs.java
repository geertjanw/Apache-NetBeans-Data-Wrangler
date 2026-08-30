package org.data.wrangler.completion;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Documentation for DuckDB syntax (keywords, clauses, types). Unlike functions,
 * which are documented by {@code duckdb_functions().description}, syntax has no
 * catalog table, so the text ships in {@code syntax-docs.properties}.
 */
public final class SyntaxDocs {

 public static final String BASE_URL = "https://duckdb.org/docs/stable/";
 public static final int TOOLTIP_WIDTH_PX = 420;
 private static final Logger LOG = Logger.getLogger(SyntaxDocs.class.getName());
 private static final Properties DOCS = new Properties();

 static {
 try (InputStream in = SyntaxDocs.class.getResourceAsStream("/org/data/wrangler/syntax-docs.properties")) {
 if (in != null) DOCS.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Cannot load syntax docs", ex);
        }
    }

 public record Doc(String keyword, String summary, String syntax, String example, URL url) {

        /** HTML for completion popups and hover tooltips. */
 public String html() {
            StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif'>");
 sb.append("<b>").append(esc(keyword)).append("</b>");
 if (summary != null) sb.append("<p>").append(esc(summary)).append("</p>");
 if (syntax != null) sb.append("<p><i>Syntax</i><br><code>").append(esc(syntax)).append("</code></p>");
 if (example != null) sb.append("<p><i>Example</i><br><pre>").append(esc(example)).append("</pre></p>");
 if (url != null) sb.append("<p><a href='").append(url).append("'>DuckDB documentation</a></p>");
 return sb.append("</body></html>").toString();
        }

        /** Shorter variant for hover tooltips; fixed width so Swing wraps the text. */
 public String tooltipHtml() {
            StringBuilder sb = new StringBuilder("<html><body style='width:" + TOOLTIP_WIDTH_PX + "px'><b>")
                    .append(esc(keyword)).append("</b>");
 if (summary != null) sb.append("<br>").append(esc(summary));
 if (syntax != null) sb.append("<br><br><code>").append(esc(syntax)).append("</code>");
 return sb.append("</body></html>").toString();
        }
    }

 private SyntaxDocs() {}

 public static Optional<Doc> lookup(String keyword) {
 if (keyword == null) return Optional.empty();
        String key = keyword.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        String summary = DOCS.getProperty(key + ".summary");
 if (summary == null) {
            // "CREATE MACRO" -> MACRO, "USING SAMPLE" -> SAMPLE, "FORCE CHECKPOINT" -> CHECKPOINT
 int us = key.lastIndexOf('_');
 if (us > 0) return lookup(key.substring(us + 1));
 return Optional.empty();
        }
        URL url = null;
        String rel = DOCS.getProperty(key + ".url");
 if (rel != null) {
 try { url = new URL(BASE_URL + rel); } catch (MalformedURLException ignore) { }
        }
 return Optional.of(new Doc(keyword.trim().toUpperCase(Locale.ROOT), summary,
                DOCS.getProperty(key + ".syntax"), DOCS.getProperty(key + ".example"), url));
    }

 public static boolean has(String keyword) { return lookup(keyword).isPresent(); }

 public static String esc(String s) {
 return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }
}
