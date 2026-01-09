package com.dev.HiddenBATHAuto.config;


import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.service.as.AsVisitReminderSmsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class AsVisitReminderScheduler {

    private final AsVisitReminderSmsService asVisitReminderSmsService;

    // 매일 21:00 (KST)
    @Scheduled(cron = "0 0 21 * * *", zone = "Asia/Seoul")
    public void sendTomorrowVisitReminderSms() {
        log.info("[AS-SMS] Scheduler started.");
        asVisitReminderSmsService.sendTomorrowVisitReminders();
        log.info("[AS-SMS] Scheduler finished.");
    }
}