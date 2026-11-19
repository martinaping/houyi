package com.ruoran.houyi.admin.corp;

import java.util.List;

/**
 * 企业新增、编辑与批量启停的请求体。
 */
public final class CorpRequests {

    private CorpRequests() {
    }

    public record Create(String corpId, String corpName, String secret, String prikey,
                         Long limits, Long timeout, Boolean enabled) {
    }

    public record Update(String corpName, String secret, String prikey,
                         Long limits, Long timeout, Boolean enabled) {
    }

    public record BatchStatus(List<Long> ids, Boolean enabled) {
    }
}
