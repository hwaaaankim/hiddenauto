package com.dev.HiddenBATHAuto.orderExcelUpload.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.CompanyDeliveryAddress;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelCompanyAddressLookupResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelCompanyAddressOptionResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelDeliveryMethodOptionResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelLookupOptionsResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelOptionDto;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelOrderStatusOptionResponse;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelAmountItemMasterRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelCompanyDeliveryAddressRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelCompanyRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelDeliveryMethodRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelMemberRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelTeamCategoryRepository;
import com.dev.HiddenBATHAuto.orderExcelUpload.support.OrderExcelAddressValidationResult;
import com.dev.HiddenBATHAuto.orderExcelUpload.support.OrderExcelAddressValidator;

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
    private final OrderExcelCompanyRepository companyRepository;
    private final OrderExcelCompanyDeliveryAddressRepository companyDeliveryAddressRepository;
    private final OrderExcelAddressValidator addressValidator;

    public OrderExcelLookupOptionsResponse getOptions() {
        OrderExcelLookupOptionsResponse response = new OrderExcelLookupOptionsResponse();
        response.setDeliveryMethods(getDeliveryMethods());
        response.setProductionCategories(getProductionCategories());
        response.setOrderStatuses(getOrderStatuses());
        response.setMiddleCategories(getMiddleCategories());
        response.setMiddleCategoriesByCategory(getMiddleCategoriesByCategory(response.getProductionCategories()));
        response.setManagers(getManagers());
        response.setDeliveryHandlers(getDeliveryHandlers());
        return response;
    }

    /**
     * 엑셀 미리보기의 업체 ID/사업자등록번호/거래처명 중 신뢰할 수 있는 값으로 업체를 확정한 뒤,
     * 업체 기본주소와 tb_company_delivery_address의 추가 배송지를 같은 주소 DTO 형식으로 반환합니다.
     */
    public OrderExcelCompanyAddressLookupResponse getCompanyAddresses(
            Long companyId,
            String businessNumber,
            String companyName
    ) {
        Company company = resolveCompany(companyId, businessNumber, companyName);
        OrderExcelCompanyAddressLookupResponse response = new OrderExcelCompanyAddressLookupResponse();
        response.setCompanyId(company.getId());
        response.setCompanyName(safe(company.getCompanyName()));
        response.setBusinessNumber(normalizeBusinessNumber(company.getBusinessNumber()));

        memberRepository.findByCompany_IdAndRoleOrderByIdAsc(company.getId(), MemberRole.CUSTOMER_REPRESENTATIVE)
                .stream()
                .findFirst()
                .ifPresent(member -> {
                    response.setRequestedByMemberId(member.getId());
                    response.setRequestedByName(safe(member.getName()));
                });

        List<OrderExcelCompanyAddressOptionResponse> addresses = new ArrayList<>();
        if (hasPrimaryAddress(company)) {
            addresses.add(toPrimaryAddress(company));
        }

        List<CompanyDeliveryAddress> additionalAddresses = companyDeliveryAddressRepository
                .findByCompany_IdOrderByIdAsc(company.getId());
        for (int i = 0; i < additionalAddresses.size(); i++) {
            addresses.add(toAdditionalAddress(additionalAddresses.get(i), i + 1));
        }

        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("해당 업체에 등록된 기본주소 또는 추가 배송지가 없습니다.");
        }

        response.setAddresses(addresses);
        return response;
    }

    public List<OrderExcelDeliveryMethodOptionResponse> getDeliveryMethods() {
        return deliveryMethodRepository.findAllByOrderByMethodNameAsc()
                .stream()
                .map(this::toDeliveryMethodOption)
                .collect(Collectors.toList());
    }

    private List<OrderExcelOrderStatusOptionResponse> getOrderStatuses() {
        return Arrays.stream(OrderStatus.values())
                .map(status -> new OrderExcelOrderStatusOptionResponse(status.name(), status.getLabel()))
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

    private Company resolveCompany(Long companyId, String businessNumber, String companyName) {
        if (companyId != null && companyId > 0) {
            return companyRepository.findById(companyId)
                    .orElseThrow(() -> new IllegalArgumentException("선택한 업체를 찾을 수 없습니다."));
        }

        String normalizedBusinessNumber = normalizeBusinessNumber(businessNumber);
        if (!normalizedBusinessNumber.isBlank()) {
            if (!normalizedBusinessNumber.matches("\\d{10}")) {
                throw new IllegalArgumentException("사업자등록번호는 숫자 10자리여야 합니다.");
            }
            return companyRepository.findByBusinessNumber(normalizedBusinessNumber)
                    .orElseThrow(() -> new IllegalArgumentException("사업자등록번호로 업체를 찾을 수 없습니다: " + normalizedBusinessNumber));
        }

        String normalizedCompanyName = safe(companyName);
        if (normalizedCompanyName.isBlank()) {
            throw new IllegalArgumentException("등록주소지를 조회할 업체 정보가 없습니다.");
        }

        List<Company> companies = companyRepository.findByCompanyName(normalizedCompanyName);
        if (companies.isEmpty()) {
            companies = companyRepository.findByCompanyNameWithoutSpaces(normalizedCompanyName.replaceAll("\\s+", ""));
        }
        if (companies.isEmpty()) {
            throw new IllegalArgumentException("거래처명으로 업체를 찾을 수 없습니다: " + normalizedCompanyName);
        }
        if (companies.size() > 1) {
            throw new IllegalArgumentException("동일한 거래처명이 여러 건입니다. 사업자등록번호를 확인해 주세요: " + normalizedCompanyName);
        }
        return companies.get(0);
    }

    private boolean hasPrimaryAddress(Company company) {
        return !firstNonBlank(
                company.getZipCode(),
                company.getDoName(),
                company.getSiName(),
                company.getGuName(),
                company.getRoadAddress(),
                company.getJibunAddress(),
                company.getOriginAddress(),
                company.getDetailAddress()
        ).isBlank();
    }

    private OrderExcelCompanyAddressOptionResponse toPrimaryAddress(Company company) {
        String roadAddress = firstNonBlank(company.getRoadAddress(), company.getOriginAddress(), company.getJibunAddress());
        OrderExcelCompanyAddressOptionResponse response = new OrderExcelCompanyAddressOptionResponse();
        response.setSourceType("PRIMARY");
        response.setAddressId(company.getId());
        response.setLabel("업체 기본주소");
        response.setZipCode(safe(company.getZipCode()));
        response.setDoName(safe(company.getDoName()));
        response.setSiName(safe(company.getSiName()));
        response.setGuName(safe(company.getGuName()));
        response.setRoadAddress(roadAddress);
        response.setJibunAddress(safe(company.getJibunAddress()));
        response.setOriginAddress(safe(company.getOriginAddress()));
        response.setDetailAddress(safe(company.getDetailAddress()));
        response.setFullAddress(buildFullAddress(company.getZipCode(), roadAddress, company.getDetailAddress()));
        applyAddressValidation(response);
        return response;
    }

    private OrderExcelCompanyAddressOptionResponse toAdditionalAddress(CompanyDeliveryAddress address, int sequence) {
        OrderExcelCompanyAddressOptionResponse response = new OrderExcelCompanyAddressOptionResponse();
        response.setSourceType("DELIVERY");
        response.setAddressId(address.getId());
        response.setLabel("추가 배송지 " + sequence);
        response.setZipCode(safe(address.getZipCode()));
        response.setDoName(safe(address.getDoName()));
        response.setSiName(safe(address.getSiName()));
        response.setGuName(safe(address.getGuName()));
        response.setRoadAddress(safe(address.getRoadAddress()));
        response.setJibunAddress("");
        response.setOriginAddress("");
        response.setDetailAddress(safe(address.getDetailAddress()));
        response.setFullAddress(buildFullAddress(address.getZipCode(), address.getRoadAddress(), address.getDetailAddress()));
        applyAddressValidation(response);
        return response;
    }

    private void applyAddressValidation(OrderExcelCompanyAddressOptionResponse address) {
        OrderExcelAddressValidationResult validation = addressValidator.validate(
                address.getLabel(),
                address.getZipCode(),
                address.getDoName(),
                address.getSiName(),
                address.getGuName(),
                address.getRoadAddress(),
                address.getJibunAddress(),
                address.getOriginAddress()
        );
        address.setValid(validation.isValid());
        address.setValidationMessage(validation.getMessage());
    }

    private String buildFullAddress(String zipCode, String roadAddress, String detailAddress) {
        List<String> parts = new ArrayList<>();
        String zip = safe(zipCode);
        if (!zip.isBlank()) {
            parts.add("(" + zip + ")");
        }
        if (!safe(roadAddress).isBlank()) {
            parts.add(safe(roadAddress));
        }
        if (!safe(detailAddress).isBlank()) {
            parts.add(safe(detailAddress));
        }
        return String.join(" ", parts);
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

    private String normalizeBusinessNumber(String value) {
        return safe(value).replaceAll("[^0-9]", "");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String normalized = safe(value);
            if (!normalized.isBlank() && !"-".equals(normalized)) {
                return normalized;
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String nullToDash(String value) {
        return value == null || value.trim().isBlank() ? "-" : value.trim();
    }
}
