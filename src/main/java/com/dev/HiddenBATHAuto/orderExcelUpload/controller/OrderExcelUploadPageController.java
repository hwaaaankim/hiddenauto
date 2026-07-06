package com.dev.HiddenBATHAuto.orderExcelUpload.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OrderExcelUploadPageController {

    @GetMapping("/management/order-excel-upload")
    public String page() {
        return "administration/orderExcelUpload/orderExcelUpload";
    }
}
