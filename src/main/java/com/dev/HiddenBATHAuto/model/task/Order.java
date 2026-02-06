package com.dev.HiddenBATHAuto.model.task;

import java.time.LocalDateTime;
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
    private boolean standard = false; // 규격 제품 주문 여부 (기본값 false)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_category_id")
    private TeamCategory productCategory;

    // 우편번호
    private String zipCode;

    // 행정구역
    private String doName;   // ex: 경기도
    private String siName;   // ex: 용인시
    private String guName;   // ex: 수지구

    // 주소
    private String roadAddress;     // ex: 경기도 용인시 수지구 죽전로 55
    private String detailAddress;   // ex: 302동 1502호

    private int quantity;
    private int productCost;                     // 제품비용 (단위: 원)
    private String orderComment;

    // ✅ 추가 필드
    private LocalDateTime preferredDeliveryDate; // 배송희망일

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_method_id") // FK 이름 명시
    private DeliveryMethod deliveryMethod; // ✅ 배송수단 엔티티 참조

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

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private OrderItem orderItem;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderHistory> historyLogs;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderImage> orderImages; // 배송 완료 후 이미지 업로드

    // ✅✅ 신규 추가: 관리자 남김말 (NULL 가능)
    @Lob
    @Column(name = "admin_memo", nullable = true)
    private String adminMemo;

    private LocalDateTime createdAt = LocalDateTime.now(); // 주문 등록일
    private LocalDateTime updatedAt;

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