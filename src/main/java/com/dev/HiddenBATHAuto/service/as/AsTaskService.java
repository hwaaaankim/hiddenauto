package com.dev.HiddenBATHAuto.service.as;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AsTaskService {

	private final AsTaskRepository asTaskRepository;
	private final AsImageRepository asImageRepository;
	private final DistrictRepository districtRepository;
	private final MemberRegionRepository memberRegionRepository;
	private final MemberRepository memberRepository;
	
	@Value("${spring.upload.path}")
	private String uploadPath;
	
	public Page<AsTask> getAsTasks(Member handler, String dateType, LocalDate date, AsStatus status, Pageable pageable) {
	    LocalDateTime start = (date != null ? date : LocalDate.now()).atStartOfDay();
	    LocalDateTime end = start.plusDays(1);

	    if ("requested".equalsIgnoreCase(dateType)) {
	        return asTaskRepository.findByRequestedDate(handler.getId(), status, start, end, pageable);
	    } else {
	        return asTaskRepository.findByProcessedDate(handler.getId(), status, start, end, pageable);
	    }
	}

	public Page<AsTask> getFilteredAsList(Long memberId, AsStatus statuses, LocalDate asDate, Pageable pageable) {
	    if (asDate != null) {
	        LocalDateTime startOfDay = asDate.atStartOfDay();
	        LocalDateTime endOfDay = asDate.plusDays(1).atStartOfDay();
	        return asTaskRepository.findByFilterWithDateRange(memberId, statuses, startOfDay, endOfDay, pageable);
	    } else {
	        return asTaskRepository.findByFilterWithDateRange(memberId, statuses, null, null, pageable);
	    }
	}

	public List<AsTask> getFilteredAsList(Long handlerId, AsStatus status, LocalDate date) {
	    LocalDateTime start = date.atStartOfDay();
	    LocalDateTime end = date.plusDays(1).atStartOfDay();

	    return asTaskRepository.findByFilterWithDateRangeNonPageable(
	            handlerId,
	            status,
	            start,
	            end
	    );
	}
	
	@Transactional
    public void updateAsTask(Long id, Integer price, String statusStr, Long assignedHandlerId) {
        AsTask asTask = getAsDetail(id);

        // ✅ 상태 파싱
        AsStatus status = AsStatus.valueOf(statusStr);

        // ✅ 비용이 null인 경우 0 처리
        asTask.setPrice(price == null ? 0 : price);

        // ✅ 담당자 지정 필수
        if (assignedHandlerId == null) {
            throw new IllegalArgumentException("담당자를 반드시 지정해야 합니다.");
        }

        Member handler = memberRepository.findById(assignedHandlerId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 담당자입니다."));

        asTask.setAssignedHandler(handler);
        asTask.setStatus(status);
        asTask.setUpdatedAt(LocalDateTime.now());

        asTaskRepository.save(asTask);
    }	
	
    public AsTask getAsDetail(Long id) {
        return asTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 AS 요청을 찾을 수 없습니다. ID: " + id));
    }

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
