package com.dev.HiddenBATHAuto.service.auth;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberValidationService {

    private final MemberRepository memberRepository;

    public boolean isUsernameDuplicate(String username) {
        return memberRepository.existsByUsername(username);
    }

    public boolean isPhoneDuplicate(String phone) {
        return memberRepository.existsByPhone(phone);
    }
}
