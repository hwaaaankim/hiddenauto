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
@lombok.RequiredArgsConstructor
public class ProductMasterPageController {
    private final com.dev.HiddenBATHAuto.service.productmaster.ProductStudioService studio;

    @GetMapping("/legacy/groups")
    public String legacyGroups() { return "administration/productmaster/groups"; }

    @GetMapping("/legacy/products")
    public String legacyProducts() { return "administration/productmaster/productList"; }

    @GetMapping("/non-standard")
    public String custom(Model model) { model.addAttribute("studioMode", "custom"); return "administration/productmaster/studio"; }

    @GetMapping("/products/{productId}/process")
    public String process(@PathVariable Long productId, Model model) { model.addAttribute("studioMode", "process"); model.addAttribute("productId", productId); return "administration/productmaster/studio"; }

    @GetMapping("/legacy/products/{productId}")
    public String legacyProduct(@PathVariable Long productId, Model model) { model.addAttribute("productId", productId); model.addAttribute("embedded", false); return "administration/productmaster/productForm"; }


    @GetMapping
    public String index() {
        return "redirect:/admin/product-master/products";
    }

    @GetMapping("/groups")
    public String groups(Model model) {
        model.addAttribute("studioMode", "groups");
        return "administration/productmaster/studio";
    }

    @GetMapping("/products")
    public String products(Model model) {
        model.addAttribute("studioMode", "standard");
        return "administration/productmaster/studio";
    }

    @GetMapping("/automation")
    public String automation() {
        return "administration/productmaster/automation";
    }

    @GetMapping("/products/new")
    public String createProduct(Model model) {
        model.addAttribute("productId", null);
        model.addAttribute("studioMode", "builder");
        return "administration/productmaster/studio";
    }

    @GetMapping("/products/{productId}")
    public String editProduct(
            @PathVariable Long productId,
            @RequestParam(name = "embedded", defaultValue = "false") boolean embedded,
            Model model
    ) {
        if (!studio.detail(productId).legacy()) {
            model.addAttribute("productId", productId); model.addAttribute("studioMode", "detail");
            return "administration/productmaster/studio";
        }
        model.addAttribute("productId", productId);
        model.addAttribute("embedded", embedded);
        return "administration/productmaster/productForm";
    }
}
