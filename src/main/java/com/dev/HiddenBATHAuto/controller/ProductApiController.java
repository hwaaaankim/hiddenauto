package com.dev.HiddenBATHAuto.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.model.product.Product;
import com.dev.HiddenBATHAuto.repository.repository.ProductRepository;
import com.dev.HiddenBATHAuto.service.product.ProductService;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {

	@Autowired
    private ProductService productService;
	
	@Autowired
	ProductRepository productRepository;	

    // 카테고리에 맞는 제품 목록을 반환
    @GetMapping("/byCategory")
    public List<Product> getProductsByCategory(@RequestParam String category) {
    	System.out.println(category);
        return productService.getProductsByBigSort(category);
    }

    // 제품 ID로 색상과 사이즈를 조회
    @GetMapping("/{productId}/options")
    public Map<String, List<?>> getProductOptions(@PathVariable Long productId) {
        return productService.getProductOptions(productId);
    }
    
    @GetMapping("/getProductDetails")
    public Product getProductDetails(@RequestParam Long productId) {
    	System.out.println(productId);
        return productRepository.findById(productId).get();
    }
}
