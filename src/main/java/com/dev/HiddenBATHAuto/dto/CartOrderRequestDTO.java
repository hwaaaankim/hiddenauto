package com.dev.HiddenBATHAuto.dto;

import lombok.Data;

@Data
public class CartOrderRequestDTO {
	private Long cartId;
	private Integer quantity;

	// 필수: getter/setter
	public Long getCartId() {
		return cartId;
	}
	public void setCartId(Long cartId) {
		this.cartId = cartId;
	}

	public Integer getQuantity() {
		return quantity;
	}
	public void setQuantity(Integer quantity) {
		this.quantity = quantity;
	}

	@Override
	public String toString() {
		return "CartOrderRequestDto{" +
				"cartId=" + cartId +
				", quantity=" + quantity +
				'}';
	}
}
