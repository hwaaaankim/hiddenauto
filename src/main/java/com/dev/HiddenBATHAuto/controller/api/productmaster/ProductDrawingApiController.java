package com.dev.HiddenBATHAuto.controller.api.productmaster;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductDrawingDtos.DrawingView;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductDrawingDtos.SaveRequest;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductDrawingDtos.TemplatePage;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ApiResponse;
import com.dev.HiddenBATHAuto.service.productmaster.ProductDrawingService;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/api/product-master/drawings")
@PreAuthorize("hasRole('ADMIN')")
public class ProductDrawingApiController {
  private final ProductDrawingService drawings;

  @GetMapping("/{kind:products|actuals}/{id}")
  public ApiResponse<DrawingView> get(@PathVariable("kind") String kind, @PathVariable("id") Long id) {
    return ApiResponse.ok(drawings.get(kind, id));
  }

  @PutMapping("/{kind:products|actuals}/{id}")
  public ApiResponse<DrawingView> save(@PathVariable("kind") String kind, @PathVariable("id") Long id,
      @RequestBody SaveRequest body, Principal principal) {
    return ApiResponse.ok(drawings.save(kind, id, body, principal.getName()));
  }

  @GetMapping("/templates")
  public ApiResponse<TemplatePage> templates(@RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return ApiResponse.ok(drawings.templates(q, page, size));
  }
}
