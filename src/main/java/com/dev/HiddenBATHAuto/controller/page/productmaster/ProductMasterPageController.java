package com.dev.HiddenBATHAuto.controller.page.productmaster;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/product-master")
@PreAuthorize("hasRole('ADMIN')")
public class ProductMasterPageController {
  private String page(Model model, String mode) {
    model.addAttribute("studioMode", mode);
    return "administration/productmaster/studio";
  }

  @GetMapping
  public String index() {
    return "redirect:/admin/product-master/products";
  }

  @GetMapping("/groups")
  public String groups(Model m) {
    return page(m, "groups");
  }

  @GetMapping("/products")
  public String products(Model m) {
    return page(m, "standard");
  }

  @GetMapping("/non-standard")
  public String custom(Model m) {
    return page(m, "custom");
  }

  @GetMapping("/products/new")
  public String create(Model m) {
    return page(m, "builder");
  }

  @GetMapping("/products/{productId}")
  public String detail(@PathVariable Long productId, Model m) {
    m.addAttribute("productId", productId);
    return page(m, "detail");
  }

  @GetMapping("/products/{productId}/process")
  public String process(@PathVariable Long productId, Model m) {
    m.addAttribute("productId", productId);
    return page(m, "process");
  }

  @GetMapping("/faq")
  public String faq(Model m) {
    return page(m, "faq");
  }

  @GetMapping("/products/{productId}/view")
  public String view(@PathVariable Long productId, Model m) {
    m.addAttribute("productId", productId);
    return page(m, "view");
  }

  @GetMapping("/products/{productId}/actuals")
  public String actuals(@PathVariable Long productId, Model m) {
    m.addAttribute("productId", productId);
    return page(m, "actuals");
  }

  @GetMapping("/products/{productId}/test")
  public String test(@PathVariable Long productId, Model m) {
    m.addAttribute("adminProductId", productId);
    return "front/productmaster/studioChat";
  }
}
