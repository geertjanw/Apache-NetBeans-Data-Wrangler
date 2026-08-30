package org.data.wrangler.excel;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads workbook structure straight from the .xlsx zip: sheet names, and the
 * used range of each sheet (the rectangle of cells that actually hold values).
 * DuckDB's read_xlsx needs a sheet name but cannot list sheets, and its automatic
 * width detection stops at the first empty cell of the first row, which is wrong
 * for sheets with blank header cells; passing an explicit range avoids both.
 */
public final class XlsxSheets {

    /** A sheet's used range, e.g. A1:E23. */
 public record UsedRange(int lastColumn, int lastRow) {
 public String a1() { return "A1:" + columnLabel(lastColumn) + (lastRow + 1); }
 public int columns() { return lastColumn + 1; }
    }

 private static final Pattern SHEET = Pattern.compile("<sheet\\b[^>]*?\\bname=\"([^\"]*)\"[^>]*?\\br:id=\"([^\"]*)\"|<sheet\\b[^>]*?\\br:id=\"([^\"]*)\"[^>]*?\\bname=\"([^\"]*)\"");
 private static final Pattern REL = Pattern.compile("<Relationship\\b[^>]*?\\bId=\"([^\"]*)\"[^>]*?\\bTarget=\"([^\"]*)\"|<Relationship\\b[^>]*?\\bTarget=\"([^\"]*)\"[^>]*?\\bId=\"([^\"]*)\"");
    /** A cell that has content: a <v> value, an inline string, or a formula. */
 private static final Pattern CELL = Pattern.compile("<c\\b[^>]*?\\br=\"([A-Z]+)(\\d+)\"[^>]*?(?:/>|>(.*?)</c>)", Pattern.DOTALL);

 private XlsxSheets() {}

 public static List<String> sheetNames(File xlsx) throws IOException {
 return new ArrayList<>(sheetParts(xlsx).keySet());
    }

    /** sheet name -> zip entry path of its worksheet XML, in workbook order. */
 static Map<String, String> sheetParts(File xlsx) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
 try (ZipFile zip = new ZipFile(xlsx)) {
            String wb = read(zip, "xl/workbook.xml");
            Map<String, String> rels = new LinkedHashMap<>();
            String relsXml = read(zip, "xl/_rels/workbook.xml.rels");
 if (relsXml != null) {
                Matcher r = REL.matcher(relsXml);
 while (r.find()) {
                    String id = r.group(1) != null ? r.group(1) : r.group(4);
                    String target = r.group(1) != null ? r.group(2) : r.group(3);
 rels.put(id, target.startsWith("/") ? target.substring(1) : "xl/" + target);
                }
            }
 if (wb == null) throw new IOException("Not an .xlsx workbook: no xl/workbook.xml");
            Matcher m = SHEET.matcher(wb);
 int n = 0;
 while (m.find()) {
 n++;
                String name = unescape(m.group(1) != null ? m.group(1) : m.group(4));
                String rid = m.group(1) != null ? m.group(2) : m.group(3);
 out.put(name, rels.getOrDefault(rid, "xl/worksheets/sheet" + n + ".xml"));
            }
        }
 return out;
    }

    /** Rectangle from A1 to the last cell with content; null for an empty sheet. */
 public static UsedRange usedRange(File xlsx, String sheet) throws IOException {
        String part = sheetParts(xlsx).get(sheet);
 if (part == null) return null;
 try (ZipFile zip = new ZipFile(xlsx)) {
            String xml = read(zip, part);
 if (xml == null) return null;
 int lastCol = -1, lastRow = -1;
            Matcher c = CELL.matcher(xml);
 while (c.find()) {
                String body = c.group(3);
 if (body == null || !(body.contains("<v>") || body.contains("<is>") || body.contains("<f"))) continue;
 lastCol = Math.max(lastCol, columnIndex(c.group(1)));
 lastRow = Math.max(lastRow, Integer.parseInt(c.group(2)) - 1);
            }
 return lastCol < 0 ? null : new UsedRange(lastCol, lastRow);
        }
    }

 private static String read(ZipFile zip, String entry) throws IOException {
        ZipEntry e = zip.getEntry(entry);
 if (e == null) return null;
 try (InputStream in = zip.getInputStream(e)) {
 return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 0 -> A, 25 -> Z, 26 -> AA, like spreadsheet column headers. */
 public static String columnLabel(int index) {
        StringBuilder sb = new StringBuilder();
 int i = index;
 do {
 sb.insert(0, (char) ('A' + i % 26));
 i = i / 26 - 1;
        } while (i >= 0);
 return sb.toString();
    }

    /** A -> 0, Z -> 25, AA -> 26. */
 public static int columnIndex(String label) {
 int i = 0;
 for (char ch : label.toCharArray()) i = i * 26 + (ch - 'A' + 1);
 return i - 1;
    }

 private static String unescape(String s) {
 return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }
}
