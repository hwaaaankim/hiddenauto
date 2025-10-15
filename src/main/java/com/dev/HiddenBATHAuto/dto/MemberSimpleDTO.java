package com.dev.HiddenBATHAuto.dto;

import com.dev.HiddenBATHAuto.model.auth.Member;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MemberSimpleDTO {
    private Long id;
    private String name;      // 실명
    private String username;  // 로그인 ID 등

    public static MemberSimpleDTO from(Member m) {
        return new MemberSimpleDTO(m.getId(), m.getName(), m.getUsername());
    }
}