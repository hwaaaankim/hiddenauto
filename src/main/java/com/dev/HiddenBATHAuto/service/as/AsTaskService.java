package com.dev.HiddenBATHAuto.service.as;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.auth.District;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRegion;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.task.AsImage;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.repository.as.AsImageRepository;
import com.dev.HiddenBATHAuto.repository.as.AsTaskRepository;
import com.dev.HiddenBATHAuto.repository.auth.DistrictRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRegionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AsTaskService {

	private final AsTaskRepository asTaskRepository;
	private final AsImageRepository asImageRepository;
	private final DistrictRepository districtRepository;
	private final MemberRegionRepository memberRegionRepository;

	@Value("${spring.upload.path}")
	private String uploadPath;

	public AsTask submitAsTask(AsTask task, List<MultipartFile> images, Member member) throws IOException {
		task.setRequestedBy(member);
		task.setRequestedAt(LocalDateTime.now());
		task.setStatus(AsStatus.REQUESTED);

		// 주소 파싱 → 도/시/구 세팅
		refineAddressFromRoad(task);

		// 담당자 자동 배정
		assignAsHandlerIfPossible(task);

		AsTask savedTask = asTaskRepository.save(task);

		// 이미지 저장
		String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		Path dir = Paths.get(uploadPath, "as", String.valueOf(member.getId()), dateStr);
		Files.createDirectories(dir);

		for (MultipartFile file : images) {
			if (file.isEmpty())
				continue;

			String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
			Path filePath = dir.resolve(filename);
			file.transferTo(filePath.toFile());

			AsImage image = new AsImage();
			image.setAsTask(savedTask);
			image.setFilename(filename);
			image.setPath(filePath.toString());
			image.setUrl("/uploads/as/" + member.getId() + "/" + dateStr + "/" + filename);
			image.setType("REQUEST");

			asImageRepository.save(image);
		}

		return savedTask;
	}

	private void refineAddressFromRoad(AsTask task) {
		String full = task.getRoadAddress();
		if (full == null || full.isBlank())
			return;

		String[] tokens = full.trim().split("\\s+");
		String doName = "", siName = "", guName = "";

		if (tokens.length >= 1)
			doName = tokens[0];

		for (int i = 1; i < tokens.length; i++) {
			String word = tokens[i];
			if (word.endsWith("시") && siName.isBlank())
				siName = word;
			else if (word.endsWith("구") && guName.isBlank())
				guName = word;
			if (!siName.isBlank() && !guName.isBlank())
				break;
		}

		if (siName.isBlank() && guName.isBlank() && tokens.length >= 2)
			guName = tokens[1];

		task.setDoName(doName);
		task.setSiName(siName);
		task.setGuName(guName);
	}

	private void assignAsHandlerIfPossible(AsTask task) {
		String doName = task.getDoName();
		String siName = task.getSiName();
		String guName = task.getGuName();

		if (guName == null || doName == null) {
			System.out.println("❌ 구 또는 도 정보 부족. 배정 중단");
			return;
		}

		String siKeyword = (siName == null || siName.isBlank()) ? null : siName;

		Optional<District> districtOpt = districtRepository.findByAddressPartsSingleNative(guName, doName, siKeyword);
		if (districtOpt.isEmpty()) {
			System.out.println("❌ 지역 일치 실패. AS 담당자 배정 불가");
			return;
		}

		District district = districtOpt.get();
		List<MemberRegion> matchedRegions = memberRegionRepository.findByDistrict(district);

		for (MemberRegion region : matchedRegions) {
			Member m = region.getMember();
			if (m.getRole() == MemberRole.INTERNAL_EMPLOYEE && "AS팀".equals(m.getTeam().getName())) {
				task.setAssignedHandler(m);
				task.setAssignedTeam(m.getTeam());

				System.out.println("✅ AS 담당자 배정 완료: " + m.getUsername());
				return;
			}
		}

		System.out.println("❌ AS 담당자 배정 실패 (AS팀 조건 불일치)");
	}
}
