package com.dev.HiddenBATHAuto.controller.page.productmaster;

import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

/** Read-only, administrator-only customer storefront preview. */
@Controller
@PreAuthorize("hasRole('ADMIN')")
public class ProductOrderExampleController {
  @GetMapping({"/admin/customer-order-example", "/admin/customer-order-example/products/{id}"})
  public String page() {
    return "administration/productmaster/customerOrderExample";
  }
}
