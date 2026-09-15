package com.dev.HiddenBATHAuto.controller.api.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ApiResponse;
import com.dev.HiddenBATHAuto.service.productmaster.*;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/product-spec/studio")
public class ProductStudioPublicApiController {
  private final ProductStudioService service;
  private final ProductStudioAssetService assets;

  @PostMapping("/catalog")
  public ApiResponse<CatalogResult> catalog(
      @RequestBody Map<Long, String> selected, @RequestParam(defaultValue = "0") int page) {
    return ApiResponse.ok(service.catalog(selected, page));
  }

  @GetMapping("/{token}/schema")
  public ApiResponse<PublicProduct> schema(@PathVariable String token) {
    return ApiResponse.ok(service.publicSchema(token));
  }

  @PostMapping("/{token}/evaluate")
  public ApiResponse<Evaluation> evaluate(
      @PathVariable String token, @RequestBody Map<String, Answer> answers, HttpSession session) {
    return ApiResponse.ok(service.evaluatePublic(token, answers, actor(session)));
  }

  @PostMapping(value = "/{token}/upload", consumes = "multipart/form-data")
  public ApiResponse<List<AssetView>> upload(
      @PathVariable String token,
      @RequestPart("files") List<MultipartFile> files,
      HttpSession session) {
    service.publicProduct(token);
    int count = session.getAttribute("pmUploadCount") instanceof Integer n ? n : 0;
    if (count + files.size() > 100)
      throw new IllegalArgumentException("현재 세션의 첨부파일 개수 제한을 초과했습니다.");
    List<AssetView> staged = assets.stage(files, actor(session));
    session.setAttribute("pmUploadCount", count + staged.size());
    return ApiResponse.ok(
        staged.stream()
            .map(
                a ->
                    new AssetView(
                        a.id(),
                        a.name(),
                        a.type(),
                        a.size(),
                        "/product-spec/studio/" + token + "/uploads/" + a.id(),
                        a.image()))
            .toList());
  }

  @GetMapping("/{token}/assets/{id}")
  public ResponseEntity<Resource> content(
      @PathVariable String token,
      @PathVariable String id,
      @RequestParam(defaultValue = "false") boolean download) {
    return assets.content(service.publicAsset(token, id), download);
  }

  @GetMapping("/{token}/uploads/{id}")
  public ResponseEntity<Resource> customerContent(
      @PathVariable String token,
      @PathVariable String id,
      @RequestParam(defaultValue = "false") boolean download,
      HttpSession session) {
    service.publicProduct(token);
    var a = assets.require(id);
    if (!a.getOwnerType().equals("STAGED") || !a.getCreatedBy().equals(actor(session)))
      throw new java.util.NoSuchElementException("첨부파일이 없습니다.");
    return assets.content(a, download);
  }

  private String actor(HttpSession s) {
    synchronized (s) {
      if (s.getAttribute("pmFileActor") == null)
        s.setAttribute("pmFileActor", "PUBLIC:" + UUID.randomUUID());
      return String.valueOf(s.getAttribute("pmFileActor"));
    }
  }
}
