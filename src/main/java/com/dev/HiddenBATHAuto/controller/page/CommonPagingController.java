package com.dev.HiddenBATHAuto.controller.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/common")
public class CommonPagingController {

	@GetMapping("/main")
	public String commonMaing() {
		
		return "administration/index";
	}
}
