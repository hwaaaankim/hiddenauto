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

	// ✅ [추가] 시리즈 대표 이미지 정보
	@Column(name="SERIES_REP_IMAGE_NAME")
	private String seriesRepImageName;

	@Column(name="SERIES_REP_IMAGE_EXTENSION")
	private String seriesRepImageExtension;

	@Column(name="SERIES_REP_IMAGE_ORIGINAL_NAME")
	private String seriesRepImageOriginalName;

	@Column(name="SERIES_REP_IMAGE_PATH")
	private String seriesRepImagePath;

	@Column(name="SERIES_REP_IMAGE_ROAD")
	private String seriesRepImageRoad;

	@OneToOne(fetch = FetchType.EAGER)
	@JoinColumn(name="SERIES_REFER_ID", referencedColumnName="PRODUCT_SORT_ID")
	private ProductSort productSort;

	@OneToMany(
			mappedBy = "series",
			fetch = FetchType.LAZY,
			cascade = CascadeType.ALL
	)
	private List<Product> products;
}














