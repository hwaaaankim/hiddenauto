package com.dev.HiddenBATHAuto.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.service.ExcelConvertService;
import com.dev.HiddenBATHAuto.service.standard.StandardUploadService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/excel")
@RequiredArgsConstructor
public class ExcelAPIController {

	private final ExcelConvertService excelConvertService;
	private final StandardUploadService standardUploadService;

    @PostMapping("/convert")
    public ResponseEntity<Resource> convertExcel(@RequestParam("file") MultipartFile file) throws IOException {
        File convertedFile = excelConvertService.processAndGenerate(file);

        InputStreamResource resource = new InputStreamResource(new FileInputStream(convertedFile));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + URLEncoder.encode(convertedFile.getName(), StandardCharsets.UTF_8) + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(convertedFile.length())
                .body(resource);
    }
    
    @PostMapping("/base")
    public ResponseEntity<?> uploadBaseData(@RequestParam("file") MultipartFile file) {
        try {
            standardUploadService.uploadBaseInfo(file);
            return ResponseEntity.ok("✅ 기본 데이터 업로드 완료");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("❌ 업로드 실패: " + e.getMessage());
        }
    }
    
    @PostMapping("/product")
    public ResponseEntity<?> uploadProductData(@RequestParam("file") MultipartFile file) {
        try {
            standardUploadService.uploadProductInfo(file);
            return ResponseEntity.ok("✅ 제품 데이터 업로드 완료");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("❌ 제품 데이터 업로드 실패: " + e.getMessage());
        }
    }
}
