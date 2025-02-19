package com.dev.HiddenBATHAuto.model.nonstandard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;


// 1차 카테고리
@Entity
@Data
@Table(name="tb_product_sort")
public class ProductSort {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name="PRODUCT_SORT_ID")
	private Long id;
	
	@Column(name="PRODUCT_SORT_NAME")
	private String name;
	
	@Column(name="PRODUCT_SORT_INDEX")
	private int productSortIndex;

}
