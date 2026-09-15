package com.dev.HiddenBATHAuto.controller.page.productmaster;

import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.dev.HiddenBATHAuto.service.productmaster.ProductMasterService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ProductPublicSpecController {

    private final ProductMasterService productMasterService;
    private final com.dev.HiddenBATHAuto.repository.productmaster.ProductMasterRepository products;

    @GetMapping("/product-spec/{token}")
    public String publicSpecification(
            @PathVariable String token,
            Model model,
            HttpServletResponse response
    ) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        try {
            var studio = products.findByQrPublicToken(token).orElse(null);
            if (studio != null && studio.getStudioDefinitionJson() != null) {
                if (studio.getStatus() != com.dev.HiddenBATHAuto.enums.productmaster.ProductMasterStatus.ACTIVE) {
                    response.setStatus(HttpStatus.NOT_FOUND.value()); return "error/404";
                }
                model.addAttribute("publicToken", token);
                return "front/productmaster/studioChat";
            }
            model.addAttribute("product", productMasterService.getPublicProduct(token));
            model.addAttribute("publicToken", token);
            return "front/productmaster/productSpec";
        } catch (NoSuchElementException | IllegalArgumentException exception) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return "error/404";
        }
    }
}
