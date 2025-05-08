package com.dev.HiddenBATHAuto.controller.page;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/management")
public class ManagementController {

	@GetMapping("/admin-only")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String adminOnlyInManagement() {
        return "관리자만 접근 가능한 페이지입니다.";
    }

    // 관리자 또는 매니저 모두 접근 가능
    @GetMapping("/shared")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGEMENT')")
    public String adminOrManagerAccess() {
        return "관리자 또는 매니저가 접근 가능한 페이지입니다.";
    }
	
	@GetMapping("/test")
	@ResponseBody
	public String managementTest() {
		
		return "management";
	}
}
