package com.dev.HiddenBATHAuto.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.MemberExcelUploadResult;
import com.dev.HiddenBATHAuto.utils.MemberExcelUploadService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/member-excel")
public class AdminMemberExcelApiController {

    private final MemberExcelUploadService memberExcelUploadService;

    @PostMapping("/upload")
    public ResponseEntity<MemberExcelUploadResult> upload(@RequestParam("file") MultipartFile file) {
        MemberExcelUploadResult result = memberExcelUploadService.uploadAndRegister(file);
        return ResponseEntity.ok(result);
    }
}