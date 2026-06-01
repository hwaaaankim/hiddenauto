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

    /**
     * 기존 호출부 보호용 메서드입니다.
     * 출고완료 메시지 필드가 없는 기존 화면/기존 호출에서는 해당 값을 건드리지 않습니다.
     */
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
        updateNonStandardOrderItem(
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
                adminMemo,
                null,
                false,
                deleteAdminImageIds,
                adminImages,
                null
        );
    }

    /**
     * 기존 관리자 수정 호출부 보호용 메서드입니다.
     * updatedByUsername은 반영하되, 출고완료 메시지는 건드리지 않습니다.
     */
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
            List<MultipartFile> adminImages,
            String updatedByUsername
    ) {
        updateNonStandardOrderItem(
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
                adminMemo,
                null,
                false,
                deleteAdminImageIds,
                adminImages,
                updatedByUsername
        );
    }

    /**
     * 출고완료 메시지까지 함께 수정하는 신규 메서드입니다.
     *
     * dispatchCompleteMessageSubmitted 값이 true일 때만
     * Order.dispatchCompleteMessage 값을 수정합니다.
     *
     * 이 값을 둔 이유:
     * - 기존 상세 화면 또는 다른 수정 폼에 dispatchCompleteMessage input이 없을 수 있습니다.
     * - 그 상태에서 저장하면 기존 출고완료 메시지가 null로 덮이는 문제가 생길 수 있습니다.
     */
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
            String dispatchCompleteMessage,
            boolean dispatchCompleteMessageSubmitted,
            List<Long> deleteAdminImageIds,
            List<MultipartFile> adminImages,
            String updatedByUsername
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
                adminMemo,
                dispatchCompleteMessage,
                dispatchCompleteMessageSubmitted,
                updatedByUsername
        );
    }
}