package com.dev.HiddenBATHAuto.orderExcelUpload.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelDeliveryMethodOptionResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelLookupOptionsResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelOptionDto;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelDeliveryMethodRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelMemberRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelTeamCategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderExcelUploadLookupService {

    private static final Long PRODUCTION_TEAM_ID = 2L;
    private static final String DELIVERY_TEAM_NAME = "배송팀";

    private final OrderExcelDeliveryMethodRepository deliveryMethodRepository;
    private final OrderExcelTeamCategoryRepository teamCategoryRepository;
    private final OrderExcelMemberRepository memberRepository;

    public OrderExcelLookupOptionsResponse getOptions() {
        OrderExcelLookupOptionsResponse response = new OrderExcelLookupOptionsResponse();
        response.setDeliveryMethods(getDeliveryMethods());
        response.setProductionCategories(getProductionCategories());
        response.setManagers(getManagers());
        response.setDeliveryHandlers(getDeliveryHandlers());
        return response;
    }

    public List<OrderExcelDeliveryMethodOptionResponse> getDeliveryMethods() {
        return deliveryMethodRepository.findAllByOrderByMethodNameAsc()
                .stream()
                .map(this::toDeliveryMethodOption)
                .collect(Collectors.toList());
    }

    private List<OrderExcelOptionDto> getProductionCategories() {
        return teamCategoryRepository.findByTeam_IdOrderByNameAsc(PRODUCTION_TEAM_ID)
                .stream()
                .map(this::toCategoryOption)
                .collect(Collectors.toList());
    }

    private List<OrderExcelOptionDto> getManagers() {
        return memberRepository.findByRolesOrderByName(List.of(MemberRole.ADMIN, MemberRole.MANAGEMENT))
                .stream()
                .map(member -> new OrderExcelOptionDto(member.getId(), nullToDash(member.getName())))
                .collect(Collectors.toList());
    }

    private List<OrderExcelOptionDto> getDeliveryHandlers() {
        return memberRepository.findByTeamNameOrderByName(DELIVERY_TEAM_NAME)
                .stream()
                .map(member -> new OrderExcelOptionDto(member.getId(), nullToDash(member.getName())))
                .collect(Collectors.toList());
    }

    private OrderExcelDeliveryMethodOptionResponse toDeliveryMethodOption(DeliveryMethod method) {
        String name = method.getMethodName() == null ? "" : method.getMethodName().trim();
        String normalized = name.replace(" ", "");
        return new OrderExcelDeliveryMethodOptionResponse(
                method.getId(),
                name,
                method.getMethodPrice(),
                normalized.contains("직배송"),
                normalized.contains("현장배송")
        );
    }

    private OrderExcelOptionDto toCategoryOption(TeamCategory category) {
        return new OrderExcelOptionDto(category.getId(), category.getName());
    }

    private String nullToDash(String value) {
        return value == null || value.trim().isBlank() ? "-" : value.trim();
    }
}
