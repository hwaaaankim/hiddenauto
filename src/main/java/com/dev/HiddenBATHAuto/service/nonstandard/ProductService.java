package com.dev.HiddenBATHAuto.service.nonstandard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.model.nonstandard.Product;
import com.dev.HiddenBATHAuto.model.nonstandard.ProductSort;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductProductSortRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductColorRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductSeriesRepository;
import com.dev.HiddenBATHAuto.repository.nonstandard.ProductRepository;

@Service
public class ProductService {

	@Autowired
	ProductRepository productRepository;

	@Autowired
	ProductSeriesRepository productMiddleSortRepository;

	@Autowired
	ProductProductSortRepository productBigSortRepository;

	@Autowired
	ProductColorRepository productColorRepository;

	@Autowired
	ProductColorService productColorService;

	@Value("${spring.upload.env}")
	private String env;

	@Value("${spring.upload.path}")
	private String commonPath;

	public Page<Product> findAllByBigMiddleTagColor(Pageable pageable, Long id) {
		Page<Product> products = productRepository.findAllByProductSortOrderByProductIndexAsc(pageable,
				productBigSortRepository.findById(id).get());
		return products;
	}

	public List<Product> getProductsByBigSort(String category) {
		ProductSort bigSort = productBigSortRepository.findByName(category);
		return productRepository.findAllByProductSort(bigSort);
	}
	
	public Map<String, List<?>> getProductOptions(Long productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new IllegalArgumentException("Invalid product ID"));
		Map<String, List<?>> options = new HashMap<>();
		options.put("colors", product.getProductColors());
		return options;
	}
	
	// 제품 이름으로 제품 상세 정보 조회
    @Transactional(readOnly = true)
    public Product getProductDetailsByName(String productName) {
        return productRepository.findByName(productName);
    }
}
