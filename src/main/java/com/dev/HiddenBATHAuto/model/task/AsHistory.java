package com.dev.HiddenBATHAuto.model.task;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.model.auth.Member;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tb_as_history")
@Data
public class AsHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private AsTask asTask;

    @Enumerated(EnumType.STRING)
    private AsStatus status;

    @ManyToOne
    private Member handler;

    private LocalDateTime changedAt = LocalDateTime.now();

    private String note;
}
