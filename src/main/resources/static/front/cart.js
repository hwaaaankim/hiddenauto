window.addEventListener('DOMContentLoaded', () => {
	const cart = JSON.parse(localStorage.getItem('cart')) || [];
	const bagIcon = document.getElementById('bag-icon');
	const cartAmount = document.getElementById('cart-product-amount');
	const cartContainer = document.getElementById('cart-container');
	const productContainer = document.getElementById('product-container');

	// 카트에 담긴 제품 종류 업데이트 함수
	function updateCartAmount() {
		if (cartAmount) {
			const uniqueProductCount = cart.length; // 제품의 종류 수
			cartAmount.textContent = uniqueProductCount; // 수량 업데이트
			cartAmount.style.display = uniqueProductCount > 0 ? 'flex' : 'none'; // 수량 없을 시 숨김 처리
		}
	}

	// 장바구니 상태 업데이트 함수
	function updateBagIcon() {
		if (cart.length && bagIcon) {
			bagIcon.classList.add('active');
		} else if (bagIcon) {
			bagIcon.classList.remove('active');
		}
		updateCartAmount(); // 수량 업데이트 호출
	}

	// 제품 삭제 함수
	function removeFromCart(index) {
		cart.splice(index, 1); // 해당 제품 삭제
		localStorage.setItem('cart', JSON.stringify(cart)); // 장바구니 업데이트
		renderCartItems(); // 장바구니 다시 렌더링
		updateBagIcon(); // 아이콘 상태 및 수량 업데이트
	}

	// 수량 변경 처리 함수
	function handleQuantityChange(event) {
		const index = event.target.getAttribute('data-index'); // 고유한 index 값으로 제품을 식별
		const newQuantity = parseInt(event.target.value) || 1; // 수량이 1 미만일 경우 1로 설정

		cart[index].quantity = newQuantity; // 장바구니 데이터 업데이트
		localStorage.setItem('cart', JSON.stringify(cart)); // 로컬 스토리지 업데이트
		updateBagIcon(); // 아이콘 상태 및 수량 업데이트
	}


	function renderCartItem(item, index) {
		const pricePerItem = item.price || 10000;
		const totalPrice = pricePerItem * item.quantity;

		let itemHTML = `
    <div class="card card-style">
        <div class="content mb-0">
            <div class="d-flex mb-4">
                <div style="width: 50%;">
                    <img src="/front/images/pictures/9s.jpg" class="rounded-m shadow-xl" width="130">
                </div>
                <div class="ms-3 p-relative">
                    <h5 class="font-600 mb-0">${item.product || '제품명 없음'}</h5>
                    <h1 class="pt-0">${totalPrice.toLocaleString()}원</h1>
                    <a href="#" class="cart-remove color-theme opacity-50 font-12" data-index="${index}">
                        <i class="fa fa-times color-red-dark pe-2 pt-3"></i>삭제</a>
                </div>
            </div>
    `;

		// row 시작
		itemHTML += `<div class="row mb-0">`;

		// 수량 필드 렌더링
		itemHTML += `
    <div class="col-3">
        <div class="input-style input-style-always-active has-borders no-icon">
            <input required type="number" class="quantity-input form-control focus-color focus-blue"
                data-index="${index}" data-price="${pricePerItem}" value="${item.quantity}" min="1">
            <label class="color-blue-dark">수량</label>
        </div>
    </div>`;

		// 나머지 속성들 렌더링
		for (const [key, value] of Object.entries(item)) {
			if (['product', 'price', 'quantity'].includes(key)) continue;

			itemHTML += `
        <div class="col-3">
            <div class="input-style input-style-always-active has-borders no-icon">
                <label class="color-blue-dark">${key}</label>
                ${Array.isArray(value)
					? `<select>${value.map((v) => `<option ${v === item[key] ? 'selected' : ''}>${v}</option>`).join('')}</select>`
					: `<input type="text" value="${value}" readonly>`}
            </div>
        </div>`;
		}

		// row 종료
		itemHTML += `</div></div></div>`;
		return itemHTML;
	}

	// 전체 장바구니 렌더링 함수
	function renderCartItems() {
		if (!productContainer || !cartContainer) {
			console.error('장바구니 관련 요소를 찾을 수 없습니다.');
			return;
		}

		productContainer.innerHTML = ''; // 초기화

		if (cart.length === 0) {
			cartContainer.innerHTML = `
                <div class="card card-style">
                    <div class="content mb-2">
                        <h3>제품이 없습니다.</h3>
                        <p class="mb-0">장바구니에 등록된 제품이 없습니다.</p>
                    </div>
                </div>`;
			updateBagIcon(); // 수량 숨김 처리
			return;
		}

		// 제품들 렌더링
		cart.forEach((item, index) => {
			const cartItemHTML = renderCartItem(item, index);
			productContainer.innerHTML += cartItemHTML;
		});

		// 삭제 버튼 이벤트 핸들러 추가
		document.querySelectorAll('.cart-remove').forEach((btn) => {
			btn.addEventListener('click', (event) => {
				const index = event.target.getAttribute('data-index');
				removeFromCart(index);
			});
		});

		// 수량 변경 이벤트 핸들러 추가
		document.querySelectorAll('.quantity-input').forEach((input) => {
			input.addEventListener('input', handleQuantityChange);
		});

		updateCartAmount(); // 렌더링 후 수량 업데이트
	}

	// 초기 로드 시 렌더링 및 아이콘 업데이트
	renderCartItems();
	updateBagIcon();
});
