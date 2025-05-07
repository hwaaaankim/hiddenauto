package com.dev.HiddenBATHAuto.controller.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/management")
public class ManagementController {

	@GetMapping("/test")
	@ResponseBody
	public String managementTest() {
		
		return "success";
	}
}
