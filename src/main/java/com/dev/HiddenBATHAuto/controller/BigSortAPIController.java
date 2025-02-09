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

import com.dev.HiddenBATHAuto.model.nonstandard.BigSort;
import com.dev.HiddenBATHAuto.model.nonstandard.MiddleSort;
import com.dev.HiddenBATHAuto.repository.repository.ProductBigSortRepository;
import com.dev.HiddenBATHAuto.service.product.ProductMiddleSortService;

import jakarta.persistence.EntityNotFoundException;

@RestController
@RequestMapping("/api/bigSort")
public class BigSortAPIController {

	@Autowired
    private ProductBigSortRepository productBigSortRepository;
	
	@Autowired
	ProductMiddleSortService productMiddleSortService;
	
	@GetMapping("/findByName")
    public ResponseEntity<BigSort> getBigSortByName(@RequestParam String name) {
        BigSort bigSort = productBigSortRepository.findByName(name);
        if (bigSort != null) {
            return ResponseEntity.ok(bigSort);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }
	
	@GetMapping("/findById")
	public ResponseEntity<BigSort> findById(@RequestParam Long id) {
	    BigSort bigSort = productBigSortRepository.findById(id)
	        .orElseThrow(() -> new EntityNotFoundException("BigSort not found"));
	    return ResponseEntity.ok(bigSort);
	}
	
	@GetMapping("/{bigSortId}")
    public ResponseEntity<List<MiddleSort>> getMiddleSortsWithProducts(
            @PathVariable Long bigSortId) {
        List<MiddleSort> middleSorts = productMiddleSortService.getMiddleSortsWithProductsByBigSortId(bigSortId);
        return ResponseEntity.ok(middleSorts);
    }

}
