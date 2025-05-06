package com.dev.HiddenBATHAuto.model.task;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.model.auth.Member;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tb_task_history")
@Data
public class TaskHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Task task;

    private String status;

    @ManyToOne
    private Member handler;

    private LocalDateTime changedAt = LocalDateTime.now();

    private String note;
}