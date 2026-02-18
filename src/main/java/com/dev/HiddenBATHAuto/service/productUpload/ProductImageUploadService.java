package com.dev.HiddenBATHAuto.service.productUpload;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.time.LocalDate;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.productUpload.ProductImageUploadReport;
import com.dev.HiddenBATHAuto.model.nonstandard.Product;
import com.dev.HiddenBATHAuto.model.nonstandard.Series;
import com.dev.HiddenBATHAuto.model.standard.StandardProduct;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductSeriesRepository;
import com.dev.HiddenBATHAuto.repository.standard.StandardProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductImageUploadService {

    private final StandardProductRepository standardProductRepository;
    private final ProductSeriesRepository seriesRepository;
    private final ProductRepository productRepository;

    @Value("${spring.upload.path}")
    private String uploadRoot;

    private static final Set<String> ALLOWED_EXT = Set.of("webp", "png", "jpg", "jpeg");
    private static final int REPORT_LIMIT = 200;

    // macOS ZIP 메타 제외
    private static final Set<String> IGNORE_TOP_FOLDERS = Set.of("__MACOSX");
    private static final Set<String> IGNORE_FILE_NAMES = Set.of(".DS_Store");

    /**
     * ✅ "컨테이너(담는) 폴더"로 자주 쓰이는 폴더명들
     * - 예: 규격/ABC123/대표.webp  => key는 "ABC123"이어야 함 (규격은 컨테이너)
     * - 환경에 따라 더 추가 가능
     */
    private static final Set<String> CONTAINER_FOLDERS = Set.of(
            "규격", "시리즈", "제품",
            "standard", "series", "product",
            "STANDARD", "SERIES", "PRODUCT"
    );

    // -------------------------------
    // Public API
    // -------------------------------
    public ProductImageUploadReport process(MultipartFile standardZip, MultipartFile seriesZip, MultipartFile productZip) {
        ProductImageUploadReport report = new ProductImageUploadReport();

        if (standardZip != null && !standardZip.isEmpty()) {
            handleStandardZip(standardZip, report);
        }
        if (seriesZip != null && !seriesZip.isEmpty()) {
            handleSeriesZip(seriesZip, report);
        }
        if (productZip != null && !productZip.isEmpty()) {
            handleProductZip(productZip, report);
        }

        fillMissingLists(report);
        return report;
    }

    // -------------------------------
    // Missing list
    // -------------------------------
    private void fillMissingLists(ProductImageUploadReport report) {
        standardProductRepository.findTop200ByImageUrlIsNullOrImageUrlEquals("")
                .forEach(sp -> {
                    if (report.getMissing().getStandardNoImage().size() >= REPORT_LIMIT) return;
                    report.getMissing().getStandardNoImage()
                            .add("id=" + sp.getId() + ", code=" + sp.getProductCode() + ", name=" + sp.getName());
                });

        seriesRepository.findTop200BySeriesRepImageRoadIsNullOrSeriesRepImageRoadEquals("")
                .forEach(s -> {
                    if (report.getMissing().getSeriesNoImage().size() >= REPORT_LIMIT) return;
                    report.getMissing().getSeriesNoImage()
                            .add("id=" + s.getId() + ", name=" + s.getName());
                });

        productRepository.findTop200ByProductRepImageRoadIsNullOrProductRepImageRoadEquals("")
                .forEach(p -> {
                    if (report.getMissing().getProductNoImage().size() >= REPORT_LIMIT) return;
                    report.getMissing().getProductNoImage()
                            .add("id=" + p.getId() + ", name=" + p.getName());
                });
    }

    // -------------------------------
    // ZIP open / temp
    // -------------------------------
    private ZipFile openZipFileWithFallback(File tmpZip) throws Exception {
        try {
            return new ZipFile(tmpZip, Charset.forName("UTF-8"));
        } catch (Exception e) {
            return new ZipFile(tmpZip, Charset.forName("EUC-KR"));
        }
    }

    private File toTempFile(MultipartFile mf) {
        try {
            String original = mf.getOriginalFilename();
            String suffix = (original != null && original.toLowerCase().endsWith(".zip")) ? ".zip" : ".tmp";
            File tmp = File.createTempFile("upload_", suffix);
            mf.transferTo(tmp);
            return tmp;
        } catch (Exception e) {
            throw new IllegalStateException("임시파일 생성/저장 실패: " + e.getMessage(), e);
        }
    }

    // -------------------------------
    // Path / key normalization
    // -------------------------------

    /**
     * ✅ ZIP 엔트리 경로 정규화
     * - \ -> /
     * - 선행 "./" 제거 (맥/일부 압축툴)
     * - 선행 "/" 제거
     * - NFC 정규화
     */
    private String normalizeEntryPath(String entryNameRaw) {
        if (!StringUtils.hasText(entryNameRaw)) return "";
        String n = entryNameRaw.replace("\\", "/").trim();

        while (n.startsWith("./")) n = n.substring(2);
        while (n.startsWith("/")) n = n.substring(1);

        n = Normalizer.normalize(n, Form.NFC);
        return n;
    }

    /**
     * 폴더/키 정규화
     * - trim
     * - NFC
     * - 뒤쪽 "/" 제거
     * - 불필요 공백 축소
     */
    private String normalizeFolderName(String s) {
        if (!StringUtils.hasText(s)) return "";
        String t = s.trim();
        t = t.replace("\\", "/");
        while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        t = t.trim();
        t = Normalizer.normalize(t, Form.NFC);
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    private boolean isZipSlip(String entryName) {
        String n = entryName.replace("\\", "/");
        return n.contains("..") || n.startsWith("/") || n.matches("^[A-Za-z]:/.*");
    }

    private String extOf(String filename) {
        if (!StringUtils.hasText(filename)) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return "";
        return filename.substring(dot + 1).toLowerCase();
    }

    private boolean shouldIgnoreEntry(String entryNameRaw) {
        if (!StringUtils.hasText(entryNameRaw)) return true;

        String name = normalizeEntryPath(entryNameRaw);

        if (!StringUtils.hasText(name)) return true;
        if (isZipSlip(name)) return true;

        String[] parts = name.split("/");
        if (parts.length >= 1) {
            String top = normalizeFolderName(parts[0]);
            if (IGNORE_TOP_FOLDERS.contains(top)) return true;
            if (".".equals(top)) return true;
        }

        String last = Paths.get(name).getFileName().toString();
        if (IGNORE_FILE_NAMES.contains(last)) return true;

        // AppleDouble 제외 (._xxx.webp)
        if (last.startsWith("._")) return true;

        return false;
    }

    // -------------------------------
    // ✅ Common top folder detection
    // -------------------------------
    /**
     * ZIP 내부 엔트리들이 전부 같은 최상단 폴더(예: 규격/...) 아래에 있으면 그 폴더명을 반환.
     * 아니면 null 반환.
     *
     * ✅ 맥에서 "./규격/..." 처럼 들어오는 경우가 있어 "." top은 무시합니다.
     */
    private String detectCommonTopFolder(ZipFile zipFile) {
        Set<String> topFolders = new HashSet<>();

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (e.isDirectory()) continue;

            String name = normalizeEntryPath(e.getName());
            if (!StringUtils.hasText(name)) continue;

            if (shouldIgnoreEntry(name)) continue;

            // 파일이 루트에 있으면 공통 top 폴더 제거 대상 아님
            if (!name.contains("/")) return null;

            String top = name.substring(0, name.indexOf('/'));
            top = normalizeFolderName(top);

            // "." 또는 빈값은 무시
            if (!StringUtils.hasText(top) || ".".equals(top)) continue;

            if (IGNORE_TOP_FOLDERS.contains(top)) continue;

            topFolders.add(top);
            if (topFolders.size() > 1) return null;
        }

        if (topFolders.size() == 1) {
            return topFolders.iterator().next();
        }
        return null;
    }

    // -------------------------------
    // ✅ Match key extraction
    // -------------------------------
    /**
     * 엔트리 경로에서 "DB 매칭 키"를 추출합니다.
     *
     * 1) 공통 최상단 폴더(commonTop)가 있으면 제거
     * 2) 남은 경로의 첫 폴더가 컨테이너(규격/시리즈/제품...)면 두 번째 폴더를 키로 사용
     * 3) 그렇지 않으면 첫 폴더를 키로 사용
     * 4) 파일이 루트면 파일명(확장자 제외)을 키로 사용
     */
    private String extractMatchKey(String entryNameRaw, String commonTop) {
        String n = normalizeEntryPath(entryNameRaw);
        if (!StringUtils.hasText(n)) return null;

        if (StringUtils.hasText(commonTop)) {
            String topNorm = normalizeFolderName(commonTop);
            String prefix = topNorm + "/";
            if (n.startsWith(prefix)) {
                n = n.substring(prefix.length());
            }
        }

        String[] parts = n.split("/");
        if (parts.length == 0) return null;

        // 루트 파일: 파일명(확장자 제거)
        if (parts.length == 1) {
            String base = Paths.get(parts[0]).getFileName().toString();
            int dot = base.lastIndexOf('.');
            String key = (dot > 0) ? base.substring(0, dot) : base;
            key = normalizeFolderName(key);
            return StringUtils.hasText(key) ? key : null;
        }

        String first = normalizeFolderName(parts[0]);
        if (!StringUtils.hasText(first) || ".".equals(first) || IGNORE_TOP_FOLDERS.contains(first)) return null;

        // ✅ 핵심: 첫 폴더가 컨테이너(규격 등)면 2번째 폴더를 키로 사용
        if (CONTAINER_FOLDERS.contains(first) && parts.length >= 2) {
            String second = normalizeFolderName(parts[1]);
            if (!StringUtils.hasText(second) || ".".equals(second) || IGNORE_TOP_FOLDERS.contains(second)) return null;
            return second;
        }

        return first;
    }

    // -------------------------------
    // Representative image selection
    // -------------------------------
    private Map<String, ZipEntry> pickRepresentativeImagePerFolder(ZipFile zipFile) {
        Map<String, ZipEntry> chosen = new LinkedHashMap<>();
        Set<String> chosenWebpKeys = new HashSet<>();

        // ✅ ZIP의 "공통 최상단 폴더"가 하나면 제거하기 위한 prefix 계산
        String commonTop = detectCommonTopFolder(zipFile);

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (e.isDirectory()) continue;

            String rawName = e.getName();
            String name = normalizeEntryPath(rawName);
            if (!StringUtils.hasText(name)) continue;

            if (shouldIgnoreEntry(name)) continue;

            String ext = extOf(name);
            if (!ALLOWED_EXT.contains(ext)) continue;

            // ✅ 핵심: "규격" 같은 컨테이너 폴더를 키로 쓰지 않도록 match key 추출
            String key = extractMatchKey(name, commonTop);
            if (!StringUtils.hasText(key)) continue;

            boolean isWebp = "webp".equals(ext);

            if (!chosen.containsKey(key)) {
                chosen.put(key, e);
                if (isWebp) chosenWebpKeys.add(key);
                continue;
            }

            // 이미 webp가 선택된 키면 교체 금지
            if (chosenWebpKeys.contains(key)) continue;

            // webp 우선권
            if (isWebp) {
                chosen.put(key, e);
                chosenWebpKeys.add(key);
            }
        }

        return chosen;
    }

    // -------------------------------
    // Save / delete
    // -------------------------------
    private Path ensureDir(String dir) throws Exception {
        Path p = Paths.get(dir);
        if (Files.notExists(p)) {
            try {
                Files.createDirectories(p);
            } catch (AccessDeniedException ade) {
                throw new IllegalStateException("디렉토리 생성 권한 없음: " + p.toAbsolutePath(), ade);
            }
        }
        return p;
    }

    /**
     * ✅ uploadRoot가 "${user.home}" 치환이 안 된 채 들어오는 경우까지 방어
     * - ${user.home} 또는 ~ 를 실제 홈 디렉토리로 보정
     * - 마지막 / 보장
     */
    private String normalizeUploadRoot(String root) {
        if (!StringUtils.hasText(root)) return root;

        String r = root.replace("\\", "/").trim();

        String userHome = System.getProperty("user.home");
        if (StringUtils.hasText(userHome)) {
            userHome = userHome.replace("\\", "/");
            r = r.replace("${user.home}", userHome);

            if (r.startsWith("~/")) {
                r = userHome + r.substring(1);
            } else if (r.equals("~")) {
                r = userHome;
            }
        }

        if (!r.endsWith("/")) r += "/";
        return r;
    }

    private SavedFile saveZipEntryToTarget(ZipFile zipFile, ZipEntry entry, String targetDir, String originalNameHint) throws Exception {
        String normalizedTargetDir = targetDir.replace("\\", "/");
        ensureDir(normalizedTargetDir);

        String entryName = normalizeEntryPath(entry.getName());
        String ext = extOf(entryName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException("허용되지 않은 확장자: " + ext + " (" + entryName + ")");
        }

        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path targetPath = Paths.get(normalizedTargetDir, fileName);

        try (InputStream is = new BufferedInputStream(zipFile.getInputStream(entry));
             BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(targetPath.toFile()))) {
            is.transferTo(os);
        } catch (AccessDeniedException ade) {
            throw new IllegalStateException("파일 저장 권한 없음: " + targetPath.toAbsolutePath(), ade);
        }

        SavedFile sf = new SavedFile();
        sf.fullPath = targetPath.toString().replace("\\", "/");
        sf.fileName = fileName;
        sf.ext = ext;
        sf.originalName = StringUtils.hasText(originalNameHint)
                ? originalNameHint
                : Paths.get(entryName).getFileName().toString();
        return sf;
    }

    private boolean deleteIfExists(String fullPath) {
        try {
            if (!StringUtils.hasText(fullPath)) return true;
            Path p = Paths.get(fullPath);
            if (Files.exists(p)) Files.delete(p);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static class SavedFile {
        String fullPath;
        String fileName;
        String originalName;
        String ext;
    }

    // -------------------------------
    // Debug helper (필요 시)
    // -------------------------------
    @SuppressWarnings("unused")
    private void logNotMatched(String type, String key, String entryName) {
        // key의 실제 유니코드 확인용
        String hex = key.chars()
                .mapToObj(c -> String.format("%04X", c))
                .reduce((a, b) -> a + " " + b)
                .orElse("");
        System.out.println("[ZIP][" + type + "][NOT_MATCH] key=[" + key + "] (hex=" + hex + "), entry=[" + entryName + "]");
    }

    // -------------------------------
    // Standard ZIP
    // -------------------------------
    private void handleStandardZip(MultipartFile zip, ProductImageUploadReport report) {
        File tmp = toTempFile(zip);

        try (ZipFile zipFile = openZipFileWithFallback(tmp)) {
            Map<String, ZipEntry> reps = pickRepresentativeImagePerFolder(zipFile);

            for (Map.Entry<String, ZipEntry> en : reps.entrySet()) {
                String key = en.getKey();     // ✅ 이제 "규격"이 아니라 "ABC123" 같은 하위 폴더가 들어와야 함
                ZipEntry entry = en.getValue();

                report.getSummary().setTotalFolders(report.getSummary().getTotalFolders() + 1);

                ProductImageUploadReport.Item item = new ProductImageUploadReport.Item();
                item.setFolderName(key);

                try {
                    StandardProduct sp = standardProductRepository.findByProductCode(key).orElse(null);
                    if (sp == null) {
                        item.setStatus("NOT_FOUND");
                        item.setMessage("제품코드로 StandardProduct 매칭 실패");
                        report.getStandard().getItems().add(item);
                        report.getSummary().setNotMatchedFolders(report.getSummary().getNotMatchedFolders() + 1);

                        // 필요하면 켜서 확인하세요
                        // logNotMatched("STANDARD", key, entry.getName());
                        continue;
                    }

                    String date = LocalDate.now().toString();
                    String root = normalizeUploadRoot(uploadRoot);

                    String dir = root + "standard/product/" + sp.getId() + "/" + date + "/";
                    SavedFile saved = saveZipEntryToTarget(zipFile, entry, dir, entry.getName());
                    String newUrl = "/upload/standard/product/" + sp.getId() + "/" + date + "/" + saved.fileName;

                    updateStandardProductImage(sp, saved, newUrl);

                    item.setStatus("UPDATED");
                    item.setMatchedId(String.valueOf(sp.getId()));
                    item.setNewImageUrl(newUrl);
                    item.setMessage("업데이트 완료 (저장경로: " + saved.fullPath + ")");
                    report.getStandard().getItems().add(item);
                    report.getSummary().setUpdated(report.getSummary().getUpdated() + 1);

                } catch (Exception e) {
                    item.setStatus("ERROR");
                    item.setMessage("처리 오류: " + e.getMessage());
                    report.getStandard().getItems().add(item);
                    report.getSummary().setErrors(report.getSummary().getErrors() + 1);
                }
            }

            if (reps.isEmpty()) {
                ProductImageUploadReport.Item item = new ProductImageUploadReport.Item();
                item.setStatus("NO_IMAGE");
                item.setFolderName("-");
                item.setMessage("ZIP에서 허용 확장자(webp/png/jpg/jpeg) 파일을 찾지 못했습니다.");
                report.getStandard().getItems().add(item);
            }

        } catch (Exception e) {
            ProductImageUploadReport.Item item = new ProductImageUploadReport.Item();
            item.setStatus("ERROR");
            item.setFolderName("-");
            item.setMessage("ZIP 열기/처리 실패: " + e.getMessage());
            report.getStandard().getItems().add(item);
            report.getSummary().setErrors(report.getSummary().getErrors() + 1);
        } finally {
            try { Files.deleteIfExists(tmp.toPath()); } catch (Exception ignore) {}
        }
    }

    @Transactional
    protected void updateStandardProductImage(StandardProduct sp, SavedFile saved, String newUrl) {
        String oldPath = sp.getImagePath();

        sp.setImageUrl(newUrl);
        sp.setImagePath(saved.fullPath);
        sp.setImageFileName(saved.fileName);
        sp.setImageOriginalName(saved.originalName);
        sp.setImageExt(saved.ext);

        standardProductRepository.save(sp);

        if (StringUtils.hasText(oldPath) && !oldPath.equals(saved.fullPath)) {
            deleteIfExists(oldPath);
        }
    }

    // -------------------------------
    // Series ZIP
    // -------------------------------
    private void handleSeriesZip(MultipartFile zip, ProductImageUploadReport report) {
        File tmp = toTempFile(zip);

        try (ZipFile zipFile = openZipFileWithFallback(tmp)) {
            Map<String, ZipEntry> reps = pickRepresentativeImagePerFolder(zipFile);

            for (Map.Entry<String, ZipEntry> en : reps.entrySet()) {
                String key = en.getKey();
                ZipEntry entry = en.getValue();

                report.getSummary().setTotalFolders(report.getSummary().getTotalFolders() + 1);

                ProductImageUploadReport.Item item = new ProductImageUploadReport.Item();
                item.setFolderName(key);

                try {
                    List<Series> list = seriesRepository.findByName(key);
                    if (list == null || list.isEmpty()) {
                        item.setStatus("NOT_FOUND");
                        item.setMessage("Series.name으로 매칭 실패");
                        report.getSeries().getItems().add(item);
                        report.getSummary().setNotMatchedFolders(report.getSummary().getNotMatchedFolders() + 1);

                        // logNotMatched("SERIES", key, entry.getName());
                        continue;
                    }
                    if (list.size() != 1) {
                        item.setStatus("AMBIGUOUS");
                        item.setMessage("동일한 Series.name이 " + list.size() + "개 존재하여 스킵(안전)");
                        report.getSeries().getItems().add(item);
                        report.getSummary().setErrors(report.getSummary().getErrors() + 1);
                        continue;
                    }

                    Series s = list.get(0);

                    String date = LocalDate.now().toString();
                    String root = normalizeUploadRoot(uploadRoot);

                    String dir = root + "series/" + s.getId() + "/" + date + "/";
                    SavedFile saved = saveZipEntryToTarget(zipFile, entry, dir, entry.getName());
                    String newUrl = "/upload/series/" + s.getId() + "/" + date + "/" + saved.fileName;

                    updateSeriesImage(s, saved, newUrl);

                    item.setStatus("UPDATED");
                    item.setMatchedId(String.valueOf(s.getId()));
                    item.setNewImageUrl(newUrl);
                    item.setMessage("업데이트 완료 (저장경로: " + saved.fullPath + ")");
                    report.getSeries().getItems().add(item);
                    report.getSummary().setUpdated(report.getSummary().getUpdated() + 1);

                } catch (Exception e) {
                    item.setStatus("ERROR");
                    item.setMessage("처리 오류: " + e.getMessage());
                    report.getSeries().getItems().add(item);
                    report.getSummary().setErrors(report.getSummary().getErrors() + 1);
                }
            }

            if (reps.isEmpty()) {
                ProductImageUploadReport.Item item = new ProductImageUploadReport.Item();
                item.setStatus("NO_IMAGE");
                item.setFolderName("-");
                item.setMessage("ZIP에서 허용 확장자(webp/png/jpg/jpeg) 파일을 찾지 못했습니다.");
                report.getSeries().getItems().add(item);
            }

        } catch (Exception e) {
            ProductImageUploadReport.Item item = new ProductImageUploadReport.Item();
            item.setStatus("ERROR");
            item.setFolderName("-");
            item.setMessage("ZIP 열기/처리 실패: " + e.getMessage());
            report.getSeries().getItems().add(item);
            report.getSummary().setErrors(report.getSummary().getErrors() + 1);
        } finally {
            try { Files.deleteIfExists(tmp.toPath()); } catch (Exception ignore) {}
        }
    }

    @Transactional
    protected void updateSeriesImage(Series s, SavedFile saved, String newUrl) {
        String oldPath = s.getSeriesRepImagePath();

        s.setSeriesRepImageRoad(newUrl);
        s.setSeriesRepImagePath(saved.fullPath);
        s.setSeriesRepImageName(saved.fileName);
        s.setSeriesRepImageExtension(saved.ext);
        s.setSeriesRepImageOriginalName(saved.originalName);

        seriesRepository.save(s);

        if (StringUtils.hasText(oldPath) && !oldPath.equals(saved.fullPath)) {
            deleteIfExists(oldPath);
        }
    }

    // -------------------------------
    // Product ZIP
    // -------------------------------
    private void handleProductZip(MultipartFile zip, ProductImageUploadReport report) {
        File tmp = toTempFile(zip);

        try (ZipFile zipFile = openZipFileWithFallback(tmp)) {
            Map<String, ZipEntry> reps = pickRepresentativeImagePerFolder(zipFile);

            for (Map.Entry<String, ZipEntry> en : reps.entrySet()) {
                String key = en.getKey();
                ZipEntry entry = en.getValue();

                report.getSummary().setTotalFolders(report.getSummary().getTotalFolders() + 1);

                ProductImageUploadReport.Item item = new ProductImageUploadReport.Item();
                item.setFolderName(key);

                try {
                    List<Product> list = productRepository.findAllByName(key);

                    if (list == null || list.isEmpty()) {
                        item.setStatus("NOT_FOUND");
                        item.setMessage("Product.name으로 매칭 실패");
                        report.getProduct().getItems().add(item);
                        report.getSummary().setNotMatchedFolders(report.getSummary().getNotMatchedFolders() + 1);

                        // logNotMatched("PRODUCT", key, entry.getName());
                        continue;
                    }
                    if (list.size() != 1) {
                        item.setStatus("AMBIGUOUS");
                        item.setMessage("동일한 Product.name이 " + list.size() + "개 존재하여 스킵(안전)");
                        report.getProduct().getItems().add(item);
                        report.getSummary().setErrors(report.getSummary().getErrors() + 1);
                        continue;
                    }

                    Product p = list.get(0);

                    String date = LocalDate.now().toString();
                    String root = normalizeUploadRoot(uploadRoot);

                    String dir = root + "product/" + p.getId() + "/" + date + "/";
                    SavedFile saved = saveZipEntryToTarget(zipFile, entry, dir, entry.getName());
                    String newUrl = "/upload/product/" + p.getId() + "/" + date + "/" + saved.fileName;

                    updateProductImage(p, saved, newUrl);

                    item.setStatus("UPDATED");
                    item.setMatchedId(String.valueOf(p.getId()));
                    item.setNewImageUrl(newUrl);
                    item.setMessage("업데이트 완료 (저장경로: " + saved.fullPath + ")");
                    report.getProduct().getItems().add(item);
                    report.getSummary().setUpdated(report.getSummary().getUpdated() + 1);

                } catch (Exception e) {
                    item.setStatus("ERROR");
                    item.setMessage("처리 오류: " + e.getMessage());
                    report.getProduct().getItems().add(item);
                    report.getSummary().setErrors(report.getSummary().getErrors() + 1);
                }
            }

            if (reps.isEmpty()) {
                ProductImageUploadReport.Item item = new ProductImageUploadReport.Item();
                item.setStatus("NO_IMAGE");
                item.setFolderName("-");
                item.setMessage("ZIP에서 허용 확장자(webp/png/jpg/jpeg) 파일을 찾지 못했습니다.");
                report.getProduct().getItems().add(item);
            }

        } catch (Exception e) {
            ProductImageUploadReport.Item item = new ProductImageUploadReport.Item();
            item.setStatus("ERROR");
            item.setFolderName("-");
            item.setMessage("ZIP 열기/처리 실패: " + e.getMessage());
            report.getProduct().getItems().add(item);
            report.getSummary().setErrors(report.getSummary().getErrors() + 1);
        } finally {
            try { Files.deleteIfExists(tmp.toPath()); } catch (Exception ignore) {}
        }
    }

    @Transactional
    protected void updateProductImage(Product p, SavedFile saved, String newUrl) {
        String oldPath = p.getProductRepImagePath();

        p.setProductRepImageRoad(newUrl);
        p.setProductRepImagePath(saved.fullPath);
        p.setProductRepImageName(saved.fileName);
        p.setProductRepImageExtension(saved.ext);
        p.setProductRepImageOriginalName(saved.originalName);

        productRepository.save(p);

        if (StringUtils.hasText(oldPath) && !oldPath.equals(saved.fullPath)) {
            deleteIfExists(oldPath);
        }
    }
}
