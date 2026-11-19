/*
 * This file is part of the zyan/wework-msgaudit.
 *
 * (c) 读心印 <aa24615@qq.com>
 *
 * This source file is subject to the MIT license that is bundled
 * with this source code in the file LICENSE.
 */


package com.ruoran.houyi;

import com.ruoran.houyi.service.CorpConfigService;
import com.ruoran.houyi.service.EventBus;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * @author renlu
 */
@Slf4j
@Component
@Data
public class Start implements CommandLineRunner {

    @Resource
    DownloadThreadKeeper downloadThreadKeeper;

    @Resource
    CorpConfigService corpConfigService;

    @Resource
    CorpManager corpManager;

    @Resource
    EventBus eventBus;

    /**
     * 手动触发单个企业拉取线程，供调试接口使用。
     */
    public void triggerCorpId(String corpId) throws Exception {
        corpManager.trigger(corpId);
    }

    @Override
    public void run(String... args){
        log.info("=== 企业微信会话存档系统启动 ===");

        downloadThreadKeeper.init();
        // 启动阶段立即同步一次启用企业，后续由 CorpManager 定时增量同步
        corpManager.refresh(true);

        log.info("=== 所有企业消息处理线程已启动 ===");
    }
}
