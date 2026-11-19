package com.ruoran.houyi.admin.message;

import java.util.List;

/**
 * 消息列表分页结果。使用游标分页，cursor 为上一页最后一条记录的主键。
 */
public record MessagePage(List<MessageSummary> items, Long nextCursor) {
}
