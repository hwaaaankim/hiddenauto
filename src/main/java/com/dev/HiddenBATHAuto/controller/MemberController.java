package com.dev.HiddenBATHAuto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MemberController {

	@GetMapping("/account")
	public String profile() {
		
		return "front/member/account";
	}

	@GetMapping("/memberList")
	public String memberList() {
		
		return "front/member/memberList";
	}
	
	@GetMapping("/memberManager")
	public String memberManager() {
		
		return "front/member/memberManager";
	}
}
