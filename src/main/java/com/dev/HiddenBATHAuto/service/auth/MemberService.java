package com.dev.HiddenBATHAuto.service.auth;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;

@Configuration
@Service
public class MemberService {

	@Autowired
	MemberRepository memberRepository;
	
	@Autowired
	CompanyRepository companyRepository;
	
	@Autowired
	PasswordEncoder passwordEncoder;
	
	@Value("${spring.upload.path}")
    private String uploadPath;
	
	public Member insertMember(Member member) {
		String encodedPassword = passwordEncoder.encode(member.getPassword());
		member.setPassword(encodedPassword);
		member.setRole(member.getRole());
		return memberRepository.save(member);

	}
	
	public void registerCustomerRepresentative(Company company, Member member, String role, MultipartFile file) {
        // 1. MemberRole 설정
        MemberRole memberRole = MemberRole.valueOf(role);
        member.setRole(memberRole);

        // 2. registrationKey 생성
        String registrationKey = UUID.randomUUID().toString().substring(0, 8);
        company.setRegistrationKey(registrationKey);

        // 3. 주소 가공 처리 (siName → si + gu 분리)
        refineAddressFields(company);

        // 4. 필수 주소값 유효성 검사
        if (company.getDoName() == null || company.getDoName().isBlank()) {
            throw new IllegalArgumentException("도 이름(doName)이 누락되었습니다.");
        }
        if (company.getRoadAddress() == null || company.getRoadAddress().isBlank()) {
            throw new IllegalArgumentException("주소 정보가 누락되었습니다.");
        }

        // 5. Company 저장
        Company savedCompany = companyRepository.save(company);

        // 6. 사업자등록증 파일 저장
        if (file != null && !file.isEmpty()) {
            try {
                String originalFilename = file.getOriginalFilename();
                String username = member.getUsername();
                String saveDir = uploadPath + "/" + username + "/signUp/licence";
                File dir = new File(saveDir);
                if (!dir.exists()) dir.mkdirs();

                Path filePath = Paths.get(saveDir, originalFilename);
                file.transferTo(filePath.toFile());

                savedCompany.setBusinessLicenseFilename(originalFilename);
                savedCompany.setBusinessLicensePath(filePath.toString());
                savedCompany.setBusinessLicenseUrl("/files/" + username + "/signUp/licence/" + originalFilename);

                companyRepository.save(savedCompany);

            } catch (Exception e) {
                throw new RuntimeException("파일 업로드 실패: " + e.getMessage());
            }
        }

        // 7. Member 설정 및 저장
        String encodedPassword = passwordEncoder.encode(member.getPassword());
        member.setPassword(encodedPassword);
        member.setCompany(savedCompany);
        member.setEnabled(true);
        memberRepository.save(member);
    }

    public void registerCustomerEmployee(Member member, String registrationKey) {
        if (registrationKey == null || registrationKey.isBlank()) {
            throw new IllegalArgumentException("업체코드를 입력해야 합니다.");
        }

        Company company = companyRepository.findByRegistrationKey(registrationKey)
                .orElseThrow(() -> new IllegalArgumentException("입력한 업체코드에 해당하는 회사가 존재하지 않습니다."));

        String encodedPassword = passwordEncoder.encode(member.getPassword());
        member.setPassword(encodedPassword);
        member.setCompany(company);
        member.setRole(MemberRole.CUSTOMER_EMPLOYEE);
        member.setEnabled(true);

        memberRepository.save(member);
    }

    /**
     * 주소에서 siName/guName을 분리하거나 보정함
     */
    private void refineAddressFields(Company company) {
        String si = company.getSiName();
        if (si != null && !si.isBlank()) {
            String[] parts = si.trim().split(" ");
            if (parts.length == 2) {
                // 예: "용인시 수지구"
                company.setSiName(parts[0]);
                company.setGuName(parts[1]);
            } else if (parts.length == 1) {
                // 예: "관악구"
                company.setGuName(parts[0]);
                company.setSiName(""); // 시 없음
            } else if (parts.length > 2) {
                // 예외 처리: 가장 앞은 시, 그다음은 구로
                company.setSiName(parts[0]);
                company.setGuName(parts[1]);
            }
        }
    }
}
