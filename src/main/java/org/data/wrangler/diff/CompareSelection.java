package org.data.wrangler.diff;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Which selections "Compare with DuckDB" accepts. The rules: one file (the
 * other is picked in a chooser), or exactly two files of the same kind.
 * NetBeans-free so it can be unit tested.
 */
final class CompareSelection {

    private CompareSelection() {
    }

    /**
     * Null when the selection can be compared; otherwise the reason, which is
     * shown as the tooltip of the greyed-out menu item.
     */
    static String problem(List<File> files) {
        if (files.isEmpty()) {
            return "Select a data file to compare";
        }
        if (files.size() > 2) {
            return "Select one or two files, not " + files.size();
        }
        if (files.size() == 2 && !sameKind(files.get(0), files.get(1))) {
            return "Cannot compare " + kind(files.get(0)) + " (" + files.get(0).getName()
                    + ") with " + kind(files.get(1)) + " (" + files.get(1).getName() + ")";
        }
        return null;
    }

    static boolean isExcel(File f) {
        String l = f.getName().toLowerCase(Locale.ROOT);
        return l.endsWith(".xlsx") || l.endsWith(".xlsm");
    }

    static String kind(File f) {
        String l = f.getName().toLowerCase(Locale.ROOT);
        if (l.endsWith(".parquet") || l.endsWith(".pq")) return "Parquet";
        if (isExcel(f)) return "Excel";
        if (l.endsWith(".json") || l.endsWith(".jsonl") || l.endsWith(".ndjson")) return "JSON";
        if (l.endsWith(".csv") || l.endsWith(".tsv")) return "CSV";
        return "other";
    }

    static boolean sameKind(File a, File b) {
        return kind(a).equals(kind(b));
    }

    static String[] kindExtensions(File f) {
        return switch (kind(f)) {
            case "Parquet" -> new String[] { "parquet", "pq" };
            case "Excel" -> new String[] { "xlsx", "xlsm" };
            case "JSON" -> new String[] { "json", "jsonl", "ndjson" };
            case "CSV" -> new String[] { "csv", "tsv" };
            default -> new String[] { "*" };
        };
    }
}
