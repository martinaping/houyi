package com.ruoran.houyi.admin.support;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 简单的 CSV 生成器：UTF-8 BOM + 全字段引号包裹，兼容 Excel。
 */
public final class CsvWriter {

    private CsvWriter() {
    }

    public static byte[] build(List<String> headers, List<List<String>> rows) {
        StringBuilder builder = new StringBuilder("\uFEFF");
        appendRow(builder, headers);
        for (List<String> row : rows) {
            appendRow(builder, row);
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendRow(StringBuilder builder, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(escape(values.get(i))).append('"');
        }
        builder.append("\r\n");
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        String text = value;
        int probe = 0;
        // Excel 会忽略前导空白，因此按第一个非空白字符判断公式注入。
        while (probe < text.length() && Character.isWhitespace(text.charAt(probe))) {
            probe++;
        }
        if (probe < text.length() && "=+-@\t".indexOf(text.charAt(probe)) >= 0) {
            text = "'" + text;
        }
        return text.replace("\"", "\"\"");
    }
}
