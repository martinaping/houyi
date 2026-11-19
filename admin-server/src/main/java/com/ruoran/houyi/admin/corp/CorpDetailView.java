package com.ruoran.houyi.admin.corp;

/**
 * 企业详情视图：在列表字段基础上补充消息量、拉取指标与凭据状态。
 */
public record CorpDetailView(
        long id,
        String corpId,
        String corpName,
        long limits,
        long timeout,
        long status,
        long updateAt,
        Long lastSeq,
        String secretMasked,
        boolean hasPrikey,
        long messageTotal,
        long messagePending,
        long messageFailed,
        long messageToday,
        Long pullLastAt,
        Long pullTotalFetched,
        String pullLastError,
        boolean redisReachable
) {
}
