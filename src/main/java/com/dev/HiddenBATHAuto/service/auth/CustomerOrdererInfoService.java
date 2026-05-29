package com.dev.HiddenBATHAuto.service.auth;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.auth.CustomerOrdererInfoDtos.OrdererInfoResponse;
import com.dev.HiddenBATHAuto.dto.auth.CustomerOrdererInfoDtos.SaveRequest;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.CompanyOrdererInfo;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.PrincipalDetails;
import com.dev.HiddenBATHAuto.repository.auth.CompanyOrdererInfoRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerOrdererInfoService {

    private final MemberRepository memberRepository;
    private final CompanyOrdererInfoRepository companyOrdererInfoRepository;

    @Transactional(readOnly = true)
    public List<OrdererInfoResponse> getOrdererInfosForCompany(Long companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("회사 정보가 없습니다.");
        }

        return companyOrdererInfoRepository.findByCompanyIdOrderByIdAsc(companyId)
                .stream()
                .map(OrdererInfoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrdererInfoResponse> getMyCompanyOrdererInfos(PrincipalDetails principal) {
        Company company = getLoginCompany(principal);

        return companyOrdererInfoRepository.findByCompanyIdOrderByIdAsc(company.getId())
                .stream()
                .map(OrdererInfoResponse::from)
                .toList();
    }

    @Transactional
    public OrdererInfoResponse createOrdererInfo(PrincipalDetails principal, SaveRequest request) {
        Company company = getLoginCompany(principal);

        String name = normalizeName(request != null ? request.getName() : null);
        String phone = normalizePhone(request != null ? request.getPhone() : null);

        CompanyOrdererInfo ordererInfo = new CompanyOrdererInfo();
        ordererInfo.setCompany(company);
        ordererInfo.setOrdererName(name);
        ordererInfo.setPhone(phone);
        ordererInfo.setCreatedAt(LocalDateTime.now());
        ordererInfo.setUpdatedAt(null);

        CompanyOrdererInfo saved = companyOrdererInfoRepository.save(ordererInfo);

        return OrdererInfoResponse.from(saved);
    }

    @Transactional
    public void deleteOrdererInfo(PrincipalDetails principal, Long ordererInfoId) {
        if (ordererInfoId == null || ordererInfoId <= 0) {
            throw new IllegalArgumentException("삭제할 주문자 정보가 올바르지 않습니다.");
        }

        Company company = getLoginCompany(principal);

        CompanyOrdererInfo target = companyOrdererInfoRepository
                .findByIdAndCompanyId(ordererInfoId, company.getId())
                .orElseThrow(() -> new IllegalArgumentException("삭제할 주문자 정보를 찾을 수 없습니다."));

        companyOrdererInfoRepository.delete(target);
    }

    private Company getLoginCompany(PrincipalDetails principal) {
        if (principal == null || principal.getMember() == null || principal.getMember().getId() == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }

        Member member = memberRepository.findById(principal.getMember().getId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Company company = member.getCompany();
        if (company == null || company.getId() == null) {
            throw new IllegalArgumentException("회사 정보가 없습니다.");
        }

        return company;
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("이름을 입력해 주세요.");
        }

        String normalized = name.trim();

        if (normalized.length() > 50) {
            throw new IllegalArgumentException("이름은 50자 이하로 입력해 주세요.");
        }

        return normalized;
    }

    private String normalizePhone(String phone) {
        String digits = onlyDigits(phone);

        if (digits.length() == 11) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7, 11);
        }

        if (digits.length() == 10) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 6) + "-" + digits.substring(6, 10);
        }

        throw new IllegalArgumentException("연락처는 숫자 10자리 또는 11자리로 입력해 주세요.");
    }

    private String onlyDigits(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("[^0-9]", "");
    }
}