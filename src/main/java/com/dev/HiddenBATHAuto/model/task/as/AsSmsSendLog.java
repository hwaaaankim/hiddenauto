package com.dev.HiddenBATHAuto.model.task.as;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.model.task.AsTaskSchedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "tb_as_sms_send_log",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_as_sms_send_log_schedule_send_date",
            columnNames = { "as_task_schedule_id", "send_date" }
        )
    },
    indexes = {
        @Index(name = "idx_as_sms_send_log_send_date", columnList = "send_date"),
        @Index(name = "idx_as_sms_send_log_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsSmsSendLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 어떤 스케줄(= 어떤 AS Task의 어떤 방문일자)에 대해 발송했는지
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "as_task_schedule_id", nullable = false)
    private AsTaskSchedule asTaskSchedule;

    // 언제 발송 대상으로 처리했는지(기준일: 내일)
    @Column(name = "send_date", nullable = false)
    private LocalDate sendDate;

    // 실제로 발송한 번호(정규화된 숫자만)
    @Column(name = "phone_digits", length = 32)
    private String phoneDigits;

    // 발송 메시지(감사/감사 로그 목적)
    @Column(name = "message", length = 2000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}