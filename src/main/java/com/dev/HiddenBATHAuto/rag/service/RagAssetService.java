package com.dev.HiddenBATHAuto.rag.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.rag.config.RagUploadProperties;
import com.dev.HiddenBATHAuto.rag.repository.RagRepository;

@Service
public class RagAssetService {

    private final RagRepository repository;
    private final RagUploadProperties uploadProperties;

    @Value("${spring.upload.path}")
    private String uploadPath;

    public RagAssetService(RagRepository repository, RagUploadProperties uploadProperties) {
        this.repository = repository;
        this.uploadProperties = uploadProperties;
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> upload(UUID projectId,
                                      UUID versionId,
                                      String ownerType,
                                      String ownerKey,
                                      String note,
                                      MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 이미지 파일이 필요합니다.");
        }
        if (!StringUtils.hasText(ownerType) || !StringUtils.hasText(ownerKey)) {
            throw new IllegalArgumentException("ownerType/ownerKey가 필요합니다.");
        }
        repository.findVersion(versionId).orElseThrow(() -> new IllegalArgumentException("버전을 찾을 수 없습니다."));

        try {
            String original = file.getOriginalFilename() == null ? "asset" : file.getOriginalFilename();
            String ext = "";
            int dot = original.lastIndexOf('.');
            if (dot >= 0) ext = original.substring(dot);
            String stored = UUID.randomUUID() + ext;
            String subDir = normalize(uploadProperties.getAssetSubDir()) + "/" + projectId + "/" + versionId + "/" + LocalDate.now();
            Path dir = Paths.get(normalize(uploadPath), subDir);
            Files.createDirectories(dir);
            Path target = dir.resolve(stored);
            file.transferTo(target.toFile());

            String fileUrl = "/administration/upload/" + subDir + "/" + stored;
            return repository.insertAsset(
                    UUID.randomUUID(),
                    projectId,
                    versionId,
                    ownerType.trim(),
                    ownerKey.trim(),
                    original,
                    stored,
                    file.getContentType(),
                    target.toAbsolutePath().toString(),
                    fileUrl,
                    note
            );
        } catch (Exception e) {
            throw new IllegalStateException("이미지 업로드 실패: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> findAssets(UUID projectId, UUID versionId, String ownerType, String ownerKey) {
        return repository.findAssets(projectId, versionId, ownerType, ownerKey);
    }

    private String normalize(String path) {
        if (path == null) return "";
        String p = path.replace("\\", "/").trim();
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        while (p.startsWith("/")) p = p.substring(1);
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*")) {
            p = path.replace("\\", "/").trim();
            while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}
