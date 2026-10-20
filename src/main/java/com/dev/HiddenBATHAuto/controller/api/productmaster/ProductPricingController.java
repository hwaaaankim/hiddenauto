package com.dev.HiddenBATHAuto.controller.api.productmaster;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ApiResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductPricingDtos.*;
import com.dev.HiddenBATHAuto.service.productmaster.ProductPricingService;
import com.dev.HiddenBATHAuto.service.productmaster.ProductPricingWire;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/api/product-master/studio")
@PreAuthorize("hasRole('ADMIN')")
public class ProductPricingController {
  private final ProductPricingService service;
  private static ApiResponse<Object> wire(Object value) {
    return ApiResponse.ok(ProductPricingWire.response(value));
  }
  @PostMapping("/pricing/status")
  public ApiResponse<Object> status(@RequestBody StatusRequest request) {
    if (request == null) throw new IllegalArgumentException("제품 ID가 필요합니다.");
    return wire(service.status(request.productIds()));
  }
  @GetMapping({"/products/{id}/pricing", "/products/{id}/pricing/context"})
  public ApiResponse<Object> context(@PathVariable Long id) { return wire(service.context(id)); }
  @PutMapping("/products/{id}/pricing")
  public ApiResponse<Object> save(@PathVariable Long id, @RequestBody SaveRequest request, Principal actor) {
    return wire(service.save(id, request, actor.getName()));
  }
  @DeleteMapping("/products/{id}/pricing")
  public ApiResponse<Object> delete(@PathVariable Long id, @RequestParam Long version, Principal actor) {
    return wire(service.delete(id, version, actor.getName()));
  }
  @PostMapping("/products/{id}/pricing/evaluate")
  public ApiResponse<Object> evaluate(@PathVariable Long id, @RequestBody EvaluateRequest request) {
    return wire(service.evaluate(id, request));
  }
  @PostMapping("/products/{id}/pricing/diagnose")
  public ApiResponse<Object> diagnose(@PathVariable Long id, @RequestBody EvaluateRequest request) {
    return wire(service.diagnose(id, request));
  }
  @GetMapping("/products/{id}/pricing/copy-sources")
  public ApiResponse<Object> copySources(@PathVariable Long id) { return wire(service.copySources(id)); }
  @PostMapping("/products/{id}/pricing/copy")
  public ApiResponse<Object> copy(@PathVariable Long id, @RequestBody CopyRequest request, Principal actor) {
    return wire(service.copy(id, request, actor.getName()));
  }
  @GetMapping("/products/{id}/pricing/actuals")
  public ApiResponse<Object> actuals(@PathVariable Long id) { return wire(service.actuals(id)); }
  @GetMapping("/products/{id}/pricing/actuals/{actualId}")
  public ApiResponse<Object> actual(@PathVariable Long id, @PathVariable Long actualId) {
    return wire(service.actual(id, actualId));
  }
}
