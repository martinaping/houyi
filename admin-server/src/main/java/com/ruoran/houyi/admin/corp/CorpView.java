package com.ruoran.houyi.admin.corp;

/**
 * 企业档案视图。secret 只返回掩码，prikey 只返回是否配置。
 */
public record CorpView(
        long id,
        String corpId,
        String corpName,
        long limits,
        long timeout,
        long status,
        long updateAt,
        Long lastSeq,
        String secretMasked,
        boolean hasPrikey
) {
    public boolean enabled() {
        return status > 0;
    }
}
