package com.dev.HiddenBATHAuto.controller.api;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.dto.MemberSimpleDTO;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CompanyMemberApiController {

    private final MemberRepository memberRepository;

    @GetMapping("/api/companies/{companyId}/members")
    public List<MemberSimpleDTO> getCompanyMembers(@PathVariable Long companyId) {
        return memberRepository.findByCompany_Id(companyId)
                .stream()
                .map(MemberSimpleDTO::from)
                .collect(Collectors.toList());
    }
}