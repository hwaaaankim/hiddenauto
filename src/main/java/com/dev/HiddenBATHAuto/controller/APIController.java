package com.dev.HiddenBATHAuto.controller;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.dev.HiddenBATHAuto.service.calculate.excel.FlapExcelUploadService;
import com.dev.HiddenBATHAuto.service.calculate.excel.LowCalculateExcelService;
import com.dev.HiddenBATHAuto.service.calculate.excel.MarbleLowCalculateExcelService;
import com.dev.HiddenBATHAuto.service.calculate.excel.MirrorStandardPriceExcelService;
import com.dev.HiddenBATHAuto.service.calculate.excel.MirrorUnstandardExcelUploadService;
import com.dev.HiddenBATHAuto.service.calculate.excel.SlideExcelUploadService;
import com.dev.HiddenBATHAuto.service.calculate.excel.TopExcelUploadService;
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
	
    private final TopExcelUploadService topExcelUploadService;
	private final LowCalculateExcelService excelService;
	private final MarbleLowCalculateExcelService marbleExcelService;
	private final FlapExcelUploadService flapExcelUploadService;
	private final SlideExcelUploadService slideExcelUplaodService;
	private final MirrorStandardPriceExcelService mirrorStandardPriceExcelService;
	private final MirrorUnstandardExcelUploadService mirrorSeriesExcelUploadService;

	@PostMapping("/mirrorSeriesExcelUpload")
	public ResponseEntity<String> uploadMirrorSeriesExcel(@RequestParam("file") MultipartFile file) {
	    try {
	        mirrorSeriesExcelUploadService.uploadExcel(file);
	        return ResponseEntity.ok("✅ 거울 시리즈 엑셀 업로드 성공");
	    } catch (Exception e) {
	        e.printStackTrace();
	        return ResponseEntity.status(500).body("❌ 업로드 실패: " + e.getMessage());
	    }
	}
	
    @PostMapping("/mirrorStandardExcelUpload")
    public ResponseEntity<String> upload(@RequestParam("file") MultipartFile file) {
        try {
            mirrorStandardPriceExcelService.uploadStandardPriceExcel(file);
            return ResponseEntity.ok("✅ 거울 규격 가격표 업로드 성공");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("❌ 업로드 실패: " + e.getMessage());
        }
    }
	
	@PostMapping("/slideExcelUpload")
	public ResponseEntity<Map<String, Object>> uploadSlideExcel(@RequestParam("file") MultipartFile file) {
	    Map<String, Object> result = new HashMap<>();
	    try {
	    	slideExcelUplaodService.uploadSlideExcel(file);
	        result.put("success", true);
	        result.put("message", "✅ 슬라이드장 엑셀 업로드 및 DB 저장 완료");
	    } catch (Exception e) {
	        e.printStackTrace();
	        result.put("success", false);
	        result.put("message", "❌ 업로드 실패: " + e.getMessage());
	    }

	    return ResponseEntity.ok(result);
	}
	
	@PostMapping("/flapExcelUpload")
	public ResponseEntity<Map<String, Object>> uploadFlapExcel(@RequestParam("file") MultipartFile file) {
	    Map<String, Object> result = new HashMap<>();
	    try {
	        flapExcelUploadService.uploadFlapExcel(file);
	        result.put("success", true);
	        result.put("message", "✅ 플랩장 엑셀 업로드 및 DB 저장 완료");
	    } catch (Exception e) {
	        e.printStackTrace();
	        result.put("success", false);
	        result.put("message", "❌ 업로드 실패: " + e.getMessage());
	    }

	    return ResponseEntity.ok(result);
	}
	
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
	
    @PostMapping("/topExcelUpload")
    public ResponseEntity<Map<String, Object>> uploadTopExcel(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            topExcelUploadService.uploadTopExcel(file);
            result.put("success", true);
            result.put("message", "엑셀 업로드가 성공적으로 완료되었습니다.");
        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "엑셀 파일 처리 중 오류가 발생했습니다: " + e.getMessage());
        }

        return ResponseEntity.ok(result);
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
