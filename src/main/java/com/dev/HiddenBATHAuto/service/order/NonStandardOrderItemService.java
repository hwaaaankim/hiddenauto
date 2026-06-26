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
     * 현장주소, 출고완료 메시지, 거울 재단 상품 여부는 기존 DB 값을 유지합니다.
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
     * updatedByUsername은 반영하되, 현장주소, 출고완료 메시지, 거울 재단 상품 여부는 기존 DB 값을 유지합니다.
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
     * 기존 출고완료 메시지 처리용 메서드입니다.
     * 현장주소와 거울 재단 상품 여부는 기존 DB 값을 유지합니다.
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

    /**
     * /management/nonStandardTaskList 넓게보기 수정폼 전용 메서드입니다.
     * 일반 배송주소와 현장주소를 모두 수신하고, 배송수단에 따라 OrderUpdateService에서 검증/저장/삭제합니다.
     * 거울 재단 상품 여부도 이 수정폼에서만 직접 수신해 저장합니다.
     */
    @Transactional
    public void updateNonStandardOrderItemWithSiteAddress(
            Long orderId,
            int productCost,
            int quantity,
            int supplyPrice,
            int totalAmount,
            int packingCost,
            int deliveryCost,
            boolean mirrorCuttingProduct,
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
            String siteZipCode,
            String siteDoName,
            String siteSiName,
            String siteGuName,
            String siteRoadAddress,
            String siteDetailAddress,
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
                siteZipCode,
                siteDoName,
                siteSiName,
                siteGuName,
                siteRoadAddress,
                siteDetailAddress,
                ordererName,
                ordererPhone,
                optionJson,
                deleteAdminImageIds,
                adminImages,
                adminMemo,
                dispatchCompleteMessage,
                dispatchCompleteMessageSubmitted,
                updatedByUsername,
                mirrorCuttingProduct
        );
    }
}
