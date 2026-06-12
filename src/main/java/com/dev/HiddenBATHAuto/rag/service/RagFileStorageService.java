package com.dev.HiddenBATHAuto.rag.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.rag.config.RagUploadProperties;
import com.dev.HiddenBATHAuto.rag.repository.RagRepository;

@Service
public class RagFileStorageService {

    private final RagUploadProperties properties;
    private final RagRepository repository;

    public RagFileStorageService(RagUploadProperties properties, RagRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    public Map<String, Object> saveAsset(UUID projectId,
                                         UUID versionId,
                                         String ownerType,
                                         String ownerKey,
                                         MultipartFile file,
                                         String note) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드 파일이 비어 있습니다.");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new IllegalArgumentException("파일 크기가 허용 범위를 초과했습니다. 파일명: " + safeOriginalName(file));
        }

        try {
            LocalDate today = LocalDate.now();
            Path root = Paths.get(properties.getBaseDir()).toAbsolutePath().normalize();
            Path dir = root.resolve(properties.getAssetSubDir())
                    .resolve(today.toString())
                    .resolve(projectId.toString())
                    .resolve(versionId.toString())
                    .normalize();
            Files.createDirectories(dir);

            String originalFilename = safeOriginalName(file);
            String ext = extension(originalFilename);
            String storedFilename = UUID.randomUUID() + (ext.isBlank() ? "" : "." + ext);
            Path target = dir.resolve(storedFilename).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalStateException("업로드 저장 경로가 올바르지 않습니다.");
            }
            file.transferTo(target);

            String url = joinUrl(properties.getPublicUrlPrefix(), today.toString(), projectId.toString(), versionId.toString(), storedFilename);
            Map<String, Object> asset = repository.insertAsset(
                    UUID.randomUUID(),
                    projectId,
                    versionId,
                    ownerType,
                    ownerKey,
                    originalFilename,
                    storedFilename,
                    file.getContentType(),
                    target.toString(),
                    url,
                    note
            );

            Map<String, Object> result = new LinkedHashMap<>(asset);
            result.put("size", file.getSize());
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장 실패: " + e.getMessage(), e);
        }
    }

    private String safeOriginalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (!StringUtils.hasText(name)) return "upload.bin";
        return Paths.get(name).getFileName().toString().replaceAll("[\\r\\n]", "_");
    }

    private String extension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) return "";
        return filename.substring(idx + 1).toLowerCase();
    }

    private String joinUrl(String prefix, String... parts) {
        StringBuilder sb = new StringBuilder(prefix == null ? "" : prefix.replaceAll("/+$", ""));
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                sb.append('/').append(part.replaceAll("^/+", ""));
            }
        }
        return sb.toString();
    }
}
