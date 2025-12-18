package com.dev.HiddenBATHAuto.dto;

import java.util.List;

public class ProductionBulkCompleteRequest {
	private List<Long> orderIds;

	public List<Long> getOrderIds() {
		return orderIds;
	}
	public void setOrderIds(List<Long> orderIds) {
		this.orderIds = orderIds;
	}
}