package com.dev.HiddenBATHAuto.model.task;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;

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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
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

    @Column(nullable = false)
    private boolean standard = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_category_id")
    private TeamCategory productCategory;

    @Column(name = "zip_code", length = 20)
    private String zipCode;
    
    private String doName;
    private String siName;
    private String guName;

    private String roadAddress;
    private String detailAddress;

    private int quantity;
    private int productCost;
    private String orderComment;

    private LocalDateTime preferredDeliveryDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_method_id")
    private DeliveryMethod deliveryMethod;

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

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private OrderItem orderItem;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderHistory> historyLogs;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderImage> orderImages = new ArrayList<>();

    @Lob
    @Column(name = "admin_memo", nullable = true)
    private String adminMemo;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;

    public void setOrderItem(OrderItem orderItem) {
        this.orderItem = orderItem;
        if (orderItem != null) {
            orderItem.setOrder(this);
        }
    }

    public void addOrderImage(OrderImage image) {
        if (image == null) {
            return;
        }
        if (this.orderImages == null) {
            this.orderImages = new ArrayList<>();
        }
        this.orderImages.add(image);
        image.setOrder(this);
    }

    public List<OrderImage> getCustomerUploadedImages() {
        if (orderImages == null) return List.of();
        return orderImages.stream()
                .filter(img -> "CUSTOMER".equalsIgnoreCase(img.getType()))
                .collect(Collectors.toList());
    }

    public List<OrderImage> getAdminUploadedImages() {
        if (orderImages == null) return List.of();
        return orderImages.stream()
                .filter(img -> "MANAGEMENT".equalsIgnoreCase(img.getType()))
                .collect(Collectors.toList());
    }

    public List<OrderImage> getDeliveryImages() {
        if (orderImages == null) return List.of();
        return orderImages.stream()
                .filter(img -> "DELIVERY".equalsIgnoreCase(img.getType()))
                .collect(Collectors.toList());
    }

    public List<OrderImage> getProofImages() {
        if (orderImages == null) return List.of();
        return orderImages.stream()
                .filter(img -> "PROOF".equalsIgnoreCase(img.getType()))
                .collect(Collectors.toList());
    }

    @Transient
    public List<OrderImage> getImagesByType(String type) {
        if (orderImages == null) return List.of();
        return orderImages.stream()
                .filter(img -> type != null && type.equalsIgnoreCase(img.getType()))
                .collect(Collectors.toList());
    }
}