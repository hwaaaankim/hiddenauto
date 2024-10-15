package com.dev.HiddenBATHAuto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UtilController {

	@GetMapping("/search")
	public String search() {
		
		return "front/util/search";
	}

}
