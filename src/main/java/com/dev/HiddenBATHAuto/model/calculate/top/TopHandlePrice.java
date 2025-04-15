package com.dev.HiddenBATHAuto.model.calculate.top;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

//손잡이 가격
@Entity
@Data
@Table(name = "tb_top_handle_price")
public class TopHandlePrice {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "handle_name")
    private String handleName;

	@Column(name = "price")
	private int price;
}
