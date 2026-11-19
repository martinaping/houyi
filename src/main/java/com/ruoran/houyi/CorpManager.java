package com.ruoran.houyi;

import com.ruoran.houyi.config.DynamicRuntimeConfig;
import com.ruoran.houyi.model.CorpInfo;
import com.ruoran.houyi.repo.CorplistRepo;
import com.ruoran.houyi.service.EventBus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 企业拉取线程管理器。
 *
 * <p>按管理端配置的间隔扫描 corplist，自动为新增启用企业启动拉取线程，
 * 为停用或删除的企业停止线程，实现不重启主服务的上下线。</p>
 */
@Slf4j
@Component
public class CorpManager {

    private final CorplistRepo corplistRepo;
    private final SpringContextUtils springContextUtils;
    private final EventBus eventBus;
    private final DynamicRuntimeConfig runtimeConfig;

    private final Map<String, Message> running = new ConcurrentHashMap<>();
    private final AtomicBoolean refreshing = new AtomicBoolean(false);
    private volatile long lastRefreshAt = 0L;

    public CorpManager(CorplistRepo corplistRepo, SpringContextUtils springContextUtils,
                       EventBus eventBus, DynamicRuntimeConfig runtimeConfig) {
        this.corplistRepo = corplistRepo;
        this.springContextUtils = springContextUtils;
        this.eventBus = eventBus;
        this.runtimeConfig = runtimeConfig;
    }

    @Scheduled(fixedDelay = 10_000L, initialDelay = 15_000L)
    public void scheduledRefresh() {
        long intervalMs = Math.max(5, runtimeConfig.getCorpRefreshSeconds()) * 1000L;
        if (System.currentTimeMillis() - lastRefreshAt < intervalMs) {
            return;
        }
        refresh(false);
    }

    /**
     * 扫描并同步启用的企业。force=true 时忽略刷新间隔，用于启动阶段。
     */
    public void refresh(boolean force) {
        long intervalMs = Math.max(5, runtimeConfig.getCorpRefreshSeconds()) * 1000L;
        if (!force && System.currentTimeMillis() - lastRefreshAt < intervalMs) {
            return;
        }
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        try {
            lastRefreshAt = System.currentTimeMillis();
            List<CorpInfo> enabled = corplistRepo.findAllByStatus(1L);
            Set<String> desired = new HashSet<>();
            for (CorpInfo corp : enabled) {
                if (StringUtils.isBlank(corp.getPrikey())) {
                    log.warn("企业 {} 未配置私钥，跳过拉取线程启动", corp.getCorpid());
                    continue;
                }
                desired.add(corp.getCorpid());
                if (!running.containsKey(corp.getCorpid())) {
                    startCorp(corp);
                }
            }
            for (Map.Entry<String, Message> entry : running.entrySet()) {
                if (!desired.contains(entry.getKey())) {
                    stopCorp(entry.getKey());
                }
            }
        } catch (Exception ex) {
            log.error("同步企业拉取线程失败", ex);
        } finally {
            refreshing.set(false);
        }
    }

    /**
     * 手动触发单个企业，企业不存在时抛出异常。
     */
    public void trigger(String corpId) throws Exception {
        if (running.containsKey(corpId)) {
            return;
        }
        Optional<CorpInfo> optional = corplistRepo.findFirstByCorpid(corpId);
        if (optional.isEmpty()) {
            throw new Exception("启动corp任务失败");
        }
        startCorp(optional.get());
    }

    public int runningCount() {
        return running.size();
    }

    private void startCorp(CorpInfo corpInfo) {
        Message message = new Message();
        message.init(corpInfo.getCorpid(), corpInfo.getSecret(), corpInfo.getPrikey());
        springContextUtils.autowireBean(message);
        running.put(corpInfo.getCorpid(), message);
        eventBus.getCorpsStatus().put(corpInfo.getCorpid(), true);
        message.start();
        log.info("企业拉取线程已启动: {} ({})", corpInfo.getCorpname(), corpInfo.getCorpid());
    }

    private void stopCorp(String corpId) {
        Message message = running.remove(corpId);
        if (message != null) {
            message.stopRunning();
        }
        eventBus.getCorpsStatus().put(corpId, false);
        log.info("企业拉取线程已停止: {}", corpId);
    }
}
