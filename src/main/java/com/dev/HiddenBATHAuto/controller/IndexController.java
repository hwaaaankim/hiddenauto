package com.dev.HiddenBATHAuto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

	@GetMapping({"/index", "/", ""})
	public String index() {
		
		return "front/index";
	}
	
	@GetMapping("/signin")
	public String signin() {
		
		return "front/common/signin";
	}
}
