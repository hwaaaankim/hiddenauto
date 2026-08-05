package com.dev.HiddenBATHAuto.orderExcelUpload.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderImage;
import com.dev.HiddenBATHAuto.orderExcelUpload.config.OrderExcelUploadImageProperties;
import com.dev.HiddenBATHAuto.orderExcelUpload.repository.OrderExcelOrderImageRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderExcelUploadImageStorageService {

    private static final String MANAGEMENT_UPLOAD_TYPE = "MANAGEMENT";

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "jfif", "png", "gif", "webp", "bmp"
    );

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/jpg", "jpg"),
            Map.entry("image/jfif", "jpg"),
            Map.entry("image/pjpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/gif", "gif"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/bmp", "bmp"),
            Map.entry("image/x-ms-bmp", "bmp")
    );

    private final OrderExcelOrderImageRepository orderImageRepository;
    private final OrderExcelUploadImageProperties imageProperties;

    @Value("${spring.upload.path}")
    private String uploadPath;

    /**
     * Task/Order 저장 전에 이미지 전체를 검사합니다.
     * 파일 하나 또는 누적 용량이 업무 상한을 넘으면 DB 저장을 시작하지 않습니다.
     */
    public void validateManagementImages(Map<String, List<MultipartFile>> filesByPartName) {
        if (filesByPartName == null || filesByPartName.isEmpty()) {
            return;
        }

        long totalSize = 0L;
        for (Map.Entry<String, List<MultipartFile>> entry : filesByPartName.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().startsWith("images_")) {
                continue;
            }

            List<MultipartFile> files = entry.getValue();
            if (files == null) {
                continue;
            }

            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                validateFile(file);
                totalSize = safeAdd(totalSize, file.getSize());
            }
        }

        long maxTotalSize = imageProperties.resolvedMaxTotalSizeBytes();
        if (totalSize > maxTotalSize) {
            throw new IllegalArgumentException(
                    "첨부 이미지 전체 용량이 최대 허용량을 초과했습니다. 현재 "
                            + formatFileSize(totalSize)
                            + " / 최대 "
                            + formatFileSize(maxTotalSize)
            );
        }
    }

    /**
     * 이미 저장된 Order에 MANAGEMENT 이미지를 연결하고 DB에 명시적으로 저장합니다.
     *
     * 기존처럼 이미지를 추가한 뒤 managed Order를 다시 repository.save(merge)하지 않으므로
     * 양방향 연관관계 hashCode가 호출되는 경로도 제거됩니다.
     */
    public List<OrderImage> storeManagementImages(Order order, List<MultipartFile> files) {
        if (order == null || files == null || files.isEmpty()) {
            return List.of();
        }

        if (order.getId() == null) {
            throw new IllegalArgumentException("이미지 저장은 Order 저장 후에만 가능합니다. order.id가 없습니다.");
        }

        if (order.getTask() == null || order.getTask().getId() == null) {
            throw new IllegalArgumentException("이미지 저장은 Task 저장 후에만 가능합니다. task.id가 없습니다.");
        }

        List<OrderImage> stored = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            stored.add(storeOne(order, file));
        }

        if (!stored.isEmpty()) {
            orderImageRepository.saveAll(stored);
        }
        return stored;
    }

    private OrderImage storeOne(Order order, MultipartFile file) {
        ValidatedImageFile validated = validateFile(file);

        Long taskId = order.getTask().getId();
        Long orderId = order.getId();
        String dateFolder = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String savedFilename = UUID.randomUUID() + "." + validated.extension();

        Path basePath = resolveBasePath();
        Path uploadDir = basePath
                .resolve("order")
                .resolve("management")
                .resolve(String.valueOf(taskId))
                .resolve(String.valueOf(orderId))
                .resolve(dateFolder)
                .normalize();

        Path targetPath = uploadDir.resolve(savedFilename).normalize();
        if (!targetPath.startsWith(basePath)) {
            throw new IllegalArgumentException("파일 저장 경로가 올바르지 않습니다.");
        }

        try {
            Files.createDirectories(uploadDir);
            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            deleteQuietly(targetPath);
            throw new IllegalArgumentException(
                    "이미지 저장 중 오류가 발생했습니다: " + validated.originalFilename(),
                    e
            );
        }

        registerRollbackCleanup(targetPath);

        OrderImage orderImage = new OrderImage();
        orderImage.setType(MANAGEMENT_UPLOAD_TYPE);
        orderImage.setFilename(validated.originalFilename());
        orderImage.setPath(targetPath.toString().replace("\\", "/"));
        orderImage.setUrl(
                "/upload/order/management/"
                        + taskId
                        + "/"
                        + orderId
                        + "/"
                        + dateFolder
                        + "/"
                        + savedFilename
        );
        orderImage.setUploadedAt(LocalDateTime.now());

        order.addOrderImage(orderImage);
        order.setUpdatedAt(LocalDateTime.now());
        return orderImage;
    }

    private ValidatedImageFile validateFile(MultipartFile file) {
        String originalFilename = sanitizeOriginalFilename(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        String extension = extension(originalFilename);

        if (extension.isBlank()) {
            extension = EXTENSION_BY_CONTENT_TYPE.getOrDefault(contentType, "");
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "지원하지 않는 이미지 형식입니다: " + originalFilename
                            + " (jpg, jpeg, jfif, png, gif, webp, bmp만 가능)"
            );
        }

        if (StringUtils.hasText(contentType)
                && !"application/octet-stream".equals(contentType)
                && !EXTENSION_BY_CONTENT_TYPE.containsKey(contentType)) {
            throw new IllegalArgumentException(
                    "이미지 MIME 형식이 올바르지 않습니다: " + originalFilename + " (" + contentType + ")"
            );
        }

        long maxFileSize = imageProperties.resolvedMaxFileSizeBytes();
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                    "이미지 한 파일의 최대 용량을 초과했습니다: "
                            + originalFilename
                            + " · 현재 "
                            + formatFileSize(file.getSize())
                            + " / 최대 "
                            + formatFileSize(maxFileSize)
            );
        }

        return new ValidatedImageFile(originalFilename, extension, contentType);
    }

    private Path resolveBasePath() {
        if (!StringUtils.hasText(uploadPath)) {
            throw new IllegalStateException("spring.upload.path 설정이 비어 있어 이미지를 저장할 수 없습니다.");
        }
        return Paths.get(normalizeDir(uploadPath)).toAbsolutePath().normalize();
    }

    private String sanitizeOriginalFilename(String originalFilename) {
        String cleaned = StringUtils.cleanPath(Objects.requireNonNullElse(originalFilename, "image"))
                .replace("\\", "/");
        int lastSlash = cleaned.lastIndexOf('/');
        String filename = lastSlash >= 0 ? cleaned.substring(lastSlash + 1) : cleaned;
        return StringUtils.hasText(filename) ? filename : "image";
    }

    private String extension(String filename) {
        String safeName = filename == null ? "" : filename.trim();
        int dotIndex = safeName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == safeName.length() - 1) {
            return "";
        }
        return safeName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        int semicolonIndex = contentType.indexOf(';');
        String normalized = semicolonIndex >= 0
                ? contentType.substring(0, semicolonIndex)
                : contentType;
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private long safeAdd(long current, long value) {
        if (value <= 0) {
            return current;
        }
        if (Long.MAX_VALUE - current < value) {
            return Long.MAX_VALUE;
        }
        return current + value;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(Locale.KOREA, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024.0) {
            return String.format(Locale.KOREA, "%.1f MB", mb);
        }
        return String.format(Locale.KOREA, "%.2f GB", mb / 1024.0);
    }

    private void registerRollbackCleanup(Path targetPath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(targetPath);
                }
            }
        });
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 원래 예외를 가리지 않기 위한 best-effort 정리입니다.
        }
    }

    private String normalizeDir(String dir) {
        if (!StringUtils.hasText(dir)) {
            return dir;
        }

        String normalized = dir.replace("\\", "/").trim();
        String userHome = System.getProperty("user.home");

        if (StringUtils.hasText(userHome)) {
            userHome = userHome.replace("\\", "/");
            normalized = normalized.replace("${user.home}", userHome);

            if (normalized.equals("~")) {
                normalized = userHome;
            } else if (normalized.startsWith("~/")) {
                normalized = userHome + normalized.substring(1);
            }
        }

        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    private record ValidatedImageFile(
            String originalFilename,
            String extension,
            String contentType
    ) {
    }
}
