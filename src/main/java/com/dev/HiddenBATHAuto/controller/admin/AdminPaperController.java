package com.dev.HiddenBATHAuto.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPaperController {

	@GetMapping("/invoice")
	public String invoice() {
		
		return "administration/paper/invoice";
	}
}
