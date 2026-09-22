package com.dev.HiddenBATHAuto.controller.api.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import static com.dev.HiddenBATHAuto.service.productmaster.ProductStudioExtensionService.*;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ApiResponse;
import com.dev.HiddenBATHAuto.service.productmaster.*;
import java.security.Principal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/api/product-master/studio")
@PreAuthorize("hasRole('ADMIN')")
public class ProductStudioExtensionController {
  private final ProductStudioExtensionService extension;
  private final ProductStudioService studio;

  public record CopyRequest(String productName, List<Variant> fixed) {}

  @PostMapping("/validate-question")
  public Object validateQuestion(@RequestBody Question q) {
    return ApiResponse.ok(
        ProductStudioEngine.validate(
            new com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process(
                2, List.of(q), List.of()),
            Map.of()));
  }

  @GetMapping("/faq")
  public Object topics() {
    return ApiResponse.ok(extension.topics());
  }

  @PostMapping("/faq")
  public Object save(@RequestBody TopicEdit r, Principal p) {
    return ApiResponse.ok(extension.saveTopic(r, p.getName()));
  }

  @DeleteMapping("/faq/{id}")
  public Object delete(@PathVariable Long id, @RequestParam Long version, Principal p) {
    extension.deleteTopic(id, version, p.getName());
    return ApiResponse.ok(null);
  }

  @GetMapping("/products/{id}/actuals")
  public Object actuals(@PathVariable Long id) {
    return ApiResponse.ok(extension.actuals(id));
  }

  @PostMapping("/products/{id}/actuals")
  public Object actual(@PathVariable Long id, @RequestBody ActualEdit r, Principal p) {
    return ApiResponse.ok(extension.createActual(id, r, p.getName()));
  }

  @PostMapping("/products/{id}/actuals/{child}/stock")
  public Object stock(
      @PathVariable Long id, @PathVariable Long child, @RequestBody StockEdit r, Principal p) {
    return ApiResponse.ok(extension.stock(id, child, r, p.getName()));
  }

  @GetMapping("/products/{id}/actuals/{child}/history")
  public Object history(@PathVariable Long id, @PathVariable Long child) {
    return ApiResponse.ok(extension.history(id, child));
  }

  @GetMapping("/decode")
  public Object decode(@RequestParam String code) {
    return ApiResponse.ok(extension.decode(code));
  }

  @PostMapping("/products/{id}/copy")
  public Object copy(@PathVariable Long id, @RequestBody CopyRequest r, Principal p) {
    return ApiResponse.ok(studio.copyProduct(id, r.productName(), r.fixed(), p.getName()));
  }

  @DeleteMapping("/products/{id}")
  public Object deleteProduct(@PathVariable Long id, Principal p) {
    studio.deleteProduct(id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/products/{id}/preview-schema")
  public Object schema(@PathVariable Long id) {
    return ApiResponse.ok(studio.previewSchema(id));
  }
}
