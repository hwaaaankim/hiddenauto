package com.dev.HiddenBATHAuto.service.excel;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.constant.KakaoAddressClient;
import com.dev.HiddenBATHAuto.dto.excel.KakaoDocument;
import com.dev.HiddenBATHAuto.dto.excel.KakaoKeywordDoc;
import com.dev.HiddenBATHAuto.dto.excel.KakaoKeywordResponse;
import com.dev.HiddenBATHAuto.dto.excel.KakaoResponse;
import com.dev.HiddenBATHAuto.utils.AddressNormalizer;
import com.dev.HiddenBATHAuto.utils.AddressPreprocessor;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompanyExcelConvertService {

    private final KakaoAddressClient kakaoAddressClient;

    /**
     * 업로드 엑셀 → 변환 엑셀 바이트
     */
    public byte[] convertToExcelBytes(MultipartFile file) throws Exception {
        try (InputStream in = file.getInputStream();
             Workbook inWb = new XSSFWorkbook(in);
             SXSSFWorkbook outWb = new SXSSFWorkbook(200);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet inSheet = inWb.getSheetAt(0);
            Sheet outSheet = outWb.createSheet("converted");

            SXSSFSheet sx = (SXSSFSheet) outSheet;
            sx.trackAllColumnsForAutoSizing();

            // ★ 회사명 컬럼을 2개로 분리: 회사명(원본), 회사명(지역삭제)
            //    id, 회사명(원본), 회사명(지역삭제), 대표자명, ...
            final String[] headers = {
                    "id(사업자번호숫자)", "회사명(원본)", "회사명(지역삭제)", "대표자명", "사업자등록번호(하이픈)",
                    "우편번호", "도", "시", "구", "도로명", "지번주소", "상세주소",
                    "tel", "phone", "email"
            };
            writeHeader(outSheet, headers);

            DataFormatter fmt = new DataFormatter();
            int outRowIdx = 1;

            Iterator<Row> it = inSheet.iterator();
            if (it.hasNext()) it.next(); // 입력 헤더 스킵

            while (it.hasNext()) {
                Row row = it.next();

                String companyNameIn = getCell(row, 0, fmt);
                String brnDashedIn   = getCell(row, 1, fmt);
                String ceoNameIn     = getCell(row, 2, fmt);
                String rawAddress    = getCell(row, 3, fmt);
                String telIn         = getCell(row, 4, fmt);
                String phoneIn       = getCell(row, 5, fmt);
                String emailIn       = getCell(row, 6, fmt);

                // ===== 회사명 처리 =====
                // 회사명(원본)
                String companyOriginalOut = orEopseum(companyNameIn);
                // 회사명(지역삭제) : 슬래시가 있는 경우, 슬래시 포함 앞부분 제거
                String companyRegionRemoved = stripRegionPrefix(companyNameIn);
                String companyRegionRemovedOut = orEopseum(companyRegionRemoved);

                // ===== 사업자번호 처리 =====
                String brnDigits    = onlyDigits(brnDashedIn);
                String brnDashedFmt = formatBrnDashed(brnDigits);

                String ceoOut   = orDefault(ceoNameIn, "익명");
                String telOut   = orEopseum(telIn);
                String phoneOut = orEopseum(phoneIn);
                String emailOut = orDefault(emailIn, "이메일없음");

                // ===== 주소 전처리 =====
                String cleaned     = AddressPreprocessor.clean(rawAddress);
                String parenDetail = AddressPreprocessor.extractParenDetail(cleaned);
                String stripped    = AddressPreprocessor.stripParen(cleaned);
                String extraDetail = AddressPreprocessor.extractCommaTailDetail(stripped);
                String baseQuery   = AddressPreprocessor.stripAfterComma(stripped);

                String q1    = AddressPreprocessor.normalizeRoadSpacing(baseQuery);
                String noise = AddressPreprocessor.extractNoiseToDetail(q1);
                String q2    = AddressPreprocessor.stripNoise(q1);
                String q3    = AddressPreprocessor.ensureProvince(q2);

                // ===== 카카오 검색 =====
                KakaoResponse resp = kakaoAddressClient.searchAddress(q3);
                KakaoDocument best = AddressNormalizer.pickBest(resp, q3);

                if (best == null && !baseQuery.equals(q3)) {
                    KakaoResponse resp2 = kakaoAddressClient.searchAddress(baseQuery);
                    best = AddressNormalizer.pickBest(resp2, baseQuery);
                }

                AddressNormalizer.NormalizedAddress na = null;
                if (best == null) {
                    KakaoKeywordResponse kresp = kakaoAddressClient.searchKeyword(q3);
                    if (kresp.getDocuments() != null && !kresp.getDocuments().isEmpty()) {
                        KakaoKeywordDoc kd = kresp.getDocuments().get(0);
                        na = AddressNormalizer.fromKeyword(kd);
                    }
                }

                // ===== 결과 필드 =====
                String zip = "", doName = "", siName = "", guName = "";
                String roadAddress = "", jibunAddress = "", detailAddr = "";

                if (best != null) {
                    zip = AddressNormalizer.getZip(best);

                    // 규칙 기반 분리
                    AddressNormalizer.AdminParts parts = AddressNormalizer.splitAdmin(best);
                    doName = parts.getDoName();
                    siName = parts.getSiName();
                    guName = parts.getGuName();

                    // 도로명 / 지번 구분 추출
                    roadAddress  = AddressNormalizer.getRoadFull(best);
                    jibunAddress = AddressNormalizer.getJibunFull(best);
                } else if (na != null) {
                    zip          = na.getZipCode() == null ? "" : na.getZipCode();
                    doName       = na.getDoName();
                    siName       = na.getSiName();
                    guName       = na.getGuName();
                    roadAddress  = na.getRoadAddress();
                    jibunAddress = na.getJibunAddress();
                }

                detailAddr = AddressNormalizer.mergeDetails(parenDetail, extraDetail, noise);

                // 출력 안전값
                String roadOut   = orDefault(roadAddress, "주소없음");
                String jibunOut  = orEopseum(jibunAddress);
                String zipOut    = orEopseum(zip);
                String doOut     = orEopseum(doName);
                String siOut     = orEopseum(siName);
                String guOut     = orEopseum(guName);
                String detailOut = orEopseum(detailAddr);

                // ===== 쓰기 =====
                // 0: id(사업자번호숫자)
                // 1: 회사명(원본)
                // 2: 회사명(지역삭제)
                // 3: 대표자명
                // 4: 사업자등록번호(하이픈)
                // 5: 우편번호
                // 6: 도
                // 7: 시
                // 8: 구
                // 9: 도로명
                // 10: 지번주소
                // 11: 상세주소
                // 12: tel
                // 13: phone
                // 14: email
                Row out = outSheet.createRow(outRowIdx++);
                write(out, 0,  brnDigits);
                write(out, 1,  companyOriginalOut);
                write(out, 2,  companyRegionRemovedOut);
                write(out, 3,  ceoOut);
                write(out, 4,  brnDashedFmt);
                write(out, 5,  zipOut);
                write(out, 6,  doOut);
                write(out, 7,  siOut);
                write(out, 8,  guOut);
                write(out, 9,  roadOut);    // 도로명
                write(out, 10, jibunOut);   // 지번주소
                write(out, 11, detailOut);  // 상세주소
                write(out, 12, telOut);
                write(out, 13, phoneOut);
                write(out, 14, emailOut);
            }

            // 컬럼 폭 자동 조정
            for (int c = 0; c <= 14; c++) {
                outSheet.autoSizeColumn(c);
            }

            outWb.write(baos);
            outWb.dispose();
            return baos.toByteArray();
        }
    }

    // ===================== 유틸 =====================

    private static void writeHeader(Sheet sh, String[] headers) {
        Row hr = sh.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hr.createCell(i, CellType.STRING);
            c.setCellValue(headers[i]);
        }
    }

    private static String getCell(Row r, int col, DataFormatter fmt) {
        if (r == null) return "";
        Cell c = r.getCell(col);
        if (c == null) return "";
        return fmt.formatCellValue(c).trim();
    }

    private static void write(Row r, int col, String v) {
        Cell c = r.createCell(col, CellType.STRING);
        c.setCellValue(v == null ? "" : v);
    }

    private static String onlyDigits(String s) {
        String d = (s == null) ? "" : s.replaceAll("\\D+", "");
        return d.isBlank() ? "없음" : d;
    }

    private static String formatBrnDashed(String digitsOrEopseum) {
        if (digitsOrEopseum == null || digitsOrEopseum.isBlank() || "없음".equals(digitsOrEopseum)) {
            return "없음";
        }
        String d = digitsOrEopseum.trim();
        if (d.length() == 10) {
            return d.substring(0, 3) + "-" + d.substring(3, 5) + "-" + d.substring(5);
        }
        return d;
    }

    private static String orDefault(String v, String def) {
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    private static String orEopseum(String v) {
        return orDefault(v, "없음");
    }

    /**
     * 회사명에서 "지역/상호명" 구조일 때, "지역/" 포함 앞부분 제거.
     * 예)
     *  - "춘천/광동타일"           -> "광동타일"
     *  - "(블랙)인천/이레종합타일" -> "이레종합타일"
     *  - "케이론"                 -> "케이론" (슬래시 없음, 그대로)
     */
    private static String stripRegionPrefix(String v) {
        if (v == null) return null;
        String s = v.trim();
        if (s.isEmpty()) return null;

        int idx = s.indexOf('/');
        if (idx < 0) {
            // 슬래시 없으면 그대로 반환
            return s;
        }

        String after = s.substring(idx + 1).trim();
        if (after.isEmpty()) {
            // "지역/"만 있고 뒤가 없으면, 안전하게 원본 유지
            return s;
        }
        return after;
    }
}