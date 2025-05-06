package com.dev.HiddenBATHAuto.model.task;

import java.time.LocalDateTime;
import java.util.List;

import com.dev.HiddenBATHAuto.model.auth.Member;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    @Enumerated(EnumType.STRING)
    private TaskStatus status = TaskStatus.REQUESTED; // 요청 후 기본값

    private String customerNote;
    private String internalNote;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL)
    private List<Order> orders;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL)
    private List<TaskHistory> historyLogs;
}

