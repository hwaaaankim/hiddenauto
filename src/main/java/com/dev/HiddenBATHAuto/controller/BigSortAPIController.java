package com.dev.HiddenBATHAuto.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.model.product.BigSort;
import com.dev.HiddenBATHAuto.repository.repository.ProductBigSortRepository;

@RestController
@RequestMapping("/api/bigSort")
public class BigSortAPIController {

	@Autowired
    private ProductBigSortRepository ProductBigSortRepository;
	
	@GetMapping("/findByName")
    public ResponseEntity<BigSort> getBigSortByName(@RequestParam String name) {
        BigSort bigSort = ProductBigSortRepository.findByName(name);
        if (bigSort != null) {
            return ResponseEntity.ok(bigSort);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }
}
