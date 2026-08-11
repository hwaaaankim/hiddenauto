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
		// 기존 기능 유지: 현재 노출 대상 팝업만 정렬해 전달합니다.
		List<Popup> popups = popupManagerService.listActiveOrderByIndex();
		model.addAttribute("popups", popups);
		return "front/index";
	}
}
