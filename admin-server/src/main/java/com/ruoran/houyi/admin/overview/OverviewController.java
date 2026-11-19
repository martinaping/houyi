package com.ruoran.houyi.admin.overview;

import com.ruoran.houyi.admin.corp.CorpService;
import com.ruoran.houyi.admin.message.MessageService;
import com.ruoran.houyi.admin.queue.QueueService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页概览接口。
 */
@RestController
@RequestMapping("/api/admin/overview")
public class OverviewController {

    private static final int RECENT_FAILURE_LIMIT = 5;

    private final CorpService corpService;
    private final QueueService queueService;
    private final MessageService messageService;

    @Value("${admin.version}")
    private String version;

    public OverviewController(CorpService corpService, QueueService queueService,
                              MessageService messageService) {
        this.corpService = corpService;
        this.queueService = queueService;
        this.messageService = messageService;
    }

    @GetMapping
    public OverviewView overview() {
        return new OverviewView(
                corpService.stats(),
                queueService.overview(),
                messageService.list(null, null, null, MessageService.STATUS_DOWNLOAD_FAILED,
                        null, null, null, RECENT_FAILURE_LIMIT).items(),
                version,
                System.currentTimeMillis()
        );
    }
}
