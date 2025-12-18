package com.dev.HiddenBATHAuto.controller.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Controller
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ExcelConvertAPIController {

    private final CompanyExcelConvertService excelConvertService;
    private static final Logger log = LoggerFactory.getLogger(ExcelConvertAPIController.class);	

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
