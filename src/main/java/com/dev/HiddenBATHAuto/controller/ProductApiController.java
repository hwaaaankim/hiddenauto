package com.dev.HiddenBATHAuto.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.model.product.BigSort;
import com.dev.HiddenBATHAuto.model.product.MiddleSort;
import com.dev.HiddenBATHAuto.model.product.Product;
import com.dev.HiddenBATHAuto.repository.repository.ProductMiddleSortRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductRepository;
import com.dev.HiddenBATHAuto.service.product.ProductService;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {

	@Autowired
    private ProductService productService;
	
	@Autowired
	ProductRepository productRepository;	

	@Autowired
	ProductMiddleSortRepository productMiddleSortRepository;
	
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

    @GetMapping("/byMiddleSort")
    public ResponseEntity<List<Product>> getProductsByMiddleSortId(@RequestParam Long middleSortId) {
    	System.out.println(middleSortId);
        // MiddleSort 조회
        Optional<MiddleSort> middleSortOpt = productMiddleSortRepository.findById(middleSortId);

        // MiddleSort가 존재하지 않는 경우 404 반환
        if (!middleSortOpt.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        MiddleSort middleSort = middleSortOpt.get();

        // MiddleSort의 이름이 "분류전체"인 경우
        if ("분류전체".equals(middleSort.getName())) {
            BigSort bigSort = middleSort.getBigSort();
            List<Product> products = productRepository.findAllByBigSort(bigSort);
            return ResponseEntity.ok(products);
        }

        // "분류전체"가 아닌 경우 해당 MiddleSort에 맞는 제품 조회
        List<Product> products = productRepository.findAllByMiddleSort(middleSort);
        return ResponseEntity.ok(products);
    }
    
}
