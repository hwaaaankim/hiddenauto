package com.dev.HiddenBATHAuto.service.team.delivery;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.delivery.DeliveryOrderSummaryRes;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderImage;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;
import com.dev.HiddenBATHAuto.utils.OrderItemOptionJsonUtil;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DeliveryOrderSummaryService {

    private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;

    public DeliveryOrderSummaryRes getSummary(Long handlerId, Long orderId) {

        DeliveryOrderIndex doi = deliveryOrderIndexRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new IllegalArgumentException("배송 인덱스에 없는 주문입니다."));

        if (doi.getDeliveryHandler() == null || doi.getDeliveryHandler().getId() == null
                || !doi.getDeliveryHandler().getId().equals(handlerId)) {
            throw new org.springframework.security.access.AccessDeniedException("담당자 불일치");
        }

        Order order = doi.getOrder();
        if (order == null) {
            throw new IllegalArgumentException("주문 정보가 없습니다.");
        }

        Task task = order.getTask();
        Member requester = (task != null ? task.getRequestedBy() : null);
        Company company = (requester != null ? requester.getCompany() : null);

        String companyName = (company != null ? safe(company.getCompanyName()) : "-");
        String requesterName = (requester != null ? safe(requester.getName()) : "-");

        // 업체 연락처
        String contact = "-";
        if (requester != null) {
            String p1 = safe(requester.getPhone());
            String p2 = safe(requester.getTelephone());
            contact = !isBlank(p1) ? p1 : (!isBlank(p2) ? p2 : "-");
        }

        // 업체 주소
        String companyAddr = "-";
        if (company != null) {
            String zip = safe(company.getZipCode());
            String road = safe(company.getRoadAddress());
            String detail = safe(company.getDetailAddress());

            StringBuilder sb = new StringBuilder();
            if (!isBlank(zip)) sb.append("(").append(zip).append(") ");
            if (!isBlank(road)) sb.append(road);
            if (!isBlank(detail)) sb.append(" ").append(detail);

            companyAddr = sb.length() == 0 ? "-" : sb.toString().trim();
        }

        // 배송지 주소
        String orderAddr = (safe(order.getRoadAddress()) + " " + safe(order.getDetailAddress())).trim();
        if (isBlank(orderAddr)) orderAddr = "-";

        // 제품 내용
        OrderItem item = order.getOrderItem();
        String productText = "-";
        if (item != null) {
            OrderItemOptionJsonUtil.enrich(item);
            String t = safe(item.getFormattedOptionText());
            if (!isBlank(t)) productText = t;
            else productText = safe(item.getProductName());
        }

        String status = (order.getStatus() != null ? order.getStatus().name() : "-");
        String statusLabel = (order.getStatus() != null ? safe(order.getStatus().getLabel()) : "-");

        // ✅ 완료처리에 사용한 이미지(배송 증빙 이미지) URL 목록
        List<String> deliveryImageUrls = List.of();
        if (order.getOrderImages() != null) {
            deliveryImageUrls = order.getDeliveryImages().stream()
                .map(OrderImage::getUrl)
                .filter(u -> u != null && !u.trim().isEmpty())
                .collect(Collectors.toList());
        }

        return DeliveryOrderSummaryRes.builder()
                .orderId(order.getId())
                .companyName(blankToDash(companyName))
                .requesterName(blankToDash(requesterName))
                .companyContact(blankToDash(contact))
                .companyAddress(blankToDash(companyAddr))
                .orderAddress(blankToDash(orderAddr))
                .productText(blankToDash(productText))
                .status(status)
                .statusLabel(statusLabel)
                .deliveryImageUrls(deliveryImageUrls)
                .build();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String blankToDash(String s) {
        return isBlank(s) ? "-" : s.trim();
    }
}