package com.dev.HiddenBATHAuto.controller.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.model.nonstandard.Series;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductProductSortRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductSeriesRepository;

@RestController
@RequestMapping("/api/series")
public class SeriesAPIController {

	@Autowired
	private ProductSeriesRepository productMiddleSortRepository;
	
	@Autowired
	private ProductProductSortRepository productBigSortRepository;

	// BigSort ID를 통해 MiddleSort 리스트 조회
	@GetMapping("/findByBigSortId")
	public ResponseEntity<List<Series>> getMiddleSortByBigSortId(@RequestParam Long bigSortId) {
		List<Series> series = productMiddleSortRepository.findAllByProductSort(productBigSortRepository.findById(bigSortId).get());
		return ResponseEntity.ok(series);
	}
	
}
















