package com.dev.HiddenBATHAuto.controller.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import com.dev.HiddenBATHAuto.service.excel.CompanyExcelConvertService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ExcelConvertAPIController {

    private final CompanyExcelConvertService excelConvertService;

    /**
     * 업로드 형식(입력):
     * 0: 회사명
     * 1: 사업자등록번호(000-00-00000)
     * 2: 대표자명
     * 3: 주소(자유형식)
     * 4: tel
     * 5: phone
     * 6: email
     *
     * 출력(다운로드):
     * 0: id(사업자번호 하이픈 제거)
     * 1: 회사명
     * 2: 대표자명(비었으면 '익명')
     * 3: 사업자등록번호(하이픈 포함 000-00-00000)
     * 4~9: 우편번호 / 도 / 시 / 구 / 도로명 / 상세주소
     * 10: tel
     * 11: phone
     * 12: email
     */
    @PostMapping(value = "/address/convert-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<Resource> convertExcel(@RequestParam("file") MultipartFile file) {
        try {
            byte[] bytes = excelConvertService.convertToExcelBytes(file);

            String filename = "converted_address_" + LocalDate.now() + ".xlsx";
            String encoded = UriUtils.encode(filename, StandardCharsets.UTF_8);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentLength(bytes.length);
            headers.setContentDisposition(
                    ContentDisposition.attachment().filename(encoded, StandardCharsets.UTF_8).build());

            return new ResponseEntity<>(new ByteArrayResource(bytes), headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Excel 변환 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(new ByteArrayResource(("변환 실패: " + e.getMessage()).getBytes(StandardCharsets.UTF_8)));
        }
    }
}
