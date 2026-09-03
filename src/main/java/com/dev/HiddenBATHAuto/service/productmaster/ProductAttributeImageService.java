package com.dev.HiddenBATHAuto.service.productmaster;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.config.productmaster.ProductMasterImageProperties;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.AttributeImageResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.AttributeImagePolicyResponse;
import com.dev.HiddenBATHAuto.model.productmaster.ProductAttributeGroup;
import com.dev.HiddenBATHAuto.model.productmaster.ProductAttributeImage;
import com.dev.HiddenBATHAuto.model.productmaster.ProductAttributeValue;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductAttributeGroupRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductAttributeImageRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductAttributeValueRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductAttributeImageService {

    private static final String STORAGE_DIRECTORY = "product-master/attribute-images";
    private static final int SIGNATURE_READ_SIZE = 16;

    private final ProductAttributeImageRepository imageRepository;
    private final ProductAttributeGroupRepository groupRepository;
    private final ProductAttributeValueRepository valueRepository;
    private final ProductMasterImageProperties imageProperties;

    @Value("${spring.upload.path}")
    private String uploadPath;

    public AttributeImagePolicyResponse getPolicy() {
        return new AttributeImagePolicyResponse(
                imageProperties.resolvedMaxFileSizeBytes(),
                imageProperties.resolvedMaxFilesPerOwner(),
                List.of("image/jpeg", "image/png", "image/gif", "image/webp")
        );
    }

    public List<AttributeImageResponse> getGroupImages(Long groupId) {
        return imageRepository.findAllByGroup_IdOrderBySortOrderAscIdAsc(groupId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AttributeImageResponse> getValueImages(Long valueId) {
        return imageRepository.findAllByOptionValue_IdOrderBySortOrderAscIdAsc(valueId).stream()
                .map(this::toResponse)
                .toList();
    }

    public Map<Long, List<AttributeImageResponse>> getGroupImageMap(Collection<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return imageRepository.findAllByGroup_IdInOrderBySortOrderAscIdAsc(groupIds).stream()
                .collect(Collectors.groupingBy(
                        image -> image.getGroup().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(this::toResponse, Collectors.toList())
                ));
    }

    public Map<Long, List<AttributeImageResponse>> getValueImageMap(Collection<Long> valueIds) {
        if (valueIds == null || valueIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return imageRepository.findAllByOptionValue_IdInOrderBySortOrderAscIdAsc(valueIds).stream()
                .collect(Collectors.groupingBy(
                        image -> image.getOptionValue().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(this::toResponse, Collectors.toList())
                ));
    }

    @Transactional
    public List<AttributeImageResponse> uploadToGroup(Long groupId, List<MultipartFile> files, String actor) {
        ProductAttributeGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NoSuchElementException("옵션 그룹을 찾을 수 없습니다."));
        requireUploadFiles(files);
        return storeForGroup(group, files, actor);
    }

    @Transactional
    public List<AttributeImageResponse> uploadToValue(Long valueId, List<MultipartFile> files, String actor) {
        ProductAttributeValue value = valueRepository.findById(valueId)
                .orElseThrow(() -> new NoSuchElementException("옵션값을 찾을 수 없습니다."));
        requireUploadFiles(files);
        return storeForValue(value, files, actor);
    }

    @Transactional
    public List<AttributeImageResponse> storeForGroup(
            ProductAttributeGroup group,
            List<MultipartFile> files,
            String actor
    ) {
        Objects.requireNonNull(group, "옵션 그룹이 필요합니다.");
        if (group.getId() == null) {
            throw new IllegalStateException("옵션 그룹 저장 후 이미지를 등록할 수 있습니다.");
        }
        List<ProductAttributeImage> existing = imageRepository
                .findAllByGroup_IdOrderBySortOrderAscIdAsc(group.getId());
        store(group, null, files, actor, existing);
        return getGroupImages(group.getId());
    }

    @Transactional
    public List<AttributeImageResponse> storeForValue(
            ProductAttributeValue value,
            List<MultipartFile> files,
            String actor
    ) {
        Objects.requireNonNull(value, "옵션값이 필요합니다.");
        if (value.getId() == null) {
            throw new IllegalStateException("옵션값 저장 후 이미지를 등록할 수 있습니다.");
        }
        List<ProductAttributeImage> existing = imageRepository
                .findAllByOptionValue_IdOrderBySortOrderAscIdAsc(value.getId());
        store(null, value, files, actor, existing);
        return getValueImages(value.getId());
    }

    @Transactional
    public void deleteImage(Long imageId) {
        ProductAttributeImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new NoSuchElementException("이미지를 찾을 수 없습니다."));
        Path storedPath = resolveStoredPath(image.getStorageKey());
        imageRepository.delete(image);
        imageRepository.flush();
        deleteAfterCommit(List.of(storedPath));
    }

    public StoredImageResource loadPublicImage(String publicToken) {
        String token = publicToken == null ? "" : publicToken.trim();
        if (token.isEmpty() || token.length() > 36) {
            throw new NoSuchElementException("이미지를 찾을 수 없습니다.");
        }
        ProductAttributeImage image = imageRepository.findByPublicToken(token)
                .orElseThrow(() -> new NoSuchElementException("이미지를 찾을 수 없습니다."));
        Path path = resolveStoredPath(image.getStorageKey());
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new NoSuchElementException("이미지 파일을 찾을 수 없습니다.");
        }
        long actualSize;
        try {
            actualSize = Files.size(path);
        } catch (IOException exception) {
            throw new NoSuchElementException("이미지 파일을 읽을 수 없습니다.");
        }
        return new StoredImageResource(
                new FileSystemResource(path),
                image.getContentType(),
                image.getOriginalFilename(),
                actualSize
        );
    }

    public void deleteGroupFilesAfterCommit(Long groupId) {
        if (groupId == null) {
            return;
        }
        List<ProductAttributeImage> images = new ArrayList<>();
        images.addAll(imageRepository.findAllByGroup_IdOrderBySortOrderAscIdAsc(groupId));
        images.addAll(imageRepository.findAllByOptionValue_Group_IdOrderBySortOrderAscIdAsc(groupId));
        deleteAfterCommit(images.stream().map(image -> resolveStoredPath(image.getStorageKey())).toList());
    }

    public void deleteValueFilesAfterCommit(Long valueId) {
        if (valueId == null) {
            return;
        }
        List<Path> paths = imageRepository.findAllByOptionValue_IdOrderBySortOrderAscIdAsc(valueId).stream()
                .map(image -> resolveStoredPath(image.getStorageKey()))
                .toList();
        deleteAfterCommit(paths);
    }

    private void store(
            ProductAttributeGroup group,
            ProductAttributeValue value,
            List<MultipartFile> files,
            String actor,
            List<ProductAttributeImage> existing
    ) {
        List<MultipartFile> uploadFiles = usableFiles(files);
        if (uploadFiles.isEmpty()) {
            return;
        }

        int limit = imageProperties.resolvedMaxFilesPerOwner();
        if (existing.size() + uploadFiles.size() > limit) {
            throw new IllegalArgumentException("한 그룹 또는 옵션값에는 이미지를 최대 " + limit + "장까지 등록할 수 있습니다.");
        }

        List<ValidatedImage> validated = uploadFiles.stream().map(this::validate).toList();
        int sortOrder = existing.stream().mapToInt(ProductAttributeImage::getSortOrder).max().orElse(0) + 10;
        List<Path> createdPaths = new ArrayList<>();
        List<ProductAttributeImage> entities = new ArrayList<>();

        try {
            for (int index = 0; index < uploadFiles.size(); index++) {
                MultipartFile file = uploadFiles.get(index);
                ValidatedImage metadata = validated.get(index);
                String token = UUID.randomUUID().toString();
                String relativeKey = storageKey(token, metadata.extension());
                Path targetPath = resolveStoredPath(relativeKey);
                Files.createDirectories(targetPath.getParent());
                try (InputStream input = file.getInputStream()) {
                    Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                createdPaths.add(targetPath);

                ProductAttributeImage image = new ProductAttributeImage();
                image.setGroup(group);
                image.setOptionValue(value);
                image.setPublicToken(token);
                image.setStorageKey(relativeKey);
                image.setOriginalFilename(metadata.originalFilename());
                image.setContentType(metadata.contentType());
                image.setFileSize(file.getSize());
                image.setSortOrder(sortOrder);
                image.setCreatedBy(normalizeActor(actor));
                entities.add(image);
                sortOrder += 10;
            }
            imageRepository.saveAllAndFlush(entities);
            deleteOnRollback(createdPaths);
        } catch (IOException | RuntimeException exception) {
            createdPaths.forEach(this::deleteQuietly);
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalStateException("이미지를 저장하지 못했습니다. 저장 경로와 파일을 확인해 주세요.", exception);
        }
    }

    private ValidatedImage validate(MultipartFile file) {
        String originalFilename = sanitizeOriginalFilename(file.getOriginalFilename());
        if (file.getSize() <= 0) {
            throw new IllegalArgumentException("비어 있는 이미지는 등록할 수 없습니다: " + originalFilename);
        }
        long maxBytes = imageProperties.resolvedMaxFileSizeBytes();
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException(
                    "이미지 한 장은 최대 " + formatMegabytes(maxBytes) + "MB까지 등록할 수 있습니다: " + originalFilename
            );
        }

        byte[] signature;
        try (InputStream input = file.getInputStream()) {
            signature = input.readNBytes(SIGNATURE_READ_SIZE);
        } catch (IOException exception) {
            throw new IllegalArgumentException("이미지를 읽을 수 없습니다: " + originalFilename, exception);
        }

        DetectedType detected = detectType(signature, signature.length);
        if (detected == null) {
            throw new IllegalArgumentException(
                    "지원하지 않거나 실제 이미지 형식이 올바르지 않습니다: " + originalFilename
                            + " (JPG, PNG, GIF, WEBP만 가능)"
            );
        }
        return new ValidatedImage(originalFilename, detected.extension(), detected.contentType());
    }

    private DetectedType detectType(byte[] bytes, int length) {
        if (matches(bytes, length, 0xFF, 0xD8, 0xFF)) {
            return new DetectedType("jpg", "image/jpeg");
        }
        if (matches(bytes, length, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return new DetectedType("png", "image/png");
        }
        if (matches(bytes, length, 0x47, 0x49, 0x46, 0x38, 0x37, 0x61)
                || matches(bytes, length, 0x47, 0x49, 0x46, 0x38, 0x39, 0x61)) {
            return new DetectedType("gif", "image/gif");
        }
        if (matches(bytes, length, 0x52, 0x49, 0x46, 0x46)
                && matchesAt(bytes, length, 8, 0x57, 0x45, 0x42, 0x50)) {
            return new DetectedType("webp", "image/webp");
        }
        return null;
    }

    private boolean matches(byte[] bytes, int length, int... expected) {
        return matchesAt(bytes, length, 0, expected);
    }

    private boolean matchesAt(byte[] bytes, int length, int offset, int... expected) {
        if (length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if ((bytes[offset + index] & 0xFF) != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private List<MultipartFile> usableFiles(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream().filter(Objects::nonNull).filter(file -> !file.isEmpty()).toList();
    }

    private void requireUploadFiles(List<MultipartFile> files) {
        if (usableFiles(files).isEmpty()) {
            throw new IllegalArgumentException("등록할 이미지 파일을 한 장 이상 선택해 주세요.");
        }
    }

    private AttributeImageResponse toResponse(ProductAttributeImage image) {
        return new AttributeImageResponse(
                image.getId(),
                "/product-spec/images/" + image.getPublicToken(),
                image.getOriginalFilename(),
                image.getContentType(),
                image.getFileSize(),
                image.getSortOrder()
        );
    }

    private String storageKey(String token, String extension) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        return STORAGE_DIRECTORY + "/" + datePath + "/" + token + "." + extension;
    }

    private Path resolveStoredPath(String storageKey) {
        if (!StringUtils.hasText(uploadPath)) {
            throw new IllegalStateException("spring.upload.path 설정이 비어 있어 이미지를 저장할 수 없습니다.");
        }
        String normalizedUploadPath = normalizeDir(uploadPath);
        Path basePath = Paths.get(normalizedUploadPath).toAbsolutePath().normalize();
        Path targetPath = basePath.resolve(storageKey == null ? "" : storageKey).normalize();
        if (!targetPath.startsWith(basePath) || targetPath.equals(basePath)) {
            throw new IllegalStateException("제품관리 이미지 저장 경로가 올바르지 않습니다.");
        }
        return targetPath;
    }

    private String sanitizeOriginalFilename(String originalFilename) {
        String cleaned = StringUtils.cleanPath(Objects.requireNonNullElse(originalFilename, "image"))
                .replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        String filename = slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
        if (!StringUtils.hasText(filename)) {
            filename = "image";
        }
        return filename.length() <= 255 ? filename : filename.substring(filename.length() - 255);
    }

    private String normalizeActor(String actor) {
        String normalized = actor == null || actor.isBlank() ? "SYSTEM" : actor.trim();
        return normalized.length() <= 100 ? normalized : normalized.substring(0, 100);
    }

    private String normalizeDir(String dir) {
        String normalized = dir.replace('\\', '/').trim();
        String userHome = System.getProperty("user.home");
        if (StringUtils.hasText(userHome)) {
            String normalizedHome = userHome.replace('\\', '/');
            normalized = normalized.replace("${user.home}", normalizedHome);
            if (normalized.equals("~")) {
                normalized = normalizedHome;
            } else if (normalized.startsWith("~/")) {
                normalized = normalizedHome + normalized.substring(1);
            }
        }
        return normalized;
    }

    private long formatMegabytes(long bytes) {
        return Math.max(1L, bytes / (1024L * 1024L));
    }

    private void deleteOnRollback(List<Path> paths) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    paths.forEach(ProductAttributeImageService.this::deleteQuietly);
                }
            }
        });
    }

    private void deleteAfterCommit(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            paths.forEach(this::deleteQuietly);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                paths.forEach(ProductAttributeImageService.this::deleteQuietly);
            }
        });
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            // DB 커밋 결과를 되돌릴 수 없으므로 로그를 기준으로 고아 파일 정리를 재시도합니다.
            log.warn("제품관리 이미지 파일을 삭제하지 못했습니다: {}", path, exception);
        }
    }

    public record StoredImageResource(
            Resource resource,
            String contentType,
            String originalFilename,
            long fileSize
    ) {
    }

    private record ValidatedImage(String originalFilename, String extension, String contentType) {
    }

    private record DetectedType(String extension, String contentType) {
    }
}
