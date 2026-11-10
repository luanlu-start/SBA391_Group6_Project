package com.fptpost.modules.payment.util;

import java.util.ArrayList;
import java.util.List;

public final class CsvSupport {

    private CsvSupport() {
    }

    /** Quotes when needed and neutralises spreadsheet formulas in user-supplied text. */
    public static String cell(String value) {
        if (value == null) return "";
        String text = value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    public static String row(List<String> values) {
        return String.join(",", values.stream().map(CsvSupport::cell).toList());
    }

    /** Parses a single RFC 4180 line; embedded line breaks are not supported. */
    public static List<String> parse(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (quoted) throw new IllegalArgumentException("Unterminated quoted value");
        cells.add(current.toString().trim());
        return cells;
    }
}
