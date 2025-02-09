package com.dev.HiddenBATHAuto.service.product;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.nonstandard.ProductSize;
import com.dev.HiddenBATHAuto.repository.repository.ProductSizeRepository;

@Service
public class ProductSizeService {

	@Autowired
	ProductSizeRepository productSizeRepository;
	
	public void insertProductSize(String[] size) {
		for(String s : size) {
			ProductSize p = new ProductSize();
			p.setProductSizeText(s);
			productSizeRepository.save(p);
		}
	}
	
	public void deleteProductSize(Long[] id) {
		for(Long i : id) {
			productSizeRepository.deleteById(i);
		}
	}
}
