package com.ruoran.houyi.admin.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV 生成：BOM、字段转义与公式注入防护。
 */
class CsvWriterTest {

    @Test
    void writesBomHeadersAndRows() {
        byte[] bytes = CsvWriter.build(List.of("a", "b"), List.of(List.of("1", "2")));
        String text = new String(bytes, StandardCharsets.UTF_8);

        assertTrue(text.startsWith("\uFEFF"));
        assertEquals("\uFEFF\"a\",\"b\"\r\n\"1\",\"2\"\r\n", text);
    }

    @Test
    void escapesQuotesAndNulls() {
        byte[] bytes = CsvWriter.build(List.of("col"), List.of(
                java.util.Arrays.asList("say \"hi\"", null)));
        String text = new String(bytes, StandardCharsets.UTF_8);

        assertTrue(text.contains("\"say \"\"hi\"\"\""));
        assertTrue(text.endsWith("\"\"\r\n"));
    }

    @Test
    void prefixesFormulaLikeValues() {
        assertEquals("'=1+1", CsvWriter.escape("=1+1"));
        assertEquals("'+A1", CsvWriter.escape("+A1"));
        assertEquals("'-B2", CsvWriter.escape("-B2"));
        assertEquals("'@cmd", CsvWriter.escape("@cmd"));
        assertEquals("plain", CsvWriter.escape("plain"));
    }
}
