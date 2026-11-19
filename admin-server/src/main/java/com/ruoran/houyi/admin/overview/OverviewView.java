package com.ruoran.houyi.admin.overview;

import com.ruoran.houyi.admin.corp.CorpStats;
import com.ruoran.houyi.admin.message.MessageSummary;
import com.ruoran.houyi.admin.queue.QueueOverview;

import java.util.List;

/**
 * 首页概览：企业统计、队列状态与最近失败消息。
 */
public record OverviewView(
        CorpStats corps,
        QueueOverview queue,
        List<MessageSummary> recentFailures,
        String version,
        long serverTime
) {
}
