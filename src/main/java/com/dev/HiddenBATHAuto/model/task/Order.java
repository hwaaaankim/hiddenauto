package com.dev.HiddenBATHAuto.model.task;

import java.time.LocalDateTime;
import java.util.List;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;

import jakarta.persistence.CascadeType;
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
@Table(name = "tb_order")
@Data
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Task task;

    private String productCategory;
    private String deliveryAddress;
    private int quantity;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_production_team_category_id", nullable = true)
    private TeamCategory assignedProductionTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_delivery_team_category_id", nullable = true)
    private TeamCategory assignedDeliveryTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_production_handler_id", nullable = true)
    private Member assignedProductionHandler;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_delivery_handler_id", nullable = true)
    private Member assignedDeliveryHandler;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> orderItems;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderHistory> historyLogs;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderImage> deliveryImages; // 배송 완료 후 이미지 업로드

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderImage> proofImages; // 배송 증빙용 사진

    private LocalDateTime createdAt = LocalDateTime.now(); // 주문 등록일
    private LocalDateTime updatedAt;
}
