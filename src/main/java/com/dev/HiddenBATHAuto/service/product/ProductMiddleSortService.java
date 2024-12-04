package com.dev.HiddenBATHAuto.service.product;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.product.MiddleSort;
import com.dev.HiddenBATHAuto.repository.repository.ProductMiddleSortRepository;

@Service
public class ProductMiddleSortService {

	@Autowired
	ProductMiddleSortRepository productMiddleSortRepository;
	
	public void deleteProductMiddleSort(Long[] id) {
		for(Long i : id) {
			productMiddleSortRepository.deleteById(i);
		}
	}
	// BigSort ID를 이용해 MiddleSort와 Product 데이터 가져오기
    public List<MiddleSort> getMiddleSortsWithProductsByBigSortId(Long bigSortId) {
        return productMiddleSortRepository.findMiddleSortsByBigSortIdWithOrderedProducts(bigSortId);
    }
}
