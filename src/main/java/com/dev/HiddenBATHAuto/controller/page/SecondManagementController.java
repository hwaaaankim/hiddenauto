package com.dev.HiddenBATHAuto.controller.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.RequiredArgsConstructor;


@Controller
@RequestMapping("/management")
@RequiredArgsConstructor
public class SecondManagementController {

    @GetMapping("/productOrderAdd")
    public String productOrderAdd() {
        return "administration/management/order/insert/productOrderAdd.html";
    }
}