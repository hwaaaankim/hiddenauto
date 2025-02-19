package com.dev.HiddenBATHAuto.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.model.nonstandard.ProductSort;
import com.dev.HiddenBATHAuto.model.nonstandard.Series;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductProductSortRepository;
import com.dev.HiddenBATHAuto.service.nonstandard.ProductMiddleSortService;

import jakarta.persistence.EntityNotFoundException;

@RestController
@RequestMapping("/api/bigSort")
public class ProductSortAPIController {

	@Autowired
    private ProductProductSortRepository productBigSortRepository;
	
	@Autowired
	ProductMiddleSortService productMiddleSortService;
	
	@GetMapping("/findByName")
    public ResponseEntity<ProductSort> getBigSortByName(@RequestParam String name) {
        ProductSort bigSort = productBigSortRepository.findByName(name);
        if (bigSort != null) {
            return ResponseEntity.ok(bigSort);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }
	
	@GetMapping("/findById")
	public ResponseEntity<ProductSort> findById(@RequestParam Long id) {
	    ProductSort bigSort = productBigSortRepository.findById(id)
	        .orElseThrow(() -> new EntityNotFoundException("BigSort not found"));
	    return ResponseEntity.ok(bigSort);
	}
	
	@GetMapping("/{bigSortId}")
    public ResponseEntity<List<Series>> getMiddleSortsWithProducts(
            @PathVariable Long bigSortId) {
        List<Series> middleSorts = productMiddleSortService.getMiddleSortsWithProductsByBigSortId(bigSortId);
        return ResponseEntity.ok(middleSorts);
    }

}
