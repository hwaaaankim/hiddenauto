package com.dev.HiddenBATHAuto.model.calculate.top;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

//기타 옵션 가격 (LED, 콘센트, 드라이걸이, 티슈홀캡 등)
@Entity
@Data
@Table(name = "tb_top_option_price")
public class TopOptionPrice {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "option_name")
	private String optionName;

	@Column(name = "price")
	private int price;
}