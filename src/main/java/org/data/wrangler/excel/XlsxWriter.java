package org.data.wrangler.excel;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal OOXML writer: all sheets, values only (numbers as numbers, everything
 * else as inline strings). No styles, formulas, merged cells or column widths;
 * read_xlsx does not surface those, so we cannot preserve them anyway.
 */
public final class XlsxWriter {

 public record SheetData(String name, List<String[]> rows) {}

 private XlsxWriter() {}

    /** Writes the workbook to any stream. In the IDE, pass a FileObject output stream so Local History records the version. */
 public static void write(OutputStream out, List<SheetData> sheets) throws IOException {
 try (ZipOutputStream zip = new ZipOutputStream(out)) {
 put(zip, "[Content_Types].xml", contentTypes(sheets.size()));
 put(zip, "_rels/.rels", RELS);
 put(zip, "xl/workbook.xml", workbook(sheets));
 put(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size()));
 for (int i = 0; i < sheets.size(); i++) put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheet(sheets.get(i).rows()));
        }
    }

    /** Plain-file variant (tests, tools outside the IDE): temp file then atomic replace. */
 public static void write(File out, List<SheetData> sheets) throws IOException {
        File tmp = new File(out.getParentFile(), out.getName() + ".tmp");
 try (OutputStream os = Files.newOutputStream(tmp.toPath())) {
 write(os, sheets);
        }
        Files.move(tmp.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

 private static void put(ZipOutputStream zip, String name, String xml) throws IOException {
 zip.putNextEntry(new ZipEntry(name));
 zip.write(xml.getBytes(StandardCharsets.UTF_8));
 zip.closeEntry();
    }

 private static String contentTypes(int n) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
 for (int i = 1; i <= n; i++) sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
            .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
 return sb.append("</Types>").toString();
    }

 private static final String RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
        + "</Relationships>";

 private static String workbook(List<SheetData> sheets) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
 for (int i = 0; i < sheets.size(); i++) sb.append("<sheet name=\"").append(esc(sheets.get(i).name())).append("\" sheetId=\"").append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
 return sb.append("</sheets></workbook>").toString();
    }

 private static String workbookRels(int n) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
 for (int i = 1; i <= n; i++) sb.append("<Relationship Id=\"rId").append(i).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
 return sb.append("</Relationships>").toString();
    }

 private static String sheet(List<String[]> rows) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
 for (int r = 0; r < rows.size(); r++) {
            String[] row = rows.get(r);
 sb.append("<row r=\"").append(r + 1).append("\">");
 for (int c = 0; c < row.length; c++) {
                String v = row[c];
 if (v == null || v.isEmpty()) continue;
                String ref = XlsxSheets.columnLabel(c) + (r + 1);
 if (SheetModel.isNumeric(v)) sb.append("<c r=\"").append(ref).append("\"><v>").append(v.replace(",", "")).append("</v></c>");
 else sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(esc(v)).append("</t></is></c>");
            }
 sb.append("</row>");
        }
 return sb.append("</sheetData></worksheet>").toString();
    }

 private static String esc(String s) {
 return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
