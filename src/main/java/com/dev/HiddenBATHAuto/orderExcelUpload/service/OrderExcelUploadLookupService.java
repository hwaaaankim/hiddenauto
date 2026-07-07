package com.dev.HiddenBATHAuto.orderExcelUpload.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelDeliveryMethodOptionResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelLookupOptionsResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelOptionDto;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelAmountItemMasterRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelDeliveryMethodRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelMemberRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelTeamCategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderExcelUploadLookupService {

    private static final String PRODUCTION_TEAM_NAME = "생산팀";
    private static final Long BATHROOM_GOODS_DISPATCH_TEAM_CATEGORY_ID = 12L;
    private static final String DELIVERY_TEAM_NAME = "배송팀";

    private final OrderExcelDeliveryMethodRepository deliveryMethodRepository;
    private final OrderExcelTeamCategoryRepository teamCategoryRepository;
    private final OrderExcelMemberRepository memberRepository;
    private final OrderExcelAmountItemMasterRepository amountItemMasterRepository;

    public OrderExcelLookupOptionsResponse getOptions() {
        OrderExcelLookupOptionsResponse response = new OrderExcelLookupOptionsResponse();
        response.setDeliveryMethods(getDeliveryMethods());
        response.setProductionCategories(getProductionCategories());
        response.setMiddleCategories(getMiddleCategories());
        response.setMiddleCategoriesByCategory(getMiddleCategoriesByCategory(response.getProductionCategories()));
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
        List<OrderExcelOptionDto> result = new ArrayList<>(teamCategoryRepository.findByTeam_NameOrderByNameAsc(PRODUCTION_TEAM_NAME)
                .stream()
                .map(this::toCategoryOption)
                .collect(Collectors.toList()));

        teamCategoryRepository.findById(BATHROOM_GOODS_DISPATCH_TEAM_CATEGORY_ID)
                .map(this::toCategoryOption)
                .ifPresent(option -> {
                    boolean alreadyExists = result.stream()
                            .anyMatch(item -> item.getId() != null && item.getId().equals(option.getId()));
                    if (!alreadyExists) {
                        result.add(option);
                    }
                });

        return result;
    }

    private List<OrderExcelOptionDto> getMiddleCategories() {
        return amountItemMasterRepository.findDistinctMiddleCategoryNames()
                .stream()
                .map(name -> new OrderExcelOptionDto(null, normalizeMiddleCategory(name)))
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<String, List<OrderExcelOptionDto>> getMiddleCategoriesByCategory(List<OrderExcelOptionDto> categories) {
        Map<String, List<OrderExcelOptionDto>> result = new LinkedHashMap<>();
        if (categories == null) {
            return result;
        }

        for (OrderExcelOptionDto category : categories) {
            String categoryName = category.getName();
            if (categoryName == null || categoryName.trim().isBlank()) {
                continue;
            }
            List<OrderExcelOptionDto> middleList = amountItemMasterRepository
                    .findDistinctMiddleCategoryNamesByCategoryName(categoryName.replaceAll("\\s+", ""))
                    .stream()
                    .map(name -> new OrderExcelOptionDto(null, normalizeMiddleCategory(name)))
                    .distinct()
                    .collect(Collectors.toList());
            result.put(categoryName, middleList);
        }
        return result;
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
                normalized.contains("현장배송"),
                normalized.contains("화물"),
                normalized.contains("방문"),
                normalized.contains("택배")
        );
    }

    private OrderExcelOptionDto toCategoryOption(TeamCategory category) {
        return new OrderExcelOptionDto(category.getId(), category.getName());
    }

    private String normalizeMiddleCategory(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || "X".equalsIgnoreCase(normalized)) {
            return "분류없음";
        }
        return normalized;
    }

    private String nullToDash(String value) {
        return value == null || value.trim().isBlank() ? "-" : value.trim();
    }
}
