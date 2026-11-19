package com.ruoran.houyi.admin.message;

/**
 * 消息列表行，不包含 content 大字段。
 */
public record MessageSummary(
        long id,
        String corpId,
        String msgId,
        long seq,
        String msgType,
        long dateNo,
        long downFinishAt,
        long downFailAt,
        Long pushAt,
        long createAt,
        boolean hasFile
) {
}
