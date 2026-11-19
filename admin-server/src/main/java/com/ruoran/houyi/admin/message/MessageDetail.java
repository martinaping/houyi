package com.ruoran.houyi.admin.message;

/**
 * 消息详情，包含原始 content 与文件信息。
 */
public record MessageDetail(
        long id,
        String corpId,
        String msgId,
        long seq,
        String content,
        String sdkFileId,
        long dateNo,
        String msgType,
        String filePath,
        long downFinishAt,
        long downFailAt,
        String md5Sum,
        int retryTimeout,
        String ossPath,
        long createAt,
        Long pushAt
) {
    public boolean hasFile() {
        return sdkFileId != null && !sdkFileId.isBlank();
    }

    public boolean pushed() {
        return pushAt != null && pushAt > 0;
    }
}
