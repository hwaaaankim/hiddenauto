package com.dev.HiddenBATHAuto.controller.page.productmaster;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ProductStudioPublicPageController {
  @GetMapping({"/front/productmaster", "/front/productmaster/"})
  public String catalog() {
    return "front/productmaster/studioChat";
  }

  @GetMapping("/front/productmaster/{token}")
  public String product(@PathVariable String token, Model model) {
    model.addAttribute("publicToken", token);
    return "front/productmaster/studioChat";
  }
}
