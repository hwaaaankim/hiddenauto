package com.dev.HiddenBATHAuto.service.as;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.AsTaskSchedule;
import com.dev.HiddenBATHAuto.model.task.as.AsSmsSendLog;
import com.dev.HiddenBATHAuto.repository.as.AsSmsSendLogRepository;
import com.dev.HiddenBATHAuto.repository.as.AsTaskScheduleRepository;
import com.dev.HiddenBATHAuto.service.SMSService;
import com.dev.HiddenBATHAuto.utils.PhoneNumberUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsVisitReminderSmsService {

    private final AsTaskScheduleRepository asTaskScheduleRepository;
    private final AsSmsSendLogRepository asSmsSendLogRepository; // ✅ 중복 방지(권장)
    private final SMSService smsService;

    /**
     * 문자 템플릿(운영에서 쉽게 바꾸도록 yml로 뺄 수 있게 처리)
     * - {name} : 요청자 이름(없으면 공백)
     * - {subject} : AS 제목
     * - {date} : 방문일(yyyy-MM-dd)
     */
    @Value("${as.sms.reminder.template:[히든오토] 내일({date}) AS 방문 예정입니다. 제목: {subject}}")
    private String messageTemplate;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Transactional
    public void sendTomorrowVisitReminders() {
        LocalDate tomorrow = LocalDate.now(KST).plusDays(1);

        List<AsTaskSchedule> schedules =
                asTaskScheduleRepository.findSchedulesByDateAndTaskStatus(tomorrow, AsStatus.IN_PROGRESS);

        if (schedules.isEmpty()) {
            log.info("[AS-SMS] No schedules for tomorrow={}, status=IN_PROGRESS", tomorrow);
            return;
        }

        log.info("[AS-SMS] Found {} schedule(s) for tomorrow={}, status=IN_PROGRESS", schedules.size(), tomorrow);

        int sent = 0;
        int skippedInvalidPhone = 0;
        int skippedDuplicate = 0;
        int failed = 0;

        for (AsTaskSchedule schedule : schedules) {
            AsTask task = schedule.getAsTask();
            if (task == null) {
                log.warn("[AS-SMS] scheduleId={} has null task. skip.", schedule.getId());
                continue;
            }

            // onsiteContact 기준
            String rawPhone = task.getOnsiteContact();
            var normalizedOpt = PhoneNumberUtil.normalizeKoreanMobile(rawPhone);

            if (normalizedOpt.isEmpty()) {
                skippedInvalidPhone++;
                log.warn("[AS-SMS] Invalid onsiteContact. taskId={}, scheduleId={}, raw={}", task.getId(), schedule.getId(), rawPhone);
                continue;
            }

            String phoneDigits = normalizedOpt.get();

            // ✅ 중복 발송 방지 (스케줄ID + 내일 기준일)
            if (asSmsSendLogRepository.existsByAsTaskSchedule_IdAndSendDate(schedule.getId(), tomorrow)) {
                skippedDuplicate++;
                log.info("[AS-SMS] Duplicate detected. scheduleId={}, sendDate={}. skip.", schedule.getId(), tomorrow);
                continue;
            }

            String message = buildMessage(task, tomorrow);

            try {
                smsService.sendMessage(phoneDigits, message);

                // 발송 로그 저장 (idempotency)
                AsSmsSendLog logEntity = AsSmsSendLog.builder()
                        .asTaskSchedule(schedule)
                        .sendDate(tomorrow)
                        .phoneDigits(phoneDigits)
                        .message(message)
                        .build();
                asSmsSendLogRepository.save(logEntity);

                sent++;
            } catch (Exception e) {
                failed++;
                log.error("[AS-SMS] Send failed. taskId={}, scheduleId={}, phone={}, err={}",
                        task.getId(), schedule.getId(), phoneDigits, e.getMessage(), e);
            }
        }

        log.info("[AS-SMS] Result tomorrow={} sent={}, dupSkip={}, invalidPhoneSkip={}, failed={}",
                tomorrow, sent, skippedDuplicate, skippedInvalidPhone, failed);
    }

    private String buildMessage(AsTask task, LocalDate visitDate) {

        Member engineer = task.getAssignedHandler();

        String engineerName = "";
        String engineerPhone = "";

        if (engineer != null) {
            engineerName = safe(engineer.getName());
            engineerPhone = safe(engineer.getPhone());
        }

        return messageTemplate
                .replace("{date}", visitDate.toString())
                .replace("{engineerName}", engineerName.isBlank() ? "-" : engineerName)
                .replace("{engineerPhone}", engineerPhone.isBlank() ? "-" : engineerPhone);
    }

    private String safe(String v) {
        return (v == null) ? "" : v;
    }
}