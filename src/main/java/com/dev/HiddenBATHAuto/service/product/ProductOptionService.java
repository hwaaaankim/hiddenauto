package com.dev.HiddenBATHAuto.service.product;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.repository.repository.ProductOptionRepository;

@Service
public class ProductOptionService {

	@Autowired
	ProductOptionRepository productOptionRepository;
	
	public void deleteProductOption(Long[] id) {
		for(Long i : id) {
			productOptionRepository.deleteById(i);
		}
	}
}
