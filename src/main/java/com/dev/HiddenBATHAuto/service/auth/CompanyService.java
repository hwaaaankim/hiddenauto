package com.dev.HiddenBATHAuto.service.auth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.client.CompanyListRowDto;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.CompanyDeliveryAddress;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.repository.auth.CompanyDeliveryAddressRepository;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyDeliveryAddressRepository companyDeliveryAddressRepository;
    private final MemberRepository memberRepository;

    /**
     * ✅ (기존 유지) 대리점 리스트 화면 조회용
     */
    @Transactional(readOnly = true)
    public Page<CompanyListRowDto> getCompanyList(String keyword, String searchType, String sortField, String sortDir,
                                                  Pageable pageable) {
        String kw = (keyword == null) ? null : keyword.trim();
        if (kw != null && kw.isEmpty()) kw = null;

        return companyRepository.searchCompanyList(kw, searchType, sortField, sortDir, pageable);
    }

    /**
     * ✅ (기존 유지) 기존 방식의 "필터 조건으로 전체 다운로드" 엑셀
     *  - 기존 버튼/기능을 살려야 한다면 그대로 사용 가능
     */
    @Transactional(readOnly = true)
    public byte[] exportCompaniesToExcel(String keyword, String searchType, String sortField, String sortDir)
            throws IOException {
        String kw = (keyword == null) ? null : keyword.trim();
        if (kw != null && kw.isEmpty()) kw = null;

        // 1) 회사만 조회 (to-one만 fetch) - 기존 repository 메서드 사용
        List<Company> companies = companyRepository.findAllForExcel(kw, searchType, sortField, sortDir);
        if (companies.isEmpty()) {
            return buildExcel(Collections.emptyList(), Map.of(), Map.of());
        }

        // 2) 회사 id 목록
        List<Long> companyIds = companies.stream().map(Company::getId).collect(Collectors.toList());

        // 3) 배송지/직원 따로 조회 (MultipleBagFetch 회피)
        List<CompanyDeliveryAddress> addresses = companyDeliveryAddressRepository.findAllByCompanyIds(companyIds);
        List<Member> members = memberRepository.findAllByCompanyIds(companyIds);

        // 4) 그룹핑
        Map<Long, List<CompanyDeliveryAddress>> addrMap = addresses.stream()
                .collect(Collectors.groupingBy(a -> a.getCompany().getId(), LinkedHashMap::new, Collectors.toList()));

        Map<Long, List<Member>> memberMap = members.stream()
                .collect(Collectors.groupingBy(m -> m.getCompany().getId(), LinkedHashMap::new, Collectors.toList()));

        // 5) 엑셀 생성 (✅ map 기반 출력)
        return buildExcel(companies, addrMap, memberMap);
    }

    /**
     * ✅ (신규) 체크된 회사 ID 기준 엑셀 다운로드
     *  - 이번 요구사항(체크된 것만 다운로드)을 위해 추가된 메서드
     */
    @Transactional(readOnly = true)
    public byte[] exportCompaniesToExcelByIds(List<Long> companyIds, String sortField, String sortDir)
            throws IOException {

        if (companyIds == null || companyIds.isEmpty()) {
            return buildExcel(Collections.emptyList(), Map.of(), Map.of());
        }

        // Company 엔티티 기준으로만 안전 정렬(허용 필드 외는 createdAt으로)
        String safeSortField = sanitizeSortField(sortField);
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, safeSortField);

        // 1) 회사 조회 (ID 기준)
        List<Company> companies = companyRepository.findAllByIdIn(companyIds, sort);
        if (companies.isEmpty()) {
            return buildExcel(Collections.emptyList(), Map.of(), Map.of());
        }

        // 2) 회사 id 목록 (정렬된 companies 기준으로 재구성)
        List<Long> ids = companies.stream().map(Company::getId).collect(Collectors.toList());

        // 3) 배송지/직원 따로 조회 (MultipleBagFetch 회피)
        List<CompanyDeliveryAddress> addresses = companyDeliveryAddressRepository.findAllByCompanyIds(ids);
        List<Member> members = memberRepository.findAllByCompanyIds(ids);

        // 4) 그룹핑
        Map<Long, List<CompanyDeliveryAddress>> addrMap = addresses.stream()
                .collect(Collectors.groupingBy(a -> a.getCompany().getId(), LinkedHashMap::new, Collectors.toList()));

        Map<Long, List<Member>> memberMap = members.stream()
                .collect(Collectors.groupingBy(m -> m.getCompany().getId(), LinkedHashMap::new, Collectors.toList()));

        // 5) 엑셀 생성 (✅ map 기반 출력)
        return buildExcel(companies, addrMap, memberMap);
    }

    private String sanitizeSortField(String sortField) {
        // Company 엔티티에 존재하는 필드만 허용
        // (memberCount 같은 DTO 정렬값은 엔티티 필드가 아니라서 서버에서는 createdAt으로 대체)
        Set<String> allowed = Set.of(
                "id",
                "companyName",
                "representativeName",
                "createdAt",
                "updatedAt",
                "point",
                "businessNumber"
        );

        if (sortField == null) return "createdAt";
        String sf = sortField.trim();
        if (allowed.contains(sf)) return sf;
        return "createdAt";
    }

    // =========================
    // 엑셀 생성 (map 기반 출력)
    // =========================
    private byte[] buildExcel(List<Company> companies,
                              Map<Long, List<CompanyDeliveryAddress>> addrMap,
                              Map<Long, List<Member>> memberMap) throws IOException {

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Companies");

            // ===== 스타일 =====
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);

            Font bodyFont = wb.createFont();
            bodyFont.setFontHeightInPoints((short) 10);

            DataFormat dataFormat = wb.createDataFormat();

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setWrapText(true);

            CellStyle bodyStyle = wb.createCellStyle();
            bodyStyle.setFont(bodyFont);
            bodyStyle.setVerticalAlignment(VerticalAlignment.TOP);
            bodyStyle.setBorderTop(BorderStyle.THIN);
            bodyStyle.setBorderBottom(BorderStyle.THIN);
            bodyStyle.setBorderLeft(BorderStyle.THIN);
            bodyStyle.setBorderRight(BorderStyle.THIN);
            bodyStyle.setWrapText(true);

            CellStyle bodyCenterStyle = wb.createCellStyle();
            bodyCenterStyle.cloneStyleFrom(bodyStyle);
            bodyCenterStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle bodyDateStyle = wb.createCellStyle();
            bodyDateStyle.cloneStyleFrom(bodyStyle);
            bodyDateStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd hh:mm"));

            // ===== 헤더 =====
            int rowIdx = 0;
            Row header = sheet.createRow(rowIdx++);
            header.setHeightInPoints(22);

            String[] headers = new String[] {
                    "회사명", "포인트", "사업자등록번호", "직원수", "주소", "등록일", "수정일", "등록된 배송지들", "직원 목록(이 회사의 모든직원 정보)"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 컬럼 너비
            sheet.setColumnWidth(0, 20 * 256); // 회사명
            sheet.setColumnWidth(1, 10 * 256); // 포인트
            sheet.setColumnWidth(2, 18 * 256); // 사업자번호
            sheet.setColumnWidth(3, 10 * 256); // 직원수
            sheet.setColumnWidth(4, 35 * 256); // 주소
            sheet.setColumnWidth(5, 18 * 256); // 등록일
            sheet.setColumnWidth(6, 18 * 256); // 수정일
            sheet.setColumnWidth(7, 45 * 256); // 배송지
            sheet.setColumnWidth(8, 60 * 256); // 직원정보

            // ===== 바디 =====
            for (Company c : companies) {

                Long companyId = c.getId();

                List<Member> members = memberMap.getOrDefault(companyId, Collections.emptyList());
                List<CompanyDeliveryAddress> deliveries = addrMap.getOrDefault(companyId, Collections.emptyList());

                String address = buildCompanyAddress(c);
                long memberCount = members.size();

                String deliveryAddressesText = buildDeliveryAddressesText(deliveries);
                String membersText = buildMembersText(members);

                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(80);

                int col = 0;

                createCell(row, col++, c.getCompanyName(), bodyStyle);
                createCell(row, col++, String.valueOf(c.getPoint()), bodyCenterStyle);
                createCell(row, col++, c.getBusinessNumber(), bodyCenterStyle);
                createCell(row, col++, String.valueOf(memberCount), bodyCenterStyle);
                createCell(row, col++, address, bodyStyle);

                // 등록일
                if (c.getCreatedAt() != null) {
                    Cell cell = row.createCell(col++);
                    cell.setCellValue(c.getCreatedAt().format(dtf));
                    cell.setCellStyle(bodyStyle);
                } else {
                    createCell(row, col++, "-", bodyStyle);
                }

                // 수정일
                if (c.getUpdatedAt() != null) {
                    Cell cell = row.createCell(col++);
                    cell.setCellValue(c.getUpdatedAt().format(dtf));
                    cell.setCellStyle(bodyStyle);
                } else {
                    createCell(row, col++, "-", bodyStyle);
                }

                createCell(row, col++, deliveryAddressesText, bodyStyle);
                createCell(row, col++, membersText, bodyStyle);
            }

            sheet.createFreezePane(0, 1);

            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "-" : value);
        cell.setCellStyle(style);
    }

    private String buildCompanyAddress(Company c) {
        StringBuilder sb = new StringBuilder();
        if (c.getZipCode() != null && !c.getZipCode().isEmpty())
            sb.append("(").append(c.getZipCode()).append(") ").append("\n");
        if (c.getRoadAddress() != null && !c.getRoadAddress().isEmpty())
            sb.append(c.getRoadAddress());
        if (c.getDetailAddress() != null && !c.getDetailAddress().isEmpty())
            sb.append(" ").append(c.getDetailAddress());
        if (sb.toString().trim().isEmpty()) return "-";
        return sb.toString().trim();
    }

    private String buildDeliveryAddressesText(List<CompanyDeliveryAddress> list) {
        if (list == null || list.isEmpty()) return "-";

        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (CompanyDeliveryAddress a : list) {
            sb.append(i++).append(") ");
            if (a.getZipCode() != null && !a.getZipCode().isEmpty())
                sb.append("(").append(a.getZipCode()).append(") ");
            sb.append(a.getRoadAddress() != null ? a.getRoadAddress() : "");
            if (a.getDetailAddress() != null && !a.getDetailAddress().isEmpty())
                sb.append(" ").append(a.getDetailAddress());
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String buildMembersText(List<Member> members) {
        if (members == null || members.isEmpty()) return "-";

        StringBuilder sb = new StringBuilder();
        int i = 1;
        DateTimeFormatter memberDtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (Member m : members) {
            sb.append(i++).append(") ");
            sb.append("이름: ").append(nvl(m.getName())).append(" / ");
            sb.append("아이디: ").append(nvl(m.getUsername())).append(" / ");
            sb.append("이메일: ").append(nvl(m.getEmail())).append(" / ");
            sb.append("휴대폰: ").append(nvl(m.getPhone())).append(" / ");
            sb.append("권한: ").append(m.getRole() != null ? m.getRole().name() : "-").append(" / ");
            sb.append("가입일: ").append(m.getCreatedAt() != null ? m.getCreatedAt().format(memberDtf) : "-");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String nvl(String v) {
        return (v == null || v.isEmpty()) ? "-" : v;
    }
}