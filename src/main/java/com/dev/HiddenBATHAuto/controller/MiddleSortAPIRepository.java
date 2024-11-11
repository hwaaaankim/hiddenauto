package com.dev.HiddenBATHAuto.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.model.product.MiddleSort;
import com.dev.HiddenBATHAuto.repository.repository.ProductBigSortRepository;
import com.dev.HiddenBATHAuto.repository.repository.ProductMiddleSortRepository;

@RestController
@RequestMapping("/api/middleSort")
public class MiddleSortAPIRepository {

	@Autowired
	private ProductMiddleSortRepository productMiddleSortRepository;
	
	@Autowired
	private ProductBigSortRepository productBigSortRepository;

	// BigSort ID를 통해 MiddleSort 리스트 조회
	@GetMapping("/findByBigSortId")
	public ResponseEntity<List<MiddleSort>> getMiddleSortByBigSortId(@RequestParam Long bigSortId) {
		List<MiddleSort> middleSorts = productMiddleSortRepository.findAllByBigSort(productBigSortRepository.findById(bigSortId).get());
		return ResponseEntity.ok(middleSorts);
	}
	
}
















