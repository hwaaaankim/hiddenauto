package com.dev.HiddenBATHAuto.model.standardBackUp;

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
import jakarta.persistence.Transient;
import lombok.Data;


// 2차 카테고리
@Data
@Entity
@Table(name="tb_middle_sort")
public class SMiddleSort {

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="MIDDLE_SORT_ID")
	private Long id;
	
	@Column(name="MIDDLE_SORT_NAME")
	private String name;
	
	@Transient
	private Long bigId;
	
	@Column(name="MIDDLE_SORT_INDEX")
	private int middleSortIndex;
	
	@OneToOne(fetch = FetchType.EAGER)
	@JoinColumn(
			name="MIDDLE_REFER_ID", referencedColumnName="BIG_SORT_ID"
			)
	private SBigSort bigSort;
	
	@OneToMany(
			mappedBy = "middleSort", 
			fetch = FetchType.LAZY, 
			cascade = CascadeType.ALL
			)
    private List<SProduct> products;
}















