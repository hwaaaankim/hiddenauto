package com.dev.HiddenBATHAuto.dto;

import java.time.LocalDateTime;

import jakarta.persistence.Transient;
import lombok.Data;

@Data
public class CompanyListDTO {
	private Long id;
	private String companyName;
	private String representativeName;
	private LocalDateTime createdAt;
	private String salesManagerName;
	private Long employeeCount;

	@Transient
	public String getDisplaySalesManagerName() {
		return salesManagerName != null ? salesManagerName : "미지정";
	}

	public CompanyListDTO(Long companyId, String companyName, String representativeName, LocalDateTime createdAt,
			String salesManagerName, Long employeeCount) {
		this.id = companyId;
		this.companyName = companyName;
		this.representativeName = representativeName;
		this.createdAt = createdAt;
		this.salesManagerName = salesManagerName;
		this.employeeCount = employeeCount;
	}
}
