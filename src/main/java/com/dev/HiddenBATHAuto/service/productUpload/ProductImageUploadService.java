package com.dev.HiddenBATHAuto.service.productUpload;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    public ProductImageUploadReport process(MultipartFile standardZip, MultipartFile seriesZip, MultipartFile productZip) {
        ProductImageUploadReport report = new ProductImageUploadReport();

        // 1) ZIP별 처리
        if (standardZip != null && !standardZip.isEmpty()) {
            handleStandardZip(standardZip, report);
        }
        if (seriesZip != null && !seriesZip.isEmpty()) {
            handleSeriesZip(seriesZip, report);
        }
        if (productZip != null && !productZip.isEmpty()) {
            handleProductZip(productZip, report);
        }

        // 2) “업로드 후에도 이미지가 없는 엔티티” 목록 채우기(최대 200)
        fillMissingLists(report);

        return report;
    }

    private void fillMissingLists(ProductImageUploadReport report) {
        // StandardProduct: imageUrl null/blank
        standardProductRepository.findTop200ByImageUrlIsNullOrImageUrlEquals("")
                .forEach(sp -> report.getMissing().getStandardNoImage().add("id=" + sp.getId() + ", code=" + sp.getProductCode() + ", name=" + sp.getName()));

        // Series: seriesRepImageRoad null/blank
        seriesRepository.findTop200BySeriesRepImageRoadIsNullOrSeriesRepImageRoadEquals("")
                .forEach(s -> report.getMissing().getSeriesNoImage().add("id=" + s.getId() + ", name=" + s.getName()));

        // Product: productRepImageRoad null/blank
        productRepository.findTop200ByProductRepImageRoadIsNullOrProductRepImageRoadEquals("")
                .forEach(p -> report.getMissing().getProductNoImage().add("id=" + p.getId() + ", name=" + p.getName()));
    }

    // -------------------------------
    // ZIP 처리(공통)
    // -------------------------------
    private ZipFile openZipFileWithFallback(File tmpZip) throws Exception {
        // 기본 UTF-8 시도 → 실패 시 EUC-KR 폴백(윈도우에서 한글 폴더명 ZIP 흔함)
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

    private String normalizeFolderName(String s) {
        if (!StringUtils.hasText(s)) return "";
        String t = s.trim();
        // ZIP 엔트리에서 흔히 섞이는 백슬래시 방지
        t = t.replace("\\", "/");
        // 끝 슬래시 제거
        while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t.trim();
    }

    private boolean isZipSlip(String entryName) {
        // Zip Slip 방어: .. 또는 절대경로 형태 거부
        String n = entryName.replace("\\", "/");
        return n.contains("..") || n.startsWith("/") || n.matches("^[A-Za-z]:/.*");
    }

    private String extOf(String filename) {
        if (!StringUtils.hasText(filename)) return "";
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return "";
        return filename.substring(dot + 1).toLowerCase();
    }

    private Map<String, ZipEntry> pickRepresentativeImagePerFolder(ZipFile zipFile) {
        // folderName -> chosenZipEntry (대표 이미지)
        Map<String, ZipEntry> chosen = new LinkedHashMap<>();

        // folderName -> 이미 webp가 선택됐는지
        Set<String> chosenWebpFolders = new HashSet<>();

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (e.isDirectory()) continue;

            String name = e.getName();
            if (!StringUtils.hasText(name)) continue;
            if (isZipSlip(name)) continue;

            name = name.replace("\\", "/");
            String[] parts = name.split("/");
            if (parts.length < 2) continue; // 최상위 폴더/파일 구조가 아니면 제외(원하시면 확장 가능)

            String folder = normalizeFolderName(parts[0]);
            if (!StringUtils.hasText(folder)) continue;

            String ext = extOf(name);
            if (!ALLOWED_EXT.contains(ext)) continue;

            boolean isWebp = "webp".equals(ext);

            // 1) 아직 아무것도 선택 안 된 폴더면: 그냥 넣어둠(임의 선택)
            if (!chosen.containsKey(folder)) {
                chosen.put(folder, e);
                if (isWebp) chosenWebpFolders.add(folder);
                continue;
            }

            // 2) 이미 webp가 선택된 폴더면: 그대로 유지(아무것도 안 함)
            if (chosenWebpFolders.contains(folder)) {
                continue;
            }

            // 3) 아직 webp가 선택되지 않았는데, 이번 파일이 webp면: webp로 교체
            if (isWebp) {
                chosen.put(folder, e);
                chosenWebpFolders.add(folder);
            }

            // 4) webp가 아닌 다른 확장자면: 기존 선택 유지(임의 그대로)
        }

        return chosen;
    }


    private Path ensureDir(String dir) throws Exception {
        Path p = Paths.get(dir);
        if (Files.notExists(p)) Files.createDirectories(p);
        return p;
    }

    private String normalizeUploadRoot(String root) {
        String r = root.replace("\\", "/");
        if (!r.endsWith("/")) r += "/";
        return r;
    }

    private SavedFile saveZipEntryToTarget(ZipFile zipFile, ZipEntry entry, String targetDir, String originalNameHint) throws Exception {
        ensureDir(targetDir);

        String entryName = entry.getName();
        String ext = extOf(entryName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException("허용되지 않은 확장자: " + ext + " (" + entryName + ")");
        }

        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path targetPath = Paths.get(targetDir, fileName);

        // 대용량 대비 스트리밍 저장
        try (InputStream is = new BufferedInputStream(zipFile.getInputStream(entry));
             BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(targetPath.toFile()))) {
            is.transferTo(os);
        }

        SavedFile sf = new SavedFile();
        sf.fullPath = targetPath.toString().replace("\\", "/");
        sf.fileName = fileName;
        sf.ext = ext;
        sf.originalName = StringUtils.hasText(originalNameHint) ? originalNameHint : Paths.get(entryName).getFileName().toString();
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
    // Standard ZIP
    // -------------------------------
    private void handleStandardZip(MultipartFile zip, ProductImageUploadReport report) {
        File tmp = toTempFile(zip);
        try (ZipFile zipFile = openZipFileWithFallback(tmp)) {
            Map<String, ZipEntry> reps = pickRepresentativeImagePerFolder(zipFile);

            for (Map.Entry<String, ZipEntry> en : reps.entrySet()) {
                String folder = en.getKey(); // productCode
                ZipEntry entry = en.getValue();

                report.getSummary().setTotalFolders(report.getSummary().getTotalFolders() + 1);

                ProductImageUploadReport.Item item = new ProductImageUploadReport.Item();
                item.setFolderName(folder);

                try {
                    StandardProduct sp = standardProductRepository.findByProductCode(folder).orElse(null);
                    if (sp == null) {
                        item.setStatus("NOT_FOUND");
                        item.setMessage("제품코드로 StandardProduct 매칭 실패");
                        report.getStandard().getItems().add(item);
                        report.getSummary().setNotMatchedFolders(report.getSummary().getNotMatchedFolders() + 1);
                        continue;
                    }

                    // 새 파일 저장
                    String date = LocalDate.now().toString();
                    String root = normalizeUploadRoot(uploadRoot);
                    String dir = root + "standard/product/" + sp.getId() + "/" + date + "/";
                    SavedFile saved = saveZipEntryToTarget(zipFile, entry, dir, entry.getName());

                    String newUrl = "/upload/standard/product/" + sp.getId() + "/" + date + "/" + saved.fileName;

                    // DB 업데이트(파일 먼저 저장 → DB 저장 성공 후 기존 파일 삭제)
                    updateStandardProductImage(sp, saved, newUrl);

                    item.setStatus("UPDATED");
                    item.setMatchedId(String.valueOf(sp.getId()));
                    item.setNewImageUrl(newUrl);
                    item.setMessage("업데이트 완료");
                    report.getStandard().getItems().add(item);
                    report.getSummary().setUpdated(report.getSummary().getUpdated() + 1);

                } catch (Exception e) {
                    item.setStatus("ERROR");
                    item.setMessage("처리 오류: " + e.getMessage());
                    report.getStandard().getItems().add(item);
                    report.getSummary().setErrors(report.getSummary().getErrors() + 1);
                }
            }

            // 이미지가 하나도 없거나 폴더를 못 뽑은 경우도 표시
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
        // 기존 파일 경로 백업
        String oldPath = sp.getImagePath();

        // 이미지 필드 “교체”
        sp.setImageUrl(newUrl);
        sp.setImagePath(saved.fullPath);
        sp.setImageFileName(saved.fileName);
        sp.setImageOriginalName(saved.originalName);
        sp.setImageExt(saved.ext);

        standardProductRepository.save(sp);

        // DB 저장이 성공한 뒤에 기존 파일 삭제 시도
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
                String folder = en.getKey(); // Series.name
                ZipEntry entry = en.getValue();

                report.getSummary().setTotalFolders(report.getSummary().getTotalFolders() + 1);

                ProductImageUploadReport.Item item = new ProductImageUploadReport.Item();
                item.setFolderName(folder);

                try {
                    List<Series> list = seriesRepository.findByName(folder);
                    if (list == null || list.isEmpty()) {
                        item.setStatus("NOT_FOUND");
                        item.setMessage("Series.name으로 매칭 실패");
                        report.getSeries().getItems().add(item);
                        report.getSummary().setNotMatchedFolders(report.getSummary().getNotMatchedFolders() + 1);
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
                    item.setMessage("업데이트 완료");
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
                String folder = en.getKey(); // Product.name
                ZipEntry entry = en.getValue();

                report.getSummary().setTotalFolders(report.getSummary().getTotalFolders() + 1);

                ProductImageUploadReport.Item item = new ProductImageUploadReport.Item();
                item.setFolderName(folder);

                try {
                	List<Product> list = productRepository.findAllByName(folder);

                    if (list == null || list.isEmpty()) {
                        item.setStatus("NOT_FOUND");
                        item.setMessage("Product.name으로 매칭 실패");
                        report.getProduct().getItems().add(item);
                        report.getSummary().setNotMatchedFolders(report.getSummary().getNotMatchedFolders() + 1);
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
                    item.setMessage("업데이트 완료");
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