package com.dev.HiddenBATHAuto.model.task;

import java.time.LocalDateTime;
import java.util.List;

import com.dev.HiddenBATHAuto.model.auth.Member;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tb_task")
@Data
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member requestedBy;

    /**
     * 우리회사측 발주 등록/관리 담당자입니다.
     *
     * DB 컬럼은 nullable/default null 로 먼저 추가합니다.
     * 그래야 서버 코드 배포 전에 DB를 먼저 반영해도 기존 발주 등록 로직이 실패하지 않습니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "managed_by_id", nullable = true)
    private Member managedBy;

    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.REQUESTED; // 요청 후 기본값

    @Column(name = "total_price")
    private int totalPrice;

    private String customerNote;
    private String internalNote;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL)
    private List<Order> orders;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL)
    private List<TaskHistory> historyLogs;
}
