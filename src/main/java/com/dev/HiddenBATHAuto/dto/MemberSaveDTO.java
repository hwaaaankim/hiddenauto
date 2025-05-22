package com.dev.HiddenBATHAuto.dto;

import com.dev.HiddenBATHAuto.model.auth.MemberRole;

import lombok.Data;

@Data
public class MemberSaveDTO {

	private String username;
    private String password;
    private String name;
    private String phone;
    private String email;

    private MemberRole role;

    private Long teamId;
    private Long teamCategoryId; // 생산팀일 경우만 사용자 입력

    private String regionJson;
}
