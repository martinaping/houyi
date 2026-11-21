package com.ruoran.houyi.sync;

import com.ruoran.houyi.repo.OriginalMsgRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * @author lh
 */
@Component
@Slf4j
public class DeleteMessage {
    @Resource
    private OriginalMsgRepo originalMsgRepo;

    /**
     * 每天凌晨 5 点批量删除两天前的已归档消息。
     */
    @Scheduled(cron = "0 0 5 * * ?")
    @Transactional(rollbackOn = Exception.class)
    public void delete() {
        int cutoff = Integer.parseInt(LocalDate.now().minusDays(2)
                .format(DateTimeFormatter.BASIC_ISO_DATE));
        originalMsgRepo.deleteOldMsgs(cutoff);
    }
}
