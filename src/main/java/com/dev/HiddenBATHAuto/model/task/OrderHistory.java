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
@Table(name = "tb_order_history")
@Data
public class OrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Order order;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @ManyToOne
    private Member handler;

    private LocalDateTime changedAt = LocalDateTime.now();

    private String note;
}
