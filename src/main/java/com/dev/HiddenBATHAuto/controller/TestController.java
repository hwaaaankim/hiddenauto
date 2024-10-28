package com.dev.HiddenBATHAuto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/test")
@Controller
public class TestController {

    @GetMapping("/admin")
    public String testAdmin(Model model) {
        return "front/test/admin";
    }
    
    @GetMapping("/member")
    public String testMember(Model model) {
        return "front/test/member";
    }
}
