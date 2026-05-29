package com.dev.HiddenBATHAuto.service.order;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class NonStandardOrderItemService {

    private final OrderUpdateService orderUpdateService;

    @Transactional
    public void updateNonStandardOrderItem(
            Long orderId,
            int productCost,
            int quantity,
            int supplyPrice,
            int totalAmount,
            int packingCost,
            int deliveryCost,
            LocalDate preferredDeliveryDate,
            String statusStr,
            Optional<Long> deliveryMethodId,
            Optional<Long> deliveryHandlerId,
            Optional<Long> productCategoryId,
            Optional<Long> companyId,
            Optional<Long> requesterMemberId,
            String zipCode,
            String doName,
            String siName,
            String guName,
            String roadAddress,
            String detailAddress,
            String ordererName,
            String ordererPhone,
            String optionJson,
            String adminMemo,
            List<Long> deleteAdminImageIds,
            List<MultipartFile> adminImages
    ) {
        orderUpdateService.updateOrder(
                orderId,
                productCost,
                quantity,
                supplyPrice,
                totalAmount,
                packingCost,
                deliveryCost,
                preferredDeliveryDate,
                statusStr,
                deliveryMethodId,
                deliveryHandlerId,
                productCategoryId,
                companyId,
                requesterMemberId,
                zipCode,
                doName,
                siName,
                guName,
                roadAddress,
                detailAddress,
                ordererName,
                ordererPhone,
                optionJson,
                deleteAdminImageIds,
                adminImages,
                adminMemo
        );
    }
}
