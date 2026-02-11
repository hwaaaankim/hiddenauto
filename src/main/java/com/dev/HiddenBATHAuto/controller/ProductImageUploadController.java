package com.dev.HiddenBATHAuto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.productUpload.ProductImageUploadReport;
import com.dev.HiddenBATHAuto.service.productUpload.ProductImageUploadService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping
public class ProductImageUploadController {

    private final ProductImageUploadService productImageUploadService;

    @GetMapping("/productImageUpload")
    public String productImageUploadPage() {
        return "front/productImageUpload";
    }

    @PostMapping("/productImageUpload")
    public String handleUpload(
            MultipartFile standardZip,
            MultipartFile seriesZip,
            MultipartFile productZip,
            Model model
    ) {
        ProductImageUploadReport report = productImageUploadService.process(standardZip, seriesZip, productZip);
        model.addAttribute("result", report);
        return "front/productImageUpload";
    }
}