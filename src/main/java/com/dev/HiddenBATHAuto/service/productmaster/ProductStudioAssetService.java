package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import com.dev.HiddenBATHAuto.model.productmaster.ProductStudioAsset;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductStudioAssetRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductStudioAssetService {
  private static final long MAX_SIZE = 20L * 1024 * 1024;
  private static final Set<String> ALLOWED =
      Set.of(
          "jpg", "jpeg", "png", "gif", "webp", "pdf", "txt", "csv", "xlsx", "xls", "docx", "doc",
          "pptx", "ppt", "zip", "hwp", "hwpx");
  private final ProductStudioAssetRepository repository;

  @Value("${spring.upload.path}")
  private String uploadPath;

  private Path directory() {
    return Paths.get(uploadPath)
        .toAbsolutePath()
        .normalize()
        .resolve("product-master/studio-assets");
  }

  private Path path(ProductStudioAsset a) {
    Path p = directory().resolve(a.getDiskName()).normalize();
    if (!p.startsWith(directory())) throw new IllegalStateException("잘못된 파일 경로입니다.");
    return p;
  }

  public List<AssetView> owned(String type, Long id) {
    return repository.findByOwnerTypeAndOwnerIdOrderByCreatedAtAsc(type, id).stream()
        .map(this::view)
        .toList();
  }

  public AssetView view(ProductStudioAsset a) {
    return new AssetView(
        a.getId(),
        a.getOriginalName(),
        a.getContentType(),
        a.getFileSize(),
        "/admin/api/product-master/studio/assets/" + a.getId(),
        a.getContentType().startsWith("image/"));
  }

  @Transactional
  public List<AssetView> stage(List<MultipartFile> files, String actor) {
    if (files == null || files.isEmpty() || files.size() > 30)
      throw new IllegalArgumentException("파일은 한 번에 1~30개까지 등록할 수 있습니다.");
    List<AssetView> result = new ArrayList<>();
    for (MultipartFile file : files) {
      if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE)
        throw new IllegalArgumentException("파일당 용량은 0바이트 초과 20MB 이하입니다.");
      String original =
          Optional.ofNullable(file.getOriginalFilename()).orElse("file").replace('\\', '/');
      original = original.substring(original.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
      if (original.length() > 240 || original.isBlank())
        throw new IllegalArgumentException("파일명은 1~240자여야 합니다.");
      String ext = extension(original);
      if (!ALLOWED.contains(ext)) throw new IllegalArgumentException("지원하지 않는 파일 확장자입니다: " + ext);
      String type = detect(file, ext);
      ProductStudioAsset asset = new ProductStudioAsset();
      asset.setId(UUID.randomUUID().toString());
      asset.setOwnerType("STAGED");
      asset.setOriginalName(original);
      asset.setContentType(type);
      asset.setFileSize(file.getSize());
      asset.setDiskName(asset.getId() + "." + ext);
      asset.setCreatedBy(actor);
      asset.setCreatedAt(LocalDateTime.now());
      Path target = path(asset);
      try {
        Files.createDirectories(directory());
        try (InputStream in = file.getInputStream()) {
          Files.copy(in, target);
        }
        if (type.startsWith("image/") && !type.equals("image/webp")) checkImage(target);
        afterRollback(target);
        repository.save(asset);
        result.add(view(asset));
      } catch (IOException | RuntimeException e) {
        deleteQuietly(target);
        if (e instanceof IllegalArgumentException ia) throw ia;
        throw new IllegalStateException("첨부파일 저장을 완료하지 못했습니다.", e);
      }
    }
    repository.flush();
    return result;
  }

  private static String extension(String name) {
    int dot = name.lastIndexOf('.');
    return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static String detect(MultipartFile file, String ext) {
    byte[] h;
    try (InputStream in = file.getInputStream()) {
      h = in.readNBytes(32);
    } catch (IOException e) {
      throw new IllegalArgumentException("파일을 읽을 수 없습니다.");
    }
    boolean jpeg =
        h.length >= 3 && (h[0] & 255) == 255 && (h[1] & 255) == 216 && (h[2] & 255) == 255;
    boolean png = h.length >= 8 && h[0] == (byte) 137 && h[1] == 80 && h[2] == 78 && h[3] == 71;
    boolean gif =
        h.length >= 6
            && new String(h, 0, 6, java.nio.charset.StandardCharsets.US_ASCII).matches("GIF8[79]a");
    boolean webp =
        h.length >= 12
            && h[0] == 82
            && h[1] == 73
            && h[2] == 70
            && h[3] == 70
            && h[8] == 87
            && h[9] == 69
            && h[10] == 66
            && h[11] == 80;
    boolean zip = h.length >= 4 && h[0] == 80 && h[1] == 75 && h[2] == 3 && h[3] == 4;
    boolean ole =
        h.length >= 8
            && h[0] == (byte) 0xd0
            && h[1] == (byte) 0xcf
            && h[2] == (byte) 0x11
            && h[3] == (byte) 0xe0;
    return switch (ext) {
      case "jpg", "jpeg" -> {
        signature(jpeg);
        yield "image/jpeg";
      }
      case "png" -> {
        signature(png);
        yield "image/png";
      }
      case "gif" -> {
        signature(gif);
        yield "image/gif";
      }
      case "webp" -> {
        signature(webp);
        yield "image/webp";
      }
      case "pdf" -> {
        signature(
            h.length >= 5 && h[0] == 37 && h[1] == 80 && h[2] == 68 && h[3] == 70 && h[4] == 45);
        yield "application/pdf";
      }
      case "zip", "xlsx", "docx", "pptx", "hwpx" -> {
        signature(zip);
        yield "application/octet-stream";
      }
      case "xls", "doc", "ppt", "hwp" -> {
        signature(ole);
        yield "application/octet-stream";
      }
      case "txt", "csv" -> {
        for (byte b : h) signature(b != 0);
        yield "text/plain";
      }
      default -> throw new IllegalArgumentException("지원하지 않는 파일입니다.");
    };
  }

  private static void signature(boolean valid) {
    if (!valid) throw new IllegalArgumentException("확장자와 파일 내용이 일치하지 않습니다.");
  }

  private static void checkImage(Path path) throws IOException {
    try (var input = ImageIO.createImageInputStream(path.toFile())) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IllegalArgumentException("이미지 정보를 읽을 수 없습니다.");
      var reader = readers.next();
      try {
        reader.setInput(input);
        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        if (pixels <= 0 || pixels > 80_000_000)
          throw new IllegalArgumentException("이미지는 8천만 화소 이하로 등록해 주세요.");
      } finally {
        reader.dispose();
      }
    }
  }

  @Transactional
  public String copyOwned(String type, Long from, Long to, String definition, String actor) {
    String result = definition;
    for (ProductStudioAsset original :
        repository.findByOwnerTypeAndOwnerIdOrderByCreatedAtAsc(type, from)) {
      ProductStudioAsset copy = new ProductStudioAsset();
      copy.setId(UUID.randomUUID().toString());
      copy.setOwnerType(type);
      copy.setOwnerId(to);
      copy.setOriginalName(original.getOriginalName());
      copy.setContentType(original.getContentType());
      copy.setFileSize(original.getFileSize());
      copy.setDiskName(UUID.randomUUID() + "." + extension(original.getOriginalName()));
      copy.setCreatedBy(actor);
      copy.setCreatedAt(LocalDateTime.now());
      Path target = path(copy);
      try {
        Files.copy(path(original), target);
      } catch (java.io.IOException ex) {
        throw new IllegalStateException("복사할 첨부파일을 읽지 못했습니다.", ex);
      }
      org.springframework.transaction.support.TransactionSynchronizationManager
          .registerSynchronization(
              new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                  if (status != STATUS_COMMITTED)
                    try {
                      Files.deleteIfExists(target);
                    } catch (java.io.IOException ignored) {
                    }
                }
              });
      repository.save(copy);
      if (result != null) result = result.replace(original.getId(), copy.getId());
    }
    return result;
  }

  public ProductStudioAsset require(String id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("첨부파일을 찾을 수 없습니다."));
  }

  @Transactional
  public void attach(String type, Long ownerId, List<String> ids, String actor) {
    if (!Set.of("GROUP", "VALUE", "PRODUCT", "PROCESS", "FAQ", "ACTUAL").contains(type)
        || ownerId == null) throw new IllegalArgumentException("첨부 대상을 확인해 주세요.");
    List<String> wanted = ProductStudioEngine.list(ids);
    if (wanted.size() > 200 || new HashSet<>(wanted).size() != wanted.size())
      throw new IllegalArgumentException("첨부파일 중복 또는 개수 제한을 확인해 주세요.");
    for (String id : wanted) {
      ProductStudioAsset a = require(id);
      if (a.getOwnerType().equals(type) && ownerId.equals(a.getOwnerId())) continue;
      if (!a.getOwnerType().equals("STAGED") || !a.getCreatedBy().equals(actor))
        throw new IllegalStateException("다른 대상 또는 다른 사용자의 임시 첨부파일입니다.");
      a.setOwnerType(type);
      a.setOwnerId(ownerId);
    }
    // Removed assets become private staged files and are collected after a grace period.
    for (ProductStudioAsset a :
        repository.findByOwnerTypeAndOwnerIdOrderByCreatedAtAsc(type, ownerId))
      if (!wanted.contains(a.getId())) {
        a.setOwnerType("STAGED");
        a.setOwnerId(null);
        a.setCreatedBy(actor);
        a.setCreatedAt(LocalDateTime.now());
      }
    repository.flush();
  }

  public void validateStaged(List<String> ids, String actor, String type, Long ownerId) {
    if (ProductStudioEngine.list(ids).size() > 30)
      throw new IllegalArgumentException("제품 첨부는 최대 30개입니다.");
    for (String id : ProductStudioEngine.list(ids)) {
      ProductStudioAsset a = require(id);
      if (a.getOwnerType().equals(type) && Objects.equals(ownerId, a.getOwnerId())) continue;
      if (!a.getOwnerType().equals("STAGED") || !a.getCreatedBy().equals(actor))
        throw new IllegalArgumentException("사용할 수 없는 임시 첨부파일입니다.");
    }
  }

  public void validateAnswers(Process process, Map<String, Answer> answers, String actor) {
    for (Question q : process.questions())
      if (q.control() == Control.FILE && !q.fixed())
        for (Field f : ProductStudioEngine.list(q.fields())) {
          Object raw =
              ProductStudioEngine.map(
                      ProductStudioEngine.map(answers)
                          .getOrDefault(q.key(), ProductStudioEngine.empty())
                          .fields())
                  .get(f.key());
          if (raw == null) continue;
          if (!(raw instanceof List<?> ids)) throw new IllegalArgumentException("파일 답변이 잘못되었습니다.");
          for (Object id : ids) {
            ProductStudioAsset a = require(String.valueOf(id));
            if (!a.getOwnerType().equals("STAGED") || !a.getCreatedBy().equals(actor))
              throw new IllegalArgumentException("현재 사용자가 업로드한 파일을 선택해 주세요.");
            if (!ProductStudioEngine.list(f.extensions()).contains(extension(a.getOriginalName()))
                || a.getFileSize() > f.maxFileMB() * 1024L * 1024L)
              throw new IllegalArgumentException(
                  f.labels().customer() + " 파일 형식 또는 용량 제한을 확인해 주세요.");
          }
        }
  }

  @Transactional
  public void bindFixedInputs(List<String> ids, String actor) {
    for (String id : ids) {
      ProductStudioAsset a = require(id);
      // Already bound product input files may be retained by any authorized administrator.
      if (a.getOwnerType().equals("INPUT")) continue;
      if (!a.getOwnerType().equals("STAGED") || !a.getCreatedBy().equals(actor))
        throw new IllegalArgumentException("현재 사용자가 올린 고정 입력 파일만 사용할 수 있습니다.");
      a.setOwnerType("INPUT");
      a.setOwnerId(null);
    }
  }

  public ResponseEntity<Resource> content(ProductStudioAsset a, boolean download) {
    Path p = path(a);
    if (!Files.isRegularFile(p)) throw new NoSuchElementException("첨부파일이 없습니다.");
    boolean inline = a.getContentType().startsWith("image/") && !download;
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(a.getOriginalName(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType(a.getContentType()))
        .contentLength(a.getFileSize())
        .body(new FileSystemResource(p));
  }

  public List<AssetView> publicViews(List<AssetView> views, String token) {
    return views.stream()
        .map(
            a ->
                new AssetView(
                    a.id(),
                    a.name(),
                    a.type(),
                    a.size(),
                    "/product-spec/studio/" + token + "/assets/" + a.id(),
                    a.image()))
        .toList();
  }

  @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
  @Transactional
  public void cleanup() {
    List<ProductStudioAsset> expired =
        repository.findTop200ByOwnerTypeAndCreatedAtBefore(
            "STAGED", LocalDateTime.now().minusHours(24));
    for (ProductStudioAsset a : expired) {
      Path p = path(a);
      repository.delete(a);
      afterCommit(p);
    }
  }

  private static void afterRollback(Path p) {
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status != STATUS_COMMITTED) deleteQuietly(p);
            }
          });
  }

  private static void afterCommit(Path p) {
    if (TransactionSynchronizationManager.isSynchronizationActive())
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              deleteQuietly(p);
            }
          });
    else deleteQuietly(p);
  }

  private static void deleteQuietly(Path p) {
    try {
      Files.deleteIfExists(p);
    } catch (IOException e) {
      log.warn("제품 첨부파일 정리 실패: {}", p, e);
    }
  }
}
