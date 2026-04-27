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
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderMemberOptionResponse;
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderSimpleOptionResponse;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.CompanyDeliveryAddress;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.standard.StandardCategory;
import com.dev.HiddenBATHAuto.model.standard.StandardProductSeries;
import com.dev.HiddenBATHAuto.repository.auth.CompanyDeliveryAddressRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
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
                    .representativeName(rep.getName())
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