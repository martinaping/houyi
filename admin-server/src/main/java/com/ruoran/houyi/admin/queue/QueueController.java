package com.ruoran.houyi.admin.queue;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 队列概览接口。
 */
@RestController
@RequestMapping("/api/admin/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping("/overview")
    public QueueOverview overview() {
        return queueService.overview();
    }
}
