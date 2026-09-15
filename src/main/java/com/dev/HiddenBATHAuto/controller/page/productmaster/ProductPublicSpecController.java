package com.dev.HiddenBATHAuto.controller.page.productmaster;

import com.dev.HiddenBATHAuto.service.productmaster.ProductStudioService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class ProductPublicSpecController {
  private final ProductStudioService products;

  @GetMapping("/product-spec/{token}")
  public String spec(@PathVariable String token, Model model, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    try {
      products.publicProduct(token);
      model.addAttribute("publicToken", token);
      return "front/productmaster/studioChat";
    } catch (NoSuchElementException e) {
      response.setStatus(404);
      return "error/404";
    }
  }
}
