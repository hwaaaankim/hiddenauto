package com.dev.HiddenBATHAuto.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.service.auth.MemberService;
import com.dev.HiddenBATHAuto.service.calculate.excel.LowCalculateExcelService;
import com.dev.HiddenBATHAuto.service.calculate.excel.MarbleLowCalculateExcelService;
import com.dev.HiddenBATHAuto.service.nonstandard.ExcelUploadService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class APIController {

	@Autowired
	MemberService memberService;
	
	@Autowired
	ExcelUploadService excelUploadService;
	
	private final LowCalculateExcelService excelService;
	private final MarbleLowCalculateExcelService marbleExcelService;

    @PostMapping("/marbleLowExcelUpload")
    public ResponseEntity<String> marbleLowExcelUpload(@RequestParam("file") MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            marbleExcelService.uploadExcel(inputStream);
            return ResponseEntity.ok("✅ 마블 하부장 엑셀 업로드 및 DB 저장 완료");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("❌ 업로드 실패: " + e.getMessage());
        }
    }
	
	@PostMapping("/lowExcelUpload")
    public ResponseEntity<String> uploadExcel(@RequestParam("file") MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            excelService.uploadExcel(inputStream);
            return ResponseEntity.ok("✅ 엑셀 업로드 및 DB 저장 완료");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("❌ 업로드 실패: " + e.getMessage());
        }
    }
	
	@PostMapping("/join")
	@ResponseBody
	public String adminJoin(Member member) {
		memberService.insertMember(member);
		return "success";
	}
	
	@PostMapping("/resetExcelUpload")
	@ResponseBody
	public List<String> addExcelUpload(MultipartFile file, Model model) throws IOException {
	    return excelUploadService.uploadExcel(file);
	}
	
}
