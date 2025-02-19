package com.dev.HiddenBATHAuto.service.nonstandard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.repository.nonstandard.ProductProductSortRepository;

@Service
public class ProductBigSortService {

	@Autowired
	ProductProductSortRepository productBigSortRepository;
	
	public void deleteProductBigSort(Long[] id) {
		for(Long i : id) {
			productBigSortRepository.deleteById(i);
		}
	}
}
