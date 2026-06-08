package com.dev.HiddenBATHAuto.service.productOrderAdd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderCompanyDeliveryAddressResponse;
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderCompanyOptionResponse;
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderCompanyOrdererInfoResponse;
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderDeliveryMethodResponse;
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderMemberOptionResponse;
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderSimpleOptionResponse;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.CompanyDeliveryAddress;
import com.dev.HiddenBATHAuto.model.auth.CompanyOrdererInfo;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.model.standard.StandardCategory;
import com.dev.HiddenBATHAuto.model.standard.StandardProductSeries;
import com.dev.HiddenBATHAuto.repository.auth.CompanyDeliveryAddressRepository;
import com.dev.HiddenBATHAuto.repository.auth.CompanyOrdererInfoRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.standard.StandardCategoryRepository;
import com.dev.HiddenBATHAuto.repository.standard.StandardProductSeriesRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductOrderAddQueryService {

    private static final Long PRODUCTION_TEAM_ID = 2L;
    private static final Long DELIVERY_TEAM_ID = 3L;

    private final MemberRepository memberRepository;
    private final TeamCategoryRepository teamCategoryRepository;
    private final StandardCategoryRepository standardCategoryRepository;
    private final StandardProductSeriesRepository standardProductSeriesRepository;
    private final CompanyDeliveryAddressRepository companyDeliveryAddressRepository;
    private final CompanyOrdererInfoRepository companyOrdererInfoRepository;
    private final DeliveryMethodRepository deliveryMethodRepository;

    public List<ProductOrderCompanyOptionResponse> searchCompanies(String keyword) {
        List<Member> reps = memberRepository.searchCompanyRepresentativeMembers(
                MemberRole.CUSTOMER_REPRESENTATIVE,
                safeKeyword(keyword),
                PageRequest.of(0, 50)
        );

        Map<Long, ProductOrderCompanyOptionResponse> result = new LinkedHashMap<>();

        for (Member rep : reps) {
            Company company = rep.getCompany();

            if (company == null || result.containsKey(company.getId())) {
                continue;
            }

            result.put(company.getId(), ProductOrderCompanyOptionResponse.builder()
                    .companyId(company.getId())
                    .companyName(company.getCompanyName())
                    .representativeName(trimToEmpty(rep.getName()))
                    .representativePhone(trimToEmpty(rep.getPhone()))
                    .joinedAt(company.getCreatedAt())
                    .address(buildCompanyAddress(company))
                    .zipCode(trimToEmpty(company.getZipCode()))
                    .doName(trimToEmpty(company.getDoName()))
                    .siName(trimToEmpty(company.getSiName()))
                    .guName(trimToEmpty(company.getGuName()))
                    .roadAddress(trimToEmpty(company.getRoadAddress()))
                    .detailAddress(trimToEmpty(company.getDetailAddress()))
                    .build());
        }

        return new ArrayList<>(result.values());
    }

    public List<ProductOrderCompanyDeliveryAddressResponse> getCompanyDeliveryAddresses(Long companyId) {
        List<CompanyDeliveryAddress> addresses =
                companyDeliveryAddressRepository.findByCompany_IdOrderByCreatedAtDescIdDesc(companyId);

        List<ProductOrderCompanyDeliveryAddressResponse> result = new ArrayList<>();

        for (CompanyDeliveryAddress address : addresses) {
            result.add(ProductOrderCompanyDeliveryAddressResponse.builder()
                    .id(address.getId())
                    .zipCode(trimToEmpty(address.getZipCode()))
                    .doName(trimToEmpty(address.getDoName()))
                    .siName(trimToEmpty(address.getSiName()))
                    .guName(trimToEmpty(address.getGuName()))
                    .roadAddress(trimToEmpty(address.getRoadAddress()))
                    .detailAddress(trimToEmpty(address.getDetailAddress()))
                    .address(buildDeliveryAddress(address))
                    .build());
        }

        return result;
    }

    public List<ProductOrderCompanyOrdererInfoResponse> getCompanyOrdererInfos(Long companyId) {
        List<CompanyOrdererInfo> infos = companyOrdererInfoRepository
                .findByCompany_IdOrderByCreatedAtDescIdDesc(companyId);

        List<ProductOrderCompanyOrdererInfoResponse> result = new ArrayList<>();

        for (CompanyOrdererInfo info : infos) {
            result.add(ProductOrderCompanyOrdererInfoResponse.builder()
                    .id(info.getId())
                    .ordererName(trimToEmpty(info.getOrdererName()))
                    .ordererPhone(trimToEmpty(resolveOrdererPhone(info)))
                    .createdAt(info.getCreatedAt())
                    .build());
        }

        return result;
    }

    public List<ProductOrderDeliveryMethodResponse> getDeliveryMethods() {
        List<DeliveryMethod> methods = deliveryMethodRepository.findAllByOrderByMethodNameAsc();
        List<ProductOrderDeliveryMethodResponse> result = new ArrayList<>();

        for (DeliveryMethod method : methods) {
            result.add(new ProductOrderDeliveryMethodResponse(
                    method.getId(),
                    trimToEmpty(method.getMethodName()),
                    method.getMethodPrice(),
                    isDirectDeliveryMethod(method)
            ));
        }

        return result;
    }

    public List<ProductOrderMemberOptionResponse> searchDeliveryHandlers(String keyword) {
        List<Member> members = memberRepository.searchMembersByTeamId(
                DELIVERY_TEAM_ID,
                safeKeyword(keyword),
                PageRequest.of(0, 50)
        );

        List<ProductOrderMemberOptionResponse> result = new ArrayList<>();

        for (Member member : members) {
            result.add(new ProductOrderMemberOptionResponse(
                    member.getId(),
                    member.getName(),
                    member.getUsername(),
                    member.getPhone(),
                    member.getCreatedAt()
            ));
        }

        return result;
    }

    public List<ProductOrderSimpleOptionResponse> getStandardCategories() {
        List<StandardCategory> categories = standardCategoryRepository.findAllByOrderByNameAsc();

        List<ProductOrderSimpleOptionResponse> result = new ArrayList<>();

        for (StandardCategory category : categories) {
            result.add(new ProductOrderSimpleOptionResponse(category.getId(), category.getName()));
        }

        return result;
    }

    public List<ProductOrderSimpleOptionResponse> getStandardSeries(Long categoryId) {
        List<StandardProductSeries> seriesList =
                standardProductSeriesRepository.findByCategory_IdOrderByNameAsc(categoryId);

        List<ProductOrderSimpleOptionResponse> result = new ArrayList<>();

        for (StandardProductSeries series : seriesList) {
            result.add(new ProductOrderSimpleOptionResponse(series.getId(), series.getName()));
        }

        return result;
    }

    public List<ProductOrderSimpleOptionResponse> getProductionCategories(String keyword) {
        List<TeamCategory> categories;

        if (keyword == null || keyword.isBlank()) {
            categories = teamCategoryRepository.findByTeam_IdOrderByNameAsc(PRODUCTION_TEAM_ID);
        } else {
            categories = teamCategoryRepository
                    .findByTeam_IdAndNameContainingIgnoreCaseOrderByNameAsc(PRODUCTION_TEAM_ID, keyword.trim());
        }

        List<ProductOrderSimpleOptionResponse> result = new ArrayList<>();

        for (TeamCategory category : categories) {
            result.add(new ProductOrderSimpleOptionResponse(category.getId(), category.getName()));
        }

        return result;
    }

    private boolean isDirectDeliveryMethod(DeliveryMethod method) {
        // ProductOrderDeliveryMethodResponse의 directDelivery 필드는 기존 이름을 유지하되,
        // 관리자 발주 화면에서는 '배송 담당자 지정 가능 여부'로 사용합니다.
        String name = trimToEmpty(method == null ? null : method.getMethodName()).replace(" ", "");
        return name.contains("직배송") || name.contains("현장배송") || name.contains("화물");
    }

    /**
     * 현재 올려주신 CompanyOrdererInfo에는 phone 컬럼 필드명이 phone으로 되어 있습니다.
     * 엔티티를 ordererPhone으로 변경하셨다면 이 메서드만 info.getOrdererPhone()으로 바꾸시면 됩니다.
     */
    private String resolveOrdererPhone(CompanyOrdererInfo info) {
        return info.getPhone();
    }

    private String safeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private String buildCompanyAddress(Company company) {
        return joinAddress(company.getRoadAddress(), company.getDetailAddress());
    }

    private String buildDeliveryAddress(CompanyDeliveryAddress address) {
        return joinAddress(address.getRoadAddress(), address.getDetailAddress());
    }

    private String joinAddress(String roadAddress, String detailAddress) {
        String road = trimToEmpty(roadAddress);
        String detail = trimToEmpty(detailAddress);

        if (!road.isBlank() && !detail.isBlank()) {
            return road + " " + detail;
        }

        if (!road.isBlank()) {
            return road;
        }

        return detail;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
