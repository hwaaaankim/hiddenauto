package com.dev.HiddenBATHAuto.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.dev.HiddenBATHAuto.model.standard.StandardCategory;
import com.dev.HiddenBATHAuto.model.standard.StandardProduct;
import com.dev.HiddenBATHAuto.repository.standard.StandardProductRepository;
import com.dev.HiddenBATHAuto.service.standard.StandardCategoryService;
import com.dev.HiddenBATHAuto.service.standard.StandardProductService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class StandardOrderController {

	
	private final StandardProductService standardProductService;
	private final StandardCategoryService standardCategoryService;
	private final StandardProductRepository standardProductRepository;
	
	@GetMapping("/standardOrderProduct")
	public String standardOrderProduct(Model model) {
	    List<StandardProduct> productList = standardProductService.findAll(); // 전체 제품
	    List<StandardCategory> categoryList = standardCategoryService.findAll(); // 카테고리 목록

	    model.addAttribute("productList", productList);
	    model.addAttribute("categoryList", categoryList);
	    return "front/standardOrder/standardOrderProduct";
	}
	
	@GetMapping("/standardOrderSelect/{id}")
	public String viewStandardProductDetail(@PathVariable Long id, Model model) {
	    StandardProduct product = standardProductRepository.findById(id)
	        .orElseThrow(() -> new IllegalArgumentException("해당 제품이 존재하지 않습니다: " + id));

	    model.addAttribute("product", product);
	    return "front/standardOrder/standardOrderDetail"; // detail.html
	}
}
