package com.dev.HiddenBATHAuto.model.nonstandard;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name="tb_product_file")
@Data
public class ProductFile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name="PRODUCT_FILE_ID")
	private Long id;
	
	@Column(name="PRODUCT_FILE_SIGN")
	private Boolean sign;
	
	@Column(name="PRODUCT_ID")
	private Long productId;
	
	@Column(name="PRODUCT_FILE_ORIGINAL_NAME")
	private String productFileOriginalName;
	
	@Column(name="PRODUCT_FILE_EXTENSION")
	private String productFileExtension;
	
	@Column(name="PRODUCT_FILE_PATH")
	private String productFilePath;
	
	@Column(name="PRODUCT_FILE_NAME")
	private String productFileName;
	
	@Column(name="PRODUCT_FILE_ROAD")
	private String productFileRoad;
	
	@Column(name="PRODUCT_FILE_DATE")
	private Date productFileDate;
	
}
