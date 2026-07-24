package com.dev.HiddenBATHAuto.service.client.bulk;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.client.bulk.ClientBulkImportIssue;
import com.dev.HiddenBATHAuto.dto.client.bulk.ClientBulkImportPreviewResponse;
import com.dev.HiddenBATHAuto.dto.client.bulk.ClientBulkImportPreviewRow;
import com.dev.HiddenBATHAuto.dto.client.bulk.ClientBulkImportSaveRequest;
import com.dev.HiddenBATHAuto.dto.client.bulk.ClientBulkImportSaveResponse;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.service.client.bulk.ClientBulkImportAddressService.AddressResolution;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClientBulkImportService {

    private static final long MAX_EXCEL_FILE_SIZE = 20L * 1024L * 1024L;
    private static final int MAX_IMPORT_ROWS = 500;
    private static final Long DEFAULT_SALES_MANAGER_ID = 1L;
    private static final String DEFAULT_PASSWORD = "12345";

    // 실제 엑셀 기준 0-based column index
    private static final int COL_COMPANY_NAME = 3;
    private static final int COL_BUSINESS_NUMBER = 4;
    private static final int COL_REPRESENTATIVE_NAME = 5;
    private static final int COL_ADDRESS_1 = 6;
    private static final int COL_ADDRESS_2 = 7;
    private static final int COL_TELEPHONE = 12;
    private static final int COL_PHONE = 14;
    private static final int COL_EMAIL = 26;

    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final ClientBulkImportAddressService addressService;

    public ClientBulkImportPreviewResponse preview(MultipartFile file) {
        validateExcelFile(file);

        List<ClientBulkImportPreviewRow> rows = parseRows(file);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("엑셀에서 등록할 대리점 데이터를 찾지 못했습니다.");
        }
        if (rows.size() > MAX_IMPORT_ROWS) {
            throw new IllegalArgumentException("한 번에 등록할 수 있는 최대 행 수는 " + MAX_IMPORT_ROWS + "행입니다.");
        }

        applyInFileDuplicateValidation(rows);
        applyDatabaseDuplicateValidation(rows);

        int saveableCount = (int) rows.stream().filter(row -> row.isSaveTarget() && !row.hasError()).count();
        String message = "총 " + rows.size() + "행을 분석했습니다. 저장 가능 " + saveableCount + "행입니다.";

        return new ClientBulkImportPreviewResponse(true, message, rows.size(), saveableCount, rows);
    }

    @Transactional
    public ClientBulkImportSaveResponse save(ClientBulkImportSaveRequest request) {
        List<ClientBulkImportPreviewRow> selectedRows = normalizeSelectedRows(request);
        List<ClientBulkImportIssue> issues = validateRowsForSave(selectedRows);

        if (!issues.isEmpty()) {
            throw new ClientBulkImportValidationException("저장할 수 없는 항목이 있습니다.", issues);
        }

        Member salesManager = memberRepository.findById(DEFAULT_SALES_MANAGER_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "담당 영업사원으로 지정할 멤버 ID 1이 존재하지 않습니다. 등록 전에 멤버 정보를 확인해 주세요."
                ));

        LocalDateTime now = LocalDateTime.now();
        int savedCompanyCount = 0;
        int savedMemberCount = 0;

        for (ClientBulkImportPreviewRow row : selectedRows) {
            String businessNumber = digitsOnly(row.getBusinessNumber());

            Company company = new Company();
            company.setCompanyName(clean(row.getCompanyName()));
            company.setPoint(0);
            company.setBusinessNumber(businessNumber);
            company.setZipCode(clean(row.getZipCode()));
            company.setDoName(clean(row.getDoName()));
            company.setSiName(clean(row.getSiName()));
            company.setGuName(clean(row.getGuName()));
            company.setOriginAddress(clean(row.getOriginAddress()));
            company.setJibunAddress(clean(row.getJibunAddress()));
            company.setRoadAddress(clean(row.getRoadAddress()));
            company.setDetailAddress(clean(row.getDetailAddress()));
            company.setRegistrationKey(createUniqueRegistrationKey());
            company.setCreatedAt(now);
            company.setUpdatedAt(null);
            company.setSalesManager(salesManager);

            Company savedCompany = companyRepository.save(company);
            savedCompanyCount++;

            Member representative = new Member();
            representative.setUsername(businessNumber);
            representative.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
            representative.setName(clean(row.getRepresentativeName()));
            representative.setTelephone(clean(row.getTelephone()));
            representative.setPhone(clean(row.getPhone()));
            representative.setEmail(clean(row.getEmail()));
            representative.setRole(MemberRole.CUSTOMER_REPRESENTATIVE);
            representative.setCompany(savedCompany);
            representative.setEnabled(true);
            representative.setCreatedAt(now);
            representative.setUpdatedAt(null);
            representative.setLastLoginAt(null);

            memberRepository.save(representative);
            savedMemberCount++;
        }

        // DB unique 제약 위반도 컨트롤러 응답 전에 확실히 발생하도록 명시적으로 flush합니다.
        entityManager.flush();

        return ClientBulkImportSaveResponse.success(savedCompanyCount, savedMemberCount);
    }

    private List<ClientBulkImportPreviewRow> parseRows(MultipartFile file) {
        List<ClientBulkImportPreviewRow> result = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() <= 0) {
                throw new IllegalArgumentException("엑셀 시트가 존재하지 않습니다.");
            }

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            int lastRowNum = sheet.getLastRowNum();
            for (int rowIndex = 1; rowIndex <= lastRowNum; rowIndex++) {
                Row excelRow = sheet.getRow(rowIndex);
                if (excelRow == null) {
                    continue;
                }

                int excelRowNumber = rowIndex + 1;
                String rawCompanyName = cellText(excelRow, COL_COMPANY_NAME, formatter, evaluator);
                String businessNumber = digitsOnly(cellText(excelRow, COL_BUSINESS_NUMBER, formatter, evaluator));
                String representativeName = cellText(excelRow, COL_REPRESENTATIVE_NAME, formatter, evaluator);
                String address1 = cellText(excelRow, COL_ADDRESS_1, formatter, evaluator);
                String address2 = cellText(excelRow, COL_ADDRESS_2, formatter, evaluator);
                String telephone = cellText(excelRow, COL_TELEPHONE, formatter, evaluator);
                String phone = cellText(excelRow, COL_PHONE, formatter, evaluator);
                String email = cellText(excelRow, COL_EMAIL, formatter, evaluator);

                if (allBlank(rawCompanyName, businessNumber, representativeName, address1, address2, telephone, phone, email)) {
                    continue;
                }

                String companyName = normalizeCompanyName(rawCompanyName);
                // 사업장주소1/2는 실제 엑셀에서 주소 본문이 두 셀로 잘려 들어오는 경우가 있으므로
                // originAddress에는 두 셀을 순서대로 합쳐 원문을 보존합니다.
                String originAddress = joinNonBlank(" ", address1, address2);
                // 다만 주소1의 열린 괄호를 주소2가 닫는 형태라면 주소2는 상세주소가 아니라
                // 잘린 주소 본문의 연속이므로 상세주소로 중복 저장하지 않습니다.
                String excelDetailAddress = resolveExcelDetailAddress(address1, address2);
                AddressResolution address = addressService.resolve(originAddress, excelDetailAddress);

                ClientBulkImportPreviewRow row = ClientBulkImportPreviewRow.builder()
                        .excelRowNumber(excelRowNumber)
                        .saveTarget(true)
                        .companyName(companyName)
                        .businessNumber(businessNumber)
                        .representativeName(clean(representativeName))
                        .telephone(clean(telephone))
                        .phone(clean(phone))
                        .email(clean(email))
                        .originAddress(originAddress)
                        .zipCode(clean(address.zipCode()))
                        .doName(clean(address.doName()))
                        .siName(clean(address.siName()))
                        .guName(clean(address.guName()))
                        .jibunAddress(clean(address.jibunAddress()))
                        .roadAddress(clean(address.roadAddress()))
                        .detailAddress(clean(address.detailAddress()))
                        .addressResolved(address.resolved())
                        .addressSource(clean(address.source()))
                        .existingCompanyDuplicate(false)
                        .existingMemberDuplicate(false)
                        .issues(new ArrayList<>())
                        .build();

                applyBasicValidation(row);

                if (!originAddress.isBlank() && !address.resolved()) {
                    row.addIssue(ClientBulkImportIssue.warning(
                            "ADDRESS_NOT_RESOLVED",
                            "주소 자동검색에 실패했습니다. 구조화 주소는 빈값으로 저장할 수 있으며, 필요하면 다음 주소검색으로 수정해 주세요.",
                            excelRowNumber
                    ));
                }

                if (row.hasError()) {
                    row.setSaveTarget(false);
                }

                result.add(row);
            }

            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("엑셀 파일을 읽을 수 없습니다. 파일 형식 또는 손상 여부를 확인해 주세요.", e);
        } catch (Exception e) {
            throw new IllegalArgumentException("엑셀 분석 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    private void applyBasicValidation(ClientBulkImportPreviewRow row) {
        int excelRowNumber = row.getExcelRowNumber();

        if (isBlank(row.getCompanyName())) {
            row.addIssue(ClientBulkImportIssue.error(
                    "COMPANY_NAME_REQUIRED",
                    "대리점명이 비어 있습니다.",
                    excelRowNumber
            ));
        }

        String businessNumber = digitsOnly(row.getBusinessNumber());
        row.setBusinessNumber(businessNumber);
        if (businessNumber.length() != 10) {
            row.addIssue(ClientBulkImportIssue.error(
                    "BUSINESS_NUMBER_INVALID",
                    "사업자등록번호는 하이픈을 제외한 숫자 10자리여야 합니다.",
                    excelRowNumber
            ));
        }

        if (isBlank(row.getRepresentativeName())) {
            row.addIssue(ClientBulkImportIssue.error(
                    "REPRESENTATIVE_NAME_REQUIRED",
                    "대표자명이 비어 있습니다.",
                    excelRowNumber
            ));
        }

        validateLength(row.getCompanyName(), 255, "대리점명", "COMPANY_NAME_TOO_LONG", excelRowNumber, row);
        validateLength(row.getRepresentativeName(), 255, "대표자명", "REPRESENTATIVE_NAME_TOO_LONG", excelRowNumber, row);
        validateLength(row.getOriginAddress(), 255, "원본주소", "ORIGIN_ADDRESS_TOO_LONG", excelRowNumber, row);
        validateLength(row.getJibunAddress(), 255, "지번주소", "JIBUN_ADDRESS_TOO_LONG", excelRowNumber, row);
        validateLength(row.getRoadAddress(), 255, "도로명주소", "ROAD_ADDRESS_TOO_LONG", excelRowNumber, row);
        validateLength(row.getDetailAddress(), 255, "상세주소", "DETAIL_ADDRESS_TOO_LONG", excelRowNumber, row);
    }

    private void validateLength(
            String value,
            int maxLength,
            String fieldName,
            String code,
            int excelRowNumber,
            ClientBulkImportPreviewRow row
    ) {
        if (value != null && value.length() > maxLength) {
            row.addIssue(ClientBulkImportIssue.error(
                    code,
                    fieldName + "은(는) " + maxLength + "자를 초과할 수 없습니다.",
                    excelRowNumber
            ));
        }
    }

    private void applyInFileDuplicateValidation(List<ClientBulkImportPreviewRow> rows) {
        Map<String, List<ClientBulkImportPreviewRow>> duplicateMap = rows.stream()
                .filter(row -> digitsOnly(row.getBusinessNumber()).length() == 10)
                .collect(Collectors.groupingBy(
                        row -> digitsOnly(row.getBusinessNumber()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        duplicateMap.forEach((businessNumber, duplicateRows) -> {
            if (duplicateRows.size() <= 1) {
                return;
            }
            for (ClientBulkImportPreviewRow row : duplicateRows) {
                row.addIssue(ClientBulkImportIssue.error(
                        "DUPLICATE_IN_EXCEL",
                        "엑셀 안에서 동일한 사업자등록번호가 중복되었습니다: " + businessNumber,
                        row.getExcelRowNumber()
                ));
                row.setSaveTarget(false);
            }
        });
    }

    private void applyDatabaseDuplicateValidation(List<ClientBulkImportPreviewRow> rows) {
        Set<String> businessNumbers = rows.stream()
                .map(ClientBulkImportPreviewRow::getBusinessNumber)
                .map(this::digitsOnly)
                .filter(value -> value.length() == 10)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> existingCompanyNumbers = findExistingCompanyBusinessNumbers(businessNumbers);
        Set<String> existingMemberUsernames = findExistingMemberUsernames(businessNumbers);

        for (ClientBulkImportPreviewRow row : rows) {
            String businessNumber = digitsOnly(row.getBusinessNumber());

            if (existingCompanyNumbers.contains(businessNumber)) {
                row.setExistingCompanyDuplicate(true);
                row.addIssue(ClientBulkImportIssue.error(
                        "COMPANY_ALREADY_EXISTS",
                        "이미 등록된 사업자등록번호의 대리점입니다. 기존 대리점은 수정하지 않으며 이 행은 저장할 수 없습니다.",
                        row.getExcelRowNumber()
                ));
                row.setSaveTarget(false);
            }

            if (existingMemberUsernames.contains(businessNumber)) {
                row.setExistingMemberDuplicate(true);
                row.addIssue(ClientBulkImportIssue.error(
                        "MEMBER_USERNAME_ALREADY_EXISTS",
                        "사업자등록번호와 동일한 멤버 아이디가 이미 존재합니다. 이 행은 저장할 수 없습니다.",
                        row.getExcelRowNumber()
                ));
                row.setSaveTarget(false);
            }
        }
    }

    private List<ClientBulkImportPreviewRow> normalizeSelectedRows(ClientBulkImportSaveRequest request) {
        if (request == null || request.getRows() == null) {
            throw new ClientBulkImportValidationException(
                    "저장 요청 데이터가 없습니다.",
                    List.of(ClientBulkImportIssue.error("EMPTY_REQUEST", "저장 요청 데이터가 없습니다.", null))
            );
        }

        List<ClientBulkImportPreviewRow> selectedRows = request.getRows().stream()
                .filter(row -> row != null && row.isSaveTarget())
                .toList();

        if (selectedRows.isEmpty()) {
            throw new ClientBulkImportValidationException(
                    "저장 대상으로 선택된 행이 없습니다.",
                    List.of(ClientBulkImportIssue.error("NO_SELECTED_ROWS", "저장 대상으로 선택된 행이 없습니다.", null))
            );
        }

        if (selectedRows.size() > MAX_IMPORT_ROWS) {
            throw new ClientBulkImportValidationException(
                    "한 번에 등록할 수 있는 최대 행 수를 초과했습니다.",
                    List.of(ClientBulkImportIssue.error(
                            "TOO_MANY_ROWS",
                            "한 번에 등록할 수 있는 최대 행 수는 " + MAX_IMPORT_ROWS + "행입니다.",
                            null
                    ))
            );
        }

        for (ClientBulkImportPreviewRow row : selectedRows) {
            row.setCompanyName(clean(row.getCompanyName()));
            row.setBusinessNumber(digitsOnly(row.getBusinessNumber()));
            row.setRepresentativeName(clean(row.getRepresentativeName()));
            row.setTelephone(clean(row.getTelephone()));
            row.setPhone(clean(row.getPhone()));
            row.setEmail(clean(row.getEmail()));
            row.setOriginAddress(clean(row.getOriginAddress()));
            row.setZipCode(clean(row.getZipCode()));
            row.setDoName(clean(row.getDoName()));
            row.setSiName(clean(row.getSiName()));
            row.setGuName(clean(row.getGuName()));
            row.setJibunAddress(clean(row.getJibunAddress()));
            row.setRoadAddress(clean(row.getRoadAddress()));
            row.setDetailAddress(clean(row.getDetailAddress()));
        }

        return selectedRows;
    }

    private List<ClientBulkImportIssue> validateRowsForSave(List<ClientBulkImportPreviewRow> rows) {
        List<ClientBulkImportIssue> issues = new ArrayList<>();
        Map<String, Integer> numberCounts = new HashMap<>();

        for (ClientBulkImportPreviewRow row : rows) {
            int excelRowNumber = row.getExcelRowNumber();
            String businessNumber = digitsOnly(row.getBusinessNumber());

            if (isBlank(row.getCompanyName())) {
                issues.add(ClientBulkImportIssue.error(
                        "COMPANY_NAME_REQUIRED",
                        "대리점명이 비어 있습니다.",
                        excelRowNumber
                ));
            }
            if (businessNumber.length() != 10) {
                issues.add(ClientBulkImportIssue.error(
                        "BUSINESS_NUMBER_INVALID",
                        "사업자등록번호는 숫자 10자리여야 합니다.",
                        excelRowNumber
                ));
            }
            if (isBlank(row.getRepresentativeName())) {
                issues.add(ClientBulkImportIssue.error(
                        "REPRESENTATIVE_NAME_REQUIRED",
                        "대표자명이 비어 있습니다.",
                        excelRowNumber
                ));
            }

            addLengthIssue(issues, row.getCompanyName(), 255, "대리점명", "COMPANY_NAME_TOO_LONG", excelRowNumber);
            addLengthIssue(issues, row.getRepresentativeName(), 255, "대표자명", "REPRESENTATIVE_NAME_TOO_LONG", excelRowNumber);
            addLengthIssue(issues, row.getOriginAddress(), 255, "원본주소", "ORIGIN_ADDRESS_TOO_LONG", excelRowNumber);
            addLengthIssue(issues, row.getJibunAddress(), 255, "지번주소", "JIBUN_ADDRESS_TOO_LONG", excelRowNumber);
            addLengthIssue(issues, row.getRoadAddress(), 255, "도로명주소", "ROAD_ADDRESS_TOO_LONG", excelRowNumber);
            addLengthIssue(issues, row.getDetailAddress(), 255, "상세주소", "DETAIL_ADDRESS_TOO_LONG", excelRowNumber);

            if (businessNumber.length() == 10) {
                numberCounts.merge(businessNumber, 1, Integer::sum);
            }
        }

        Set<String> duplicatedInRequest = numberCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        for (ClientBulkImportPreviewRow row : rows) {
            String businessNumber = digitsOnly(row.getBusinessNumber());
            if (duplicatedInRequest.contains(businessNumber)) {
                issues.add(ClientBulkImportIssue.error(
                        "DUPLICATE_IN_REQUEST",
                        "저장 요청 안에서 사업자등록번호가 중복되었습니다: " + businessNumber,
                        row.getExcelRowNumber()
                ));
            }
        }

        Set<String> businessNumbers = rows.stream()
                .map(ClientBulkImportPreviewRow::getBusinessNumber)
                .map(this::digitsOnly)
                .filter(value -> value.length() == 10)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> existingCompanyNumbers = findExistingCompanyBusinessNumbers(businessNumbers);
        Set<String> existingMemberUsernames = findExistingMemberUsernames(businessNumbers);

        for (ClientBulkImportPreviewRow row : rows) {
            String businessNumber = digitsOnly(row.getBusinessNumber());
            if (existingCompanyNumbers.contains(businessNumber)) {
                issues.add(ClientBulkImportIssue.error(
                        "COMPANY_ALREADY_EXISTS",
                        "이미 등록된 사업자등록번호입니다. 기존 대리점 데이터는 변경하지 않습니다.",
                        row.getExcelRowNumber()
                ));
            }
            if (existingMemberUsernames.contains(businessNumber)) {
                issues.add(ClientBulkImportIssue.error(
                        "MEMBER_USERNAME_ALREADY_EXISTS",
                        "사업자등록번호와 동일한 멤버 아이디가 이미 존재합니다.",
                        row.getExcelRowNumber()
                ));
            }
        }

        return deduplicateIssues(issues);
    }

    private Set<String> findExistingCompanyBusinessNumbers(Set<String> businessNumbers) {
        if (businessNumbers == null || businessNumbers.isEmpty()) {
            return Set.of();
        }

        Set<String> lookupCandidates = buildBusinessNumberLookupCandidates(businessNumbers);
        return entityManager.createQuery(
                        "select c.businessNumber from Company c where c.businessNumber in :businessNumbers",
                        String.class
                )
                .setParameter("businessNumbers", lookupCandidates)
                .getResultList()
                .stream()
                .map(this::digitsOnly)
                .filter(value -> value.length() == 10)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Set<String> findExistingMemberUsernames(Set<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Set.of();
        }

        Set<String> lookupCandidates = buildBusinessNumberLookupCandidates(usernames);
        return entityManager.createQuery(
                        "select m.username from Member m where m.username in :usernames",
                        String.class
                )
                .setParameter("usernames", lookupCandidates)
                .getResultList()
                .stream()
                .map(this::digitsOnly)
                .filter(value -> value.length() == 10)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Set<String> buildBusinessNumberLookupCandidates(Set<String> values) {
        Set<String> candidates = new LinkedHashSet<>();
        for (String value : values) {
            String digits = digitsOnly(value);
            if (digits.length() != 10) {
                continue;
            }
            candidates.add(digits);
            candidates.add(digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5));
        }
        return candidates;
    }

    private String createUniqueRegistrationKey() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String key = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            Long count = entityManager.createQuery(
                            "select count(c) from Company c where c.registrationKey = :registrationKey",
                            Long.class
                    )
                    .setParameter("registrationKey", key)
                    .getSingleResult();
            if (count == 0L) {
                return key;
            }
        }
        throw new IllegalStateException("고유 업체코드를 생성하지 못했습니다. 다시 시도해 주세요.");
    }

    private List<ClientBulkImportIssue> deduplicateIssues(List<ClientBulkImportIssue> issues) {
        Map<String, ClientBulkImportIssue> unique = new LinkedHashMap<>();
        for (ClientBulkImportIssue issue : issues) {
            String key = issue.level() + "|" + issue.code() + "|" + issue.excelRowNumber() + "|" + issue.message();
            unique.putIfAbsent(key, issue);
        }
        return new ArrayList<>(unique.values());
    }

    private void addLengthIssue(
            List<ClientBulkImportIssue> issues,
            String value,
            int maxLength,
            String fieldName,
            String code,
            int excelRowNumber
    ) {
        if (value != null && value.length() > maxLength) {
            issues.add(ClientBulkImportIssue.error(
                    code,
                    fieldName + "은(는) " + maxLength + "자를 초과할 수 없습니다.",
                    excelRowNumber
            ));
        }
    }

    private void validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("엑셀 파일을 선택해 주세요.");
        }
        if (file.getSize() > MAX_EXCEL_FILE_SIZE) {
            throw new IllegalArgumentException("엑셀 파일은 20MB 이하만 업로드할 수 있습니다.");
        }

        String filename = file.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase();
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new IllegalArgumentException(".xlsx 또는 .xls 파일만 업로드할 수 있습니다.");
        }
    }

    private String cellText(Row row, int columnIndex, DataFormatter formatter, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return "";
        }
        return clean(formatter.formatCellValue(cell, evaluator));
    }


    private String resolveExcelDetailAddress(String address1, String address2) {
        String first = clean(address1);
        String second = clean(address2);
        if (second.isBlank()) {
            return "";
        }

        long openCount = first.chars().filter(ch -> ch == '(').count();
        long closeCount = first.chars().filter(ch -> ch == ')').count();
        if (openCount > closeCount && second.indexOf(')') >= 0) {
            return "";
        }

        return second;
    }

    private String normalizeCompanyName(String rawName) {
        String value = clean(rawName);
        int slashIndex = value.indexOf('/');
        if (slashIndex >= 0 && slashIndex + 1 < value.length()) {
            String withoutRegion = clean(value.substring(slashIndex + 1));
            if (!withoutRegion.isBlank()) {
                return withoutRegion;
            }
        }
        return value;
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String joinNonBlank(String delimiter, String... values) {
        List<String> texts = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                String cleaned = clean(value);
                if (!cleaned.isBlank()) {
                    texts.add(cleaned);
                }
            }
        }
        return String.join(delimiter, texts);
    }

    private boolean allBlank(String... values) {
        if (values == null) {
            return true;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return false;
            }
        }
        return true;
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
