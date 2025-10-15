package com.dev.HiddenBATHAuto.model.task;

import java.time.LocalDate;

import com.dev.HiddenBATHAuto.model.auth.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Data;

@Data
@Entity
@Table(
    name = "tb_delivery_order_index",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_delivery_order_index_order", columnNames = {"order_id"})
    },
    indexes = {
        @Index(name = "idx_doi_handler_date_idx", columnList = "delivery_handler_id, delivery_date, order_index"),
        @Index(name = "idx_doi_order", columnList = "order_id")
    }
)
public class DeliveryOrderIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 담당자(배송 기사)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_handler_id")
    private Member deliveryHandler;

    // 주문(1:1)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    // 동시 업데이트로 인한 꼬임 방지(권장)
    @Version
    private Long version;
}
