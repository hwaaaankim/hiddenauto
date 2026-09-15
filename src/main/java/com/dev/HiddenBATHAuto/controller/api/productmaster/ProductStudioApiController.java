package com.dev.HiddenBATHAuto.controller.api.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ApiResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import com.dev.HiddenBATHAuto.service.productmaster.*;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/api/product-master/studio")
@PreAuthorize("hasRole('ADMIN')")
public class ProductStudioApiController {
  private final ProductStudioAttributeService attributes;
  private final ProductStudioService service;
  private final ProductStudioAssetService assets;
  private final com.dev.HiddenBATHAuto.repository.productmaster.ProductMasterRepository products;

  @GetMapping("/summary")
  public ApiResponse<java.util.Map<String, Long>> summary() {
    return ApiResponse.ok(
        java.util.Map.of(
            "standard",
            products.countByNonStandard(false),
            "custom",
            products.countByNonStandard(true),
            "draft",
            products.countByNonStandardAndStatus(
                true, com.dev.HiddenBATHAuto.enums.productmaster.ProductMasterStatus.DRAFT)));
  }

  private final com.dev.HiddenBATHAuto.service.productmaster.ProductInventoryService inventory;

  @PostMapping("/bootstrap")
  public ApiResponse<List<GroupView>> bootstrap(Principal p) {
    return ApiResponse.ok(attributes.bootstrap(p.getName()));
  }

  @GetMapping("/groups")
  public ApiResponse<List<GroupView>> groups() {
    return ApiResponse.ok(attributes.catalog());
  }

  @PostMapping("/groups")
  public ApiResponse<GroupView> group(@RequestBody GroupEdit r, Principal p) {
    return ApiResponse.ok(attributes.saveGroup(r, p.getName()));
  }

  @DeleteMapping("/groups/{id}")
  public ApiResponse<Void> deleteGroup(@PathVariable Long id, Principal p) {
    attributes.deleteGroup(id, p.getName());
    return ApiResponse.ok(null);
  }

  @PostMapping("/groups/reorder")
  public ApiResponse<List<GroupView>> reorderGroups(@RequestBody List<Long> ids, Principal p) {
    return ApiResponse.ok(attributes.reorderGroups(ids, p.getName()));
  }

  @PostMapping("/groups/{id}/values")
  public ApiResponse<List<ValueView>> values(
      @PathVariable Long id, @RequestBody List<ValueEdit> r, Principal p) {
    return ApiResponse.ok(attributes.saveValues(id, r, p.getName()));
  }

  @DeleteMapping("/values/{id}")
  public ApiResponse<Void> deleteValue(@PathVariable Long id, Principal p) {
    attributes.deleteValue(id, p.getName());
    return ApiResponse.ok(null);
  }

  @PostMapping("/groups/{id}/values/reorder")
  public ApiResponse<GroupView> reorderValues(
      @PathVariable Long id, @RequestBody List<Long> ids, Principal p) {
    return ApiResponse.ok(attributes.reorderValues(id, ids, p.getName()));
  }

  @PostMapping("/preview")
  public ApiResponse<Preview> preview(@RequestBody GenerateRequest r) {
    return ApiResponse.ok(service.preview(r));
  }

  @PostMapping("/register")
  public ApiResponse<List<Long>> register(@RequestBody RegisterRequest r, Principal p) {
    return ApiResponse.ok(service.register(r, p.getName()));
  }

  @PostMapping("/products/search")
  public ApiResponse<ProductPage> search(@RequestBody ProductFilter r) {
    return ApiResponse.ok(service.search(r));
  }

  @GetMapping("/products/{id}")
  public ApiResponse<ProductView> detail(@PathVariable Long id) {
    return ApiResponse.ok(service.detail(id));
  }

  @PutMapping("/products/{id}")
  public ApiResponse<ProductView> update(
      @PathVariable Long id, @RequestBody ProductEdit r, Principal p) {
    return ApiResponse.ok(service.update(id, r, p.getName()));
  }

  @PostMapping("/products/{id}/validate")
  public ApiResponse<Validation> validate(@PathVariable Long id, @RequestBody Process r) {
    return ApiResponse.ok(service.validateProcess(id, r));
  }

  @PostMapping("/products/{id}/evaluate")
  public ApiResponse<Evaluation> evaluate(
      @PathVariable Long id, @RequestBody EvaluateRequest r, Principal p) {
    return ApiResponse.ok(service.evaluate(id, r, p.getName()));
  }

  @PutMapping("/products/{id}/process")
  public ApiResponse<ProductView> process(
      @PathVariable Long id, @RequestBody ProcessSave r, Principal p) {
    return ApiResponse.ok(service.saveProcess(id, r, p.getName()));
  }

  @PostMapping(value = "/assets/stage", consumes = "multipart/form-data")
  public ApiResponse<List<AssetView>> stage(
      @RequestPart("files") List<MultipartFile> files, Principal p) {
    return ApiResponse.ok(assets.stage(files, p.getName()));
  }

  @PutMapping("/assets/{type}/{id}")
  public ApiResponse<List<AssetView>> attach(
      @PathVariable String type,
      @PathVariable Long id,
      @RequestBody List<String> ids,
      Principal p) {
    service.validateAttachmentOwner(type, id);
    assets.attach(type, id, ids, p.getName());
    return ApiResponse.ok(assets.owned(type, id));
  }

  @GetMapping("/assets/{id}")
  public ResponseEntity<Resource> content(
      @PathVariable String id,
      @RequestParam(defaultValue = "false") boolean download,
      Principal p) {
    var file = assets.require(id);
    if (file.getOwnerType().equals("STAGED") && !file.getCreatedBy().equals(p.getName()))
      throw new java.util.NoSuchElementException("첨부파일이 없습니다.");
    return assets.content(file, download);
  }

  @GetMapping("/products/{id}/stock")
  public ApiResponse<com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.StockView> stock(
      @PathVariable Long id) {
    return ApiResponse.ok(inventory.detail(id));
  }

  @PostMapping("/products/{id}/stock")
  public ApiResponse<com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.StockView>
      adjustStock(
          @PathVariable Long id,
          @RequestBody
              com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.StockAdjustmentRequest
                  request,
          Principal principal) {
    return ApiResponse.ok(inventory.adjust(id, request, principal.getName()));
  }

  @PostMapping("/products/{id}/stock/{movementId}/void")
  public ApiResponse<com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.StockView>
      voidStock(
          @PathVariable Long id,
          @PathVariable Long movementId,
          @RequestBody
              com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.VoidStockMovementRequest
                  request,
          Principal principal) {
    return ApiResponse.ok(
        inventory.voidMovement(id, movementId, request.reason(), principal.getName()));
  }
}
