package com.dev.HiddenBATHAuto.dto;

import com.dev.HiddenBATHAuto.model.task.Order;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderSummaryDTO {
    private String createdAt;
    private String preferredDeliveryDate;
    private String address;
    private int quantity;
    private int price;
    private String categoryName;

    public static OrderSummaryDTO from(Order order) {
        return new OrderSummaryDTO(
            order.getCreatedAt().toLocalDate().toString(),
            order.getPreferredDeliveryDate() != null ? order.getPreferredDeliveryDate().toLocalDate().toString() : null,
            order.getRoadAddress() + " " + order.getDetailAddress(),
            order.getOrderItem() != null ? order.getOrderItem().getQuantity() : 0,
            order.getProductCost(),
            order.getProductCategory() != null ? order.getProductCategory().getName() : "-"
        );
    }
}
