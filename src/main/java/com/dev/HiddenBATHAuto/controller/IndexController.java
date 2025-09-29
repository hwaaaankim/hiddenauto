package com.dev.HiddenBATHAuto.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.dev.HiddenBATHAuto.model.manager.Popup;
import com.dev.HiddenBATHAuto.service.manager.PopupManagerService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class IndexController {

	private final PopupManagerService popupManagerService;

	@GetMapping({ "/index", "/", "" })
	public String index(Model model) {
		// ✅ 현재 노출 대상만 dispOrder ASC, createdAt DESC 로 정렬해 전달
		List<Popup> popups = popupManagerService.listActiveOrderByIndex();
		model.addAttribute("popups", popups);
		return "front/index";
	}

}
