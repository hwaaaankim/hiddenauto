package com.dev.HiddenBATHAuto.model.standardBackUp;

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
@Table(name="tb_big_sort")
public class SBigSort {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name="BIG_SORT_ID")
	private Long id;
	
	@Column(name="BIG_SORT_NAME")
	private String name;
	
	@Column(name="BIG_SORT_INDEX")
	private int bigSortIndex;

}
