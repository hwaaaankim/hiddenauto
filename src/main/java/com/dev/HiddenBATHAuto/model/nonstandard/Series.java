package com.dev.HiddenBATHAuto.model.nonstandard;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;


// 2차 카테고리
@Data
@Entity
@Table(name="tb_series")
public class Series {

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="SERIES_ID")
	private Long id;
	
	@Column(name="SERIES_NAME")
	private String name;
	
	@Column(name="SERIES_INDEX")
	private int seriesIndex;
	
	@OneToOne(fetch = FetchType.EAGER)
	@JoinColumn(
			name="SERIES_REFER_ID", referencedColumnName="PRODUCT_SORT_ID"
			)
	private ProductSort productSort;
	
	@OneToMany(
			mappedBy = "series", 
			fetch = FetchType.LAZY, 
			cascade = CascadeType.ALL
			)
    private List<Product> products;
}















