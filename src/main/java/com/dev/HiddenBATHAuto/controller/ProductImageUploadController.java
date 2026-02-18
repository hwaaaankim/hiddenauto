package com.dev.HiddenBATHAuto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.productUpload.ProductImageUploadReport;
import com.dev.HiddenBATHAuto.service.productUpload.ProductImageUploadService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ProductImageUploadController {

    private final ProductImageUploadService productImageUploadService;

    @GetMapping("/productImageUpload")
    public String productImageUploadPage(Model model) {
        // ✅ GET에서도 result를 항상 넣어두면(빈 리포트) 화면 쪽 null 이슈가 줄어듭니다.
        model.addAttribute("result", null);
        return "front/productImageUpload";
    }

    @PostMapping("/productImageUpload")
    public String handleUpload(
            @RequestParam(value = "standardZip", required = false) MultipartFile standardZip,
            @RequestParam(value = "seriesZip", required = false) MultipartFile seriesZip,
            @RequestParam(value = "productZip", required = false) MultipartFile productZip,
            Model model
    ) {
        ProductImageUploadReport report = productImageUploadService.process(standardZip, seriesZip, productZip);
        model.addAttribute("result", report);
        return "front/productImageUpload";
    }
}
