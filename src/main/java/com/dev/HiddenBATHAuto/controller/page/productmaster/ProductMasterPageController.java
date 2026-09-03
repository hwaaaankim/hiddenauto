package com.dev.HiddenBATHAuto.controller.page.productmaster;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/product-master")
@PreAuthorize("hasRole('ADMIN')")
public class ProductMasterPageController {

    @GetMapping
    public String index() {
        return "redirect:/admin/product-master/products";
    }

    @GetMapping("/groups")
    public String groups() {
        return "administration/productmaster/groups";
    }

    @GetMapping("/products")
    public String products() {
        return "administration/productmaster/productList";
    }

    @GetMapping("/automation")
    public String automation() {
        return "administration/productmaster/automation";
    }

    @GetMapping("/products/new")
    public String createProduct(Model model) {
        model.addAttribute("productId", null);
        model.addAttribute("embedded", false);
        return "administration/productmaster/productForm";
    }

    @GetMapping("/products/{productId}")
    public String editProduct(
            @PathVariable Long productId,
            @RequestParam(name = "embedded", defaultValue = "false") boolean embedded,
            Model model
    ) {
        model.addAttribute("productId", productId);
        model.addAttribute("embedded", embedded);
        return "administration/productmaster/productForm";
    }
}
