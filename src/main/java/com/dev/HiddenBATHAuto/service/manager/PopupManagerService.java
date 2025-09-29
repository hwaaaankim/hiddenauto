package com.dev.HiddenBATHAuto.service.manager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.manager.Popup;
import com.dev.HiddenBATHAuto.repository.manager.PopupManagerRepository;

import lombok.RequiredArgsConstructor;

/**
 * 팝업 관리 서비스 - 정렬: dispOrder ASC, createdAt DESC - 신규 등록 시 dispOrder = (현재 최대값 +
 * 1) - 드래그앤드랍 정렬 저장: 전달된 ID 순서대로 dispOrder를 1..N 재부여
 */
@Service
@RequiredArgsConstructor
public class PopupManagerService {

	private final PopupManagerRepository repository;

	@Value("${spring.upload.path}")
	private String uploadPath; // ex) /data/uploads

	private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	/*
	 * ========================= 조회 =========================
	 */

	/** (호환 유지) 리스트: 기존 메서드명 유지하되 내부 정렬을 'dispOrder ASC, createdAt DESC'로 변경 */
	@Transactional(readOnly = true)
	public List<Popup> listAllDesc() {
		return repository.findAllByOrderByDispOrderAscCreatedAtDesc();
	}
	
	// PopupManagerService
	@Transactional(readOnly = true)
	public List<Popup> listActiveOrderByIndex() {
	    var now = java.time.LocalDateTime.now();
	    return repository.findByStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByDispOrderAscCreatedAtDesc(now, now);
	}
	
	/** 페이지 조회도 동일 정렬 기준으로 */
	@Transactional(readOnly = true)
	public Page<Popup> page(int page, int size) {
		return repository.findAllByOrderByDispOrderAscCreatedAtDesc(PageRequest.of(page, size));
	}

	/*
	 * ========================= 파일 저장/URL 변환 유틸 =========================
	 */

	private String ensureExt(MultipartFile file) {
		String orig = file.getOriginalFilename();
		String ext = (orig != null && orig.contains(".")) ? orig.substring(orig.lastIndexOf('.') + 1) : "";
		if (!StringUtils.hasText(ext))
			ext = "bin";
		return ext.toLowerCase();
	}

	private String saveImage(MultipartFile image) throws IOException {
		// ✅ null / empty 가드
		MultipartFile f = Objects.requireNonNull(image, "이미지는 필수입니다.");
		if (f.isEmpty()) {
			throw new IllegalArgumentException("이미지는 필수입니다.");
		}

		String day = LocalDate.now().format(DAY_FMT);
		String ext = ensureExt(f);
		String filename = UUID.randomUUID() + "." + ext;

		Path dir = Paths.get(uploadPath, "popup", day);
		Files.createDirectories(dir);

		Path target = dir.resolve(filename);
		Files.copy(f.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

		return target.toAbsolutePath().toString();
	}

	private static void deleteIfExistsSilently(String path) {
		if (!StringUtils.hasText(path))
			return;
		try {
			Files.deleteIfExists(Path.of(path));
		} catch (Exception ignored) {
		}
	}

	private String toPublicUrl(String absolutePath) {
		// absolutePath: /data/uploads/popup/2025-09-29/uuid.png
		// uploadPath : /data/uploads
		// → /upload/popup/2025-09-29/uuid.png
		String normUpload = Paths.get(uploadPath).toAbsolutePath().normalize().toString();
		String normAbs = Paths.get(absolutePath).toAbsolutePath().normalize().toString();
		String relative = normAbs.replaceFirst("^" + java.util.regex.Pattern.quote(normUpload), "");
		relative = relative.replace('\\', '/'); // Windows 대비
		if (!relative.startsWith("/"))
			relative = "/" + relative;
		return "/upload" + relative;
	}

	/*
	 * ========================= 등록/수정/삭제 =========================
	 */

	/** 신규 등록: dispOrder = 현재 최대값 + 1 */
	@Transactional
	public Popup insert(MultipartFile image, Boolean linkEnabled, String linkUrl, LocalDateTime startAt,
			LocalDateTime endAt) throws IOException {

		LocalDateTime s = Objects.requireNonNull(startAt, "게시 시작일을 입력하세요.");
		LocalDateTime e = Objects.requireNonNull(endAt, "게시 종료일을 입력하세요.");
		if (e.isBefore(s)) {
			throw new IllegalArgumentException("종료일이 시작일보다 빠를 수 없습니다.");
		}

		String imagePath = saveImage(image);
		String imageUrl = toPublicUrl(imagePath);

		boolean useLink = (linkEnabled != null && linkEnabled);
		if (useLink) {
			if (!StringUtils.hasText(linkUrl)) {
				throw new IllegalArgumentException("연결 URL을 입력하세요.");
			}
		} else {
			linkUrl = null;
		}

		// ✅ dispOrder = (최대값 + 1), 없으면 1부터 시작
		int nextOrder = repository.findTopByOrderByDispOrderDesc()
				.map(p -> (p.getDispOrder() == null ? 0 : p.getDispOrder()) + 1).orElse(1);

		Popup popup = Popup.builder().imagePath(imagePath).imageUrl(imageUrl).linkEnabled(useLink).linkUrl(linkUrl)
				.startAt(s).endAt(e).dispOrder(nextOrder).build();

		return repository.save(popup);
	}

	@Transactional
	public Popup update(Long id, MultipartFile newImage, // optional
			Boolean linkEnabled, String linkUrl, LocalDateTime startAt, LocalDateTime endAt) throws IOException {

		Popup popup = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("팝업을 찾을 수 없습니다."));

		LocalDateTime s = Objects.requireNonNull(startAt, "게시 시작일을 입력하세요.");
		LocalDateTime e = Objects.requireNonNull(endAt, "게시 종료일을 입력하세요.");
		if (e.isBefore(s)) {
			throw new IllegalArgumentException("종료일이 시작일보다 빠를 수 없습니다.");
		}

		// 선택 이미지 교체
		if (newImage != null && !newImage.isEmpty()) {
			String oldPath = popup.getImagePath();
			String imagePath = saveImage(newImage);
			String imageUrl = toPublicUrl(imagePath);
			popup.setImagePath(imagePath);
			popup.setImageUrl(imageUrl);
			deleteIfExistsSilently(oldPath);
		}

		boolean useLink = (linkEnabled != null && linkEnabled);
		if (useLink) {
			if (!StringUtils.hasText(linkUrl)) {
				throw new IllegalArgumentException("연결 URL을 입력하세요.");
			}
			popup.setLinkUrl(linkUrl);
		} else {
			popup.setLinkUrl(null);
		}

		popup.setLinkEnabled(useLink);
		popup.setStartAt(s);
		popup.setEndAt(e);

		return popup;
	}

	@Transactional
	public void delete(Long id) {
		Popup popup = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("팝업을 찾을 수 없습니다."));
		String path = popup.getImagePath();
		repository.delete(popup);
		deleteIfExistsSilently(path);
	}

	/*
	 * ========================= 순서 일괄 업데이트 (드래그 저장용) =========================
	 */

	/**
	 * 전달된 ID 순서대로 dispOrder = 1..N 재부여 - 클라이언트에서 모든 카드의 data-id를 DOM 순서로 보내는 것을 가정
	 */
	@Transactional
	public void updateOrderByIds(List<Long> orderedIds) {
		if (orderedIds == null || orderedIds.isEmpty())
			return;

		int order = 1;
		for (Long id : orderedIds) {
			repository.updateDispOrder(id, order++);
		}
	}
}