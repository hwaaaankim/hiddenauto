package com.dev.HiddenBATHAuto.controller.page.productmaster;

import com.dev.HiddenBATHAuto.config.productmaster.ProductDrawingProperties;
import com.dev.HiddenBATHAuto.service.productmaster.ProductDrawingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/admin/product-master")
public class ProductDrawingPageController {
  private final ProductDrawingService drawings;
  private final ProductDrawingProperties properties;

  @GetMapping("/products/{id}/drawing")
  public String product(@PathVariable Long id, Model model) {
    return page("products", id, model);
  }

  @GetMapping("/actuals/{id}/drawing")
  public String actual(@PathVariable Long id, Model model) {
    return page("actuals", id, model);
  }

  private String page(String kind, Long id, Model model) {
    var drawing = drawings.get(kind, id);
    model.addAttribute("drawingKind", kind);
    model.addAttribute("drawingTargetId", id);
    model.addAttribute("editorUrl", properties.getEditorUrl());
    model.addAttribute("aiDiagnosticsEnabled", properties.isDiagnosticLoggingEnabled());
    model.addAttribute("aiDiagnosticLogMaxChars", properties.getDiagnosticLogMaxChars());
    model.addAttribute("backUrl", "actuals".equals(kind)
        ? "/admin/product-master/products/" + drawing.productId() + "/actuals"
        : "/admin/product-master/products");
    return "administration/productmaster/drawing";
  }
}
