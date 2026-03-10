package com.dev.HiddenBATHAuto.service.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.client.AdminClientCompanyUpdateRequest;
import com.dev.HiddenBATHAuto.dto.client.AdminClientMemberUpdateRequest;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminClientDetailService {

    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final Environment environment;

    @Value("${spring.upload.path}")
    private String uploadPath;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp", "pdf");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public void updateCompany(Long companyId, AdminClientCompanyUpdateRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("회사가 존재하지 않습니다. ID=" + companyId));

        String companyName = trim(request.getCompanyName());
        String businessNumber = normalizeBusinessNumber(request.getBusinessNumber());
        String zipCode = trim(request.getZipCode());
        String doName = trim(request.getDoName());
        String siName = trim(request.getSiName());
        String guName = trim(request.getGuName());
        String roadAddress = trim(request.getRoadAddress());
        String detailAddress = trim(request.getDetailAddress());
        String licenseAction = trim(request.getLicenseAction()).toUpperCase();

        if (!StringUtils.hasText(companyName)) {
            throw new IllegalArgumentException("업체명은 필수입니다.");
        }
        if (request.getPoint() == null || request.getPoint() < 0) {
            throw new IllegalArgumentException("적립금은 0 이상이어야 합니다.");
        }
        if (businessNumber.length() != 10) {
            throw new IllegalArgumentException("사업자등록번호는 숫자 10자리여야 합니다.");
        }
        if (!StringUtils.hasText(zipCode)) {
            throw new IllegalArgumentException("우편번호는 필수입니다.");
        }
        if (!StringUtils.hasText(doName) || !StringUtils.hasText(siName) || !StringUtils.hasText(roadAddress)) {
            throw new IllegalArgumentException("주소는 필수입니다.");
        }

        boolean duplicatedBusinessNumber = companyRepository.existsByBusinessNumberAndIdNot(businessNumber, companyId);
        if (duplicatedBusinessNumber) {
            throw new IllegalArgumentException("이미 사용 중인 사업자등록번호입니다.");
        }

        MultipartFile newLicenseFile = request.getBusinessLicenseFile();

        boolean hasExistingLicense = StringUtils.hasText(company.getBusinessLicenseUrl());
        boolean hasNewLicense = (newLicenseFile != null && !newLicenseFile.isEmpty());

        if (!StringUtils.hasText(licenseAction)) {
            licenseAction = "KEEP";
        }

        if ("DELETE".equals(licenseAction) && !hasNewLicense) {
            throw new IllegalArgumentException("사업자등록증은 필수입니다. 삭제만 할 수 없고 유지 또는 새 파일 등록이 필요합니다.");
        }

        if ("REPLACE".equals(licenseAction) && !hasNewLicense) {
            throw new IllegalArgumentException("사업자등록증 교체 파일이 없습니다.");
        }

        if ("KEEP".equals(licenseAction) && !hasExistingLicense && !hasNewLicense) {
            throw new IllegalArgumentException("사업자등록증은 필수입니다.");
        }

        company.setCompanyName(companyName);
        company.setPoint(request.getPoint());
        company.setBusinessNumber(businessNumber);
        company.setZipCode(zipCode);
        company.setDoName(doName);
        company.setSiName(siName);
        company.setGuName(guName);
        company.setRoadAddress(roadAddress);
        company.setDetailAddress(detailAddress);
        company.setUpdatedAt(LocalDateTime.now());

        if ("REPLACE".equals(licenseAction)) {
            validateLicenseFile(newLicenseFile);
            deleteCompanyLicenseFile(company);
            saveCompanyLicenseFile(company, newLicenseFile);
        } else if ("DELETE".equals(licenseAction)) {
            deleteCompanyLicenseFile(company);
            if (hasNewLicense) {
                validateLicenseFile(newLicenseFile);
                saveCompanyLicenseFile(company, newLicenseFile);
            } else {
                throw new IllegalArgumentException("사업자등록증은 필수입니다.");
            }
        } else if ("KEEP".equals(licenseAction)) {
            if (!hasExistingLicense && hasNewLicense) {
                validateLicenseFile(newLicenseFile);
                saveCompanyLicenseFile(company, newLicenseFile);
            }
        }
    }

    public void updateMemberInfo(Long memberId, AdminClientMemberUpdateRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다. ID=" + memberId));

        String name = trim(request.getName());
        String phone = trim(request.getPhone());
        String email = trim(request.getEmail());
        String telephone = trim(request.getTelephone());

        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("이름은 필수입니다.");
        }
        if (!StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("연락처는 필수입니다.");
        }
        if (StringUtils.hasText(email) && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }

        member.setName(name);
        member.setPhone(phone);
        member.setEmail(email);
        member.setTelephone(telephone);
        member.setUpdatedAt(LocalDateTime.now());
    }

    private void validateLicenseFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("사업자등록증 파일이 없습니다.");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);

        if (!StringUtils.hasText(extension) || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("사업자등록증은 png, jpg, jpeg, gif, webp, pdf 파일만 가능합니다.");
        }
    }

    /**
     * 기존 회원가입 규칙과 동일:
     * 실제 저장 경로: {spring.upload.path}/{username}/signUp/licence/{저장파일명}
     * 접근 URL: /upload/{username}/signUp/licence/{저장파일명}
     */
    private void saveCompanyLicenseFile(Company company, MultipartFile file) {
        try {
            String username = resolveCompanyLicenseOwnerUsername(company);
            String originalFilename = file.getOriginalFilename();
            String extension = getExtension(originalFilename).toLowerCase();

            String safeOriginalFilename = Paths.get(originalFilename).getFileName().toString();
            String savedFilename = UUID.randomUUID() + "_" + safeOriginalFilename;

            String relativePath = username + "/signUp/licence";
            String normalizedUploadRoot = normalizeDir(uploadPath);
            String saveDir = Paths.get(normalizedUploadRoot, relativePath).toString();

            File dir = new File(saveDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            Path filePath = Paths.get(saveDir, savedFilename);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            company.setBusinessLicenseFilename(safeOriginalFilename);
            company.setBusinessLicensePath(filePath.toString());
            company.setBusinessLicenseUrl("/upload/" + relativePath + "/" + savedFilename);

        } catch (IOException e) {
            throw new RuntimeException("사업자등록증 저장 중 오류가 발생했습니다.", e);
        }
    }

    private void deleteCompanyLicenseFile(Company company) {
        try {
            if (StringUtils.hasText(company.getBusinessLicensePath())) {
                Path path = Paths.get(company.getBusinessLicensePath());
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("기존 사업자등록증 파일 삭제 중 오류가 발생했습니다.", e);
        }

        company.setBusinessLicenseFilename(null);
        company.setBusinessLicensePath(null);
        company.setBusinessLicenseUrl(null);
    }

    /**
     * 업데이트 시 username 기준 결정
     * 1) 대표자
     * 2) 첫 번째 소속 회원
     * 3) 없으면 예외
     */
    private String resolveCompanyLicenseOwnerUsername(Company company) {
        List<Member> companyMembers = memberRepository.findByCompany(company);

        Member representative = companyMembers.stream()
                .filter(member -> member.getRole() == MemberRole.CUSTOMER_REPRESENTATIVE)
                .min(Comparator.comparing(Member::getId))
                .orElse(null);

        if (representative != null && StringUtils.hasText(representative.getUsername())) {
            return representative.getUsername().trim();
        }

        Member firstMember = companyMembers.stream()
                .filter(member -> StringUtils.hasText(member.getUsername()))
                .min(Comparator.comparing(Member::getId))
                .orElse(null);

        if (firstMember != null) {
            return firstMember.getUsername().trim();
        }

        throw new IllegalArgumentException("회사 소속 회원의 username 을 찾을 수 없어 사업자등록증 저장 경로를 생성할 수 없습니다.");
    }

    private String normalizeBusinessNumber(String value) {
        return trim(value).replaceAll("\\D", "");
    }

    private String getExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeDir(String dir) {
        if (!StringUtils.hasText(dir)) return dir;

        String d = dir.replace("\\", "/").trim();

        String userHome = System.getProperty("user.home");
        if (StringUtils.hasText(userHome)) {
            userHome = userHome.replace("\\", "/");
            d = d.replace("${user.home}", userHome);

            if (d.equals("~")) {
                d = userHome;
            } else if (d.startsWith("~/")) {
                d = userHome + d.substring(1);
            }
        }

        if (!d.endsWith("/")) d = d + "/";
        return d;
    }
}