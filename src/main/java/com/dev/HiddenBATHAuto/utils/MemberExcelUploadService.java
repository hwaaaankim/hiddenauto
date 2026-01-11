package com.dev.HiddenBATHAuto.utils;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.MemberExcelUploadResult;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberExcelUploadService {

    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager em;

    private static final String DEFAULT_PASSWORD = "12345";

    // ✅ 인덱스 고정
    private static final int COL_COMPANY_NAME   = 0;
    private static final int COL_BUSINESS_NO    = 1;
    private static final int COL_MEMBER_NAME    = 2;
    private static final int COL_ZIP            = 3;
    private static final int COL_ORIGIN_ADDR    = 4;
    private static final int COL_DO             = 5;
    private static final int COL_SI             = 6;
    private static final int COL_GU             = 7;
    private static final int COL_ROAD_ADDR      = 8;
    private static final int COL_JIBUN_ADDR     = 9;
    private static final int COL_DETAIL_ADDR    = 10;
    private static final int COL_TEL            = 11;
    private static final int COL_PHONE          = 12;
    private static final int COL_EMAIL          = 13;

    /**
     * ✅ 바깥 메서드는 트랜잭션 걸지 않습니다.
     * (여기서 @Transactional 걸면 “한 행 실패 → 전체 rollback-only”가 됩니다)
     */
    public MemberExcelUploadResult uploadAndRegister(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드 파일이 비어있습니다.");
        }

        // salesManager로 지정할 admin(1번) 존재 확인만 여기서
        memberRepository.findById(1L)
            .orElseThrow(() -> new IllegalStateException("salesManager로 지정할 Member(id=1)를 찾을 수 없습니다."));

        MemberExcelUploadResult result = new MemberExcelUploadResult();

        try (InputStream is = file.getInputStream();
             Workbook wb = WorkbookFactory.create(is)) {

            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            if (sheet == null) throw new IllegalArgumentException("엑셀 시트가 존재하지 않습니다.");

            DataFormatter formatter = new DataFormatter(Locale.KOREA);

            int lastRow = sheet.getLastRowNum();

            // 0행은 헤더 스킵
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String companyNameRaw = readTextCell(row, COL_COMPANY_NAME, formatter);
                String businessRaw    = readBusinessCellRaw(row, COL_BUSINESS_NO, formatter);
                String memberNameRaw  = readTextCell(row, COL_MEMBER_NAME, formatter);

                if (isAllBlank(companyNameRaw, businessRaw, memberNameRaw)) continue;

                result.setTotalRows(result.getTotalRows() + 1);

                try {
                    // ✅ 행 단위 새 트랜잭션으로 처리
                    RowProcessCounts counts = processOneRowInNewTx(row, r, formatter);

                    result.setSuccessRows(result.getSuccessRows() + 1);
                    result.setCreatedCompanies(result.getCreatedCompanies() + counts.createdCompanies);
                    result.setUpdatedCompanies(result.getUpdatedCompanies() + counts.updatedCompanies);
                    result.setCreatedMembers(result.getCreatedMembers() + counts.createdMembers);
                    result.setUpdatedMembers(result.getUpdatedMembers() + counts.updatedMembers);

                } catch (Exception rowEx) {
                    result.setFailedRows(result.getFailedRows() + 1);
                    result.getErrors().add(new MemberExcelUploadResult.RowError(r, rowEx.getMessage()));
                }
            }

            return result;

        } catch (Exception e) {
            throw new IllegalStateException("엑셀 처리 중 오류: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ 이 메서드만 REQUIRES_NEW
     * - 한 행 저장 중 예외 → 그 행만 롤백
     * - 다음 행은 새 트랜잭션으로 계속 진행
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected RowProcessCounts processOneRowInNewTx(Row row, int rowIndex, DataFormatter formatter) {
        // salesManager admin(1) 로드 (트랜잭션 안에서 프록시 안전)
        Member admin = memberRepository.findById(1L)
            .orElseThrow(() -> new IllegalStateException("salesManager로 지정할 Member(id=1)를 찾을 수 없습니다."));

        String companyNameRaw = readTextCell(row, COL_COMPANY_NAME, formatter);
        String businessRaw    = readBusinessCellRaw(row, COL_BUSINESS_NO, formatter);
        String memberNameRaw  = readTextCell(row, COL_MEMBER_NAME, formatter);

        String companyName = normalizeText(companyNameRaw);
        String memberName  = normalizeText(memberNameRaw);

        String businessNumberClean = normalizeBusinessNumber(businessRaw);
        if (businessNumberClean.isBlank()) {
            throw new IllegalArgumentException("사업자등록번호없음 (원본='" + safe(businessRaw) + "') / row=" + rowIndex
                + " / snapshot=" + snapshotRow(row, formatter));
        }

        // ✅ 10자리 강제 (원치 않으면 제거)
        if (businessNumberClean.length() != 10) {
            throw new IllegalArgumentException("사업자등록번호 형식오류(10자리 아님). 정제='" + businessNumberClean
                + "', 원본='" + safe(businessRaw) + "' / row=" + rowIndex
                + " / snapshot=" + snapshotRow(row, formatter));
        }

        String zipCode       = normalizeText(readTextCell(row, COL_ZIP, formatter));
        String originAddress = normalizeText(readTextCell(row, COL_ORIGIN_ADDR, formatter));
        String doName        = normalizeText(readTextCell(row, COL_DO, formatter));
        String siName        = normalizeText(readTextCell(row, COL_SI, formatter));
        String guName        = normalizeText(readTextCell(row, COL_GU, formatter));
        String roadAddress   = normalizeText(readTextCell(row, COL_ROAD_ADDR, formatter));
        String jibunAddress  = normalizeText(readTextCell(row, COL_JIBUN_ADDR, formatter));
        String detailAddress = normalizeText(readTextCell(row, COL_DETAIL_ADDR, formatter));
        String telephone     = normalizeText(readTextCell(row, COL_TEL, formatter));
        String phone         = normalizeText(readTextCell(row, COL_PHONE, formatter));
        String email         = normalizeText(readTextCell(row, COL_EMAIL, formatter));

        RowProcessCounts counts = new RowProcessCounts();

     // ===== Company upsert =====
        Company company = companyRepository.findByBusinessNumber(businessNumberClean).orElse(null);
        boolean isCompanyNew = (company == null);
        if (isCompanyNew) {
            company = new Company();
            company.setBusinessNumber(businessNumberClean);
            company.setCreatedAt(LocalDateTime.now());
            company.setPoint(0);
            company.setSalesManager(admin);
            counts.createdCompanies++;
        } else {
            counts.updatedCompanies++;
        }

        company.setCompanyName(companyName);

        // ✅ 추가: registrationKey 세팅
        company.setRegistrationKey(businessNumberClean);

        company.setZipCode(zipCode);
        company.setDoName(doName);
        company.setSiName(siName);
        company.setGuName(guName);

        company.setOriginAddress(originAddress);
        company.setJibunAddress(jibunAddress);

        company.setRoadAddress(roadAddress);
        company.setDetailAddress(detailAddress);

        company.setUpdatedAt(LocalDateTime.now());

        // ✅ 저장 + flush
        company = companyRepository.saveAndFlush(company);


        // ===== Member upsert =====
        String username = businessNumberClean;

        Member member = memberRepository.findByUsername(username).orElse(null);
        boolean isMemberNew = (member == null);

        if (isMemberNew) {
            member = new Member();
            member.setCreatedAt(LocalDateTime.now());
            member.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
            counts.createdMembers++;
        } else {
            counts.updatedMembers++;
            // 기존도 초기화 원하면 주석 해제
            // member.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        }

        member.setUsername(username);
        member.setName(memberName);
        member.setEmail(email);
        member.setPhone(phone);
        member.setTelephone(telephone);

        member.setRole(MemberRole.CUSTOMER_REPRESENTATIVE);
        member.setCompany(company);

        member.setTeam(null);
        member.setTeamCategory(null);
        member.setProductCategoryScope(null);

        member.setUpdatedAt(LocalDateTime.now());
        member.setEnabled(true);

        memberRepository.saveAndFlush(member);

        // ✅ 행 하나 끝났으면 영속성 컨텍스트를 비워서 다음 행에 영향 없게(안전)
        em.clear();

        return counts;
    }

    // ====== 내부 카운트용 ======
    protected static class RowProcessCounts {
        int createdCompanies = 0;
        int updatedCompanies = 0;
        int createdMembers = 0;
        int updatedMembers = 0;
    }

    // ====== 셀 읽기 유틸 ======
    private static String readTextCell(Row row, int colIdx, DataFormatter formatter) {
        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return formatter.formatCellValue(cell);
    }

    private static String readBusinessCellRaw(Row row, int colIdx, DataFormatter formatter) {
        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";

        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) type = cell.getCachedFormulaResultType();

        switch (type) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                // 표시값(formatter) 쓰면 날짜처럼 바뀔 수 있어 raw 숫자로 복원
                double nv = cell.getNumericCellValue();
                return BigDecimal.valueOf(nv).stripTrailingZeros().toPlainString();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return formatter.formatCellValue(cell);
        }
    }

    private static String normalizeText(String v) {
        if (v == null) return "-";
        String s = v.trim();
        if (s.isEmpty()) return "-";
        if ("NULL".equalsIgnoreCase(s)) return "-";
        return s;
    }

    private static String normalizeBusinessNumber(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        if ("NULL".equalsIgnoreCase(s)) return "";
        return s.replaceAll("[^0-9]", "");
    }

    private static boolean isAllBlank(String... arr) {
        for (String s : arr) {
            if (s != null && !s.trim().isEmpty() && !"NULL".equalsIgnoreCase(s.trim())) return false;
        }
        return true;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String snapshotRow(Row row, DataFormatter formatter) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i <= 13; i++) {
            String v = "";
            try {
                if (i == COL_BUSINESS_NO) v = readBusinessCellRaw(row, i, formatter);
                else v = readTextCell(row, i, formatter);
            } catch (Exception ignore) {}
            if (i > 0) sb.append(", ");
            sb.append(i).append(":'").append(safe(v)).append("'");
        }
        sb.append("}");
        return sb.toString();
    }
}