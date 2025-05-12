package com.dev.HiddenBATHAuto.controller.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin")
public class AdminController {

	@GetMapping("/test")
	@ResponseBody
	public String adminTest() {
		
		return "admin";
	}
	
	@GetMapping("/index")
	public String index() {
		
		return "administration/index";
	}
	
	@GetMapping("/invoice")
	public String invoice() {
		
		return "administration/paper/invoice";
	}
	
	@GetMapping("/manufactureList")
	public String manufactureList() {
		return "administration/manufacture/list";
	}
	
	@GetMapping("/manufactureDetail")
	public String manufactureDetail() {
		return "administration/manufacture/detail";
	}
	
	@GetMapping("/manufacturePaper")
	public String manufacturePaper() {
		return "administration/manufacture/paper";
	}
}
