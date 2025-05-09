package com.dev.HiddenBATHAuto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/error")
public class CustomErrorController {

	@GetMapping("/403")
    public String accessDeniedHtml(HttpServletRequest request, Model model) {
        model.addAttribute("userRole", request.getAttribute("userRole"));
        model.addAttribute("requestedUri", request.getAttribute("requestedUri"));
        return "error/403";
    }

    @GetMapping("/404")
    public String error404() {
        return "error/404";
    }

    @GetMapping("/500")
    public String error500() {
        return "error/500";
    }
}
