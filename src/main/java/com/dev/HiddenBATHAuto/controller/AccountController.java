package com.dev.HiddenBATHAuto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccountController {

	@GetMapping("/signin")
	public String signin() {
		
		return "front/account/signin";
	}
	
	@GetMapping("/signup")
	public String signup() {
		
		return "front/account/signup";
	}
}
