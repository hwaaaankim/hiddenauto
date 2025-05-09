window.addEventListener('DOMContentLoaded', () => {
	const bagIcon = document.getElementById('bag-icon');
	const cartAmount = document.getElementById('cart-product-amount');
	const cartContainer = document.getElementById('cart-container');
	const productContainer = document.getElementById('product-container');

	// 카트에 담긴 제품 종류 업데이트 함수
	function updateCartAmount(cart = null) {
		if (!cart) cart = JSON.parse(localStorage.getItem('cart')) || [];
		if (cartAmount) {
			const uniqueProductCount = cart.length;
			cartAmount.textContent = uniqueProductCount;
			cartAmount.style.display = uniqueProductCount > 0 ? 'flex' : 'none';
		}
	}

	// 장바구니 상태 업데이트 함수
	function updateBagIcon() {
		const cart = JSON.parse(localStorage.getItem('cart')) || [];
		if (cart.length && bagIcon) {
			bagIcon.classList.add('active');
		} else if (bagIcon) {
			bagIcon.classList.remove('active');
		}
		updateCartAmount(cart);
	}

	// 제품 삭제 함수
	function removeFromCart(index) {
		const cart = JSON.parse(localStorage.getItem('cart')) || [];
		cart.splice(index, 1);
		localStorage.setItem('cart', JSON.stringify(cart));
		renderCartItems();
		updateBagIcon();
	}

	// 수량 변경 처리 함수
	function handleQuantityChange(event) {
		const index = event.target.getAttribute('data-index');
		const newQuantity = parseInt(event.target.value) || 1;

		const cart = JSON.parse(localStorage.getItem('cart')) || [];
		cart[index].quantity = newQuantity;
		localStorage.setItem('cart', JSON.stringify(cart));
		updateBagIcon();
	}

	// 제품 1개 렌더링 함수
	function renderCartItem(item, index) {
		const pricePerItem = item.price || 10000;
		const totalPrice = pricePerItem * item.quantity;
		const option = item.optionJson || {};
		const productName = option.product || '제품명 없음';
		const code = option.code || 'CODE';
	
		let itemHTML = `
		<div class="card card-style">
			<div class="content mb-0">
				<div class="d-flex mb-4">
					<div style="width: 50%;">
						<img src="/front/images/pictures/9s.jpg" class="rounded-m shadow-xl" width="130">
					</div>
					<div class="ms-3 p-relative">
						<h5 class="font-600 mb-0">${productName}</h5>
						<h1 class="pt-0">${totalPrice.toLocaleString()}원</h1>
						<a href="#" class="cart-remove color-theme opacity-50 font-12" data-index="${index}">
							<i class="fa fa-times color-red-dark pe-2 pt-3"></i>삭제</a>
					</div>
				</div>
				<div class="row mb-0">
					<div class="col-3">
						<div class="input-style input-style-always-active has-borders no-icon">
							<input required type="number" class="quantity-input form-control focus-color focus-blue"
								data-index="${index}" data-price="${pricePerItem}" value="${item.quantity}" min="1">
							<label class="color-blue-dark">수량</label>
						</div>
					</div>`;
	
		for (const [key, value] of Object.entries(option)) {
			if (['product', 'code'].includes(key)) continue;
		
			let displayValue;
			if (typeof value === 'object' && value !== null) {
				// category, middleSort 같은 경우 label 있으면 그걸 표시
				if ('label' in value) {
					displayValue = value.label;
				} else {
					displayValue = JSON.stringify(value); // fallback
				}
			} else {
				displayValue = value;
			}
		
			itemHTML += `
				<div class="col-3">
					<div class="input-style input-style-always-active has-borders no-icon">
						<label class="color-blue-dark">${key}</label>
						<input type="text" value="${displayValue}" readonly>
					</div>
				</div>`;
		}
	
		itemHTML += `</div></div></div>`;
		return itemHTML;
	}

	// 전체 장바구니 렌더링 함수
	function renderCartItems() {
		const cart = JSON.parse(localStorage.getItem('cart')) || [];

		if (!productContainer || !cartContainer) {
			console.error('장바구니 관련 요소를 찾을 수 없습니다.');
			return;
		}

		productContainer.innerHTML = '';

		if (cart.length === 0) {
			cartContainer.innerHTML = `
				<div class="card card-style">
					<div class="content mb-2">
						<h3>제품이 없습니다.</h3>
						<p class="mb-0">장바구니에 등록된 제품이 없습니다.</p>
					</div>
				</div>`;
			updateBagIcon();
			return;
		}

		cart.forEach((item, index) => {
			productContainer.innerHTML += renderCartItem(item, index);
		});

		document.querySelectorAll('.cart-remove').forEach((btn) => {
			btn.addEventListener('click', (event) => {
				const index = event.target.getAttribute('data-index');
				removeFromCart(index);
			});
		});

		document.querySelectorAll('.quantity-input').forEach((input) => {
			input.addEventListener('input', handleQuantityChange);
		});

		updateCartAmount(cart);
	}

	// 초기 로드 시
	renderCartItems();
	updateBagIcon();

	// 전역 등록
	window.updateCartAmount = updateCartAmount;
	window.updateBagIcon = updateBagIcon;
	window.renderCartItems = renderCartItems;
});
