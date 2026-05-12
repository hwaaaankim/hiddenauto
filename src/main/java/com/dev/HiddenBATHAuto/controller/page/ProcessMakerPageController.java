package com.dev.HiddenBATHAuto.controller.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class ProcessMakerPageController {

    @GetMapping("/process/processMaker")
    public String processMakerPage() {
        return "administration/process/processMaker";
    }
    
    @GetMapping("/process/processTest")
    public String processTestPage() {
        return "administration/process/processTest";
    }
}