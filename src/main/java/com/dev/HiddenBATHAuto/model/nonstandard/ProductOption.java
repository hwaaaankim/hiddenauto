package com.dev.HiddenBATHAuto.model.nonstandard;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name="tb_product_option")
@Data
public class ProductOption {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name="PRODUCT_OPTION_ID")
	private Long id;
	
	@Column(name="PRODUCT_OPTION_TEXT")
	private String productOptionText;
	
	@ManyToMany(mappedBy = "productOptions", fetch = FetchType.EAGER)
	@JsonIgnore
    private List<Product> products;
}
