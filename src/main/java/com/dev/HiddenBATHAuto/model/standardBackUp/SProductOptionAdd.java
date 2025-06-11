package com.dev.HiddenBATHAuto.model.standardBackUp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name="tb_product_option_add")
@Data
public class SProductOptionAdd {


	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name="PRODUCT_OPTION_ADD_ID")
	private Long id;
	
	@Column(name="PRODUCT_OPTION_ADD_TEXT")
	private String productOptionAddText;
	
}
