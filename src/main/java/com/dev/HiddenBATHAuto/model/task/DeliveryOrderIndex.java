package com.dev.HiddenBATHAuto.model.task;

import java.time.LocalDate;

import com.dev.HiddenBATHAuto.model.auth.Member;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tb_delivery_order_index")
@Data
public class DeliveryOrderIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Member deliveryHandler;

    @OneToOne(fetch = FetchType.LAZY)
    private Order order;

    private LocalDate deliveryDate;

    private int orderIndex;
}
