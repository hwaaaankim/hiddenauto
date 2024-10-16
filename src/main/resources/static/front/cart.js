window.addEventListener('DOMContentLoaded', () => {
	const cart = JSON.parse(localStorage.getItem('cart')) || [];
	const bagIcon = document.getElementById('bag-icon');
	const cartContainer = document.getElementById('cart-container');
	const productContainer = document.getElementById('product-container');
	const priceContainer = document.getElementById('price-container');

	// 장바구니 상태 업데이트 함수
	function updateBagIcon() {
		if (cart.length && bagIcon) {
			bagIcon.classList.add('active');
		} else if (bagIcon) {
			bagIcon.classList.remove('active');
		}
	}

	// 총 금액, 포인트 적용 금액, 최종 금액 계산 함수
	function calculateTotal() {
		const totalAmountElement = document.getElementById('total-amount');
		const pointUsageElement = document.getElementById('point-usage');
		const finalAmountElement = document.getElementById('final-amount');

		if (!totalAmountElement || !pointUsageElement || !finalAmountElement) {
			console.error('금액을 표시할 HTML 요소를 찾을 수 없습니다.');
			return;
		}

		const totalAmount = cart.reduce((sum, item) => sum + ((item.price || 10000) * item.quantity), 0); // 가격 없으면 기본값 10000
		const pointInputValue = parseInt(document.getElementById('point-input').value) || 0;
		const pointUsage = Math.min(pointInputValue, 10000); // 포인트 사용 금액

		totalAmountElement.innerText = `${totalAmount.toLocaleString()} 원`;
		pointUsageElement.innerText = `-${pointUsage.toLocaleString()} 원`;
		finalAmountElement.innerText = `${(totalAmount - pointUsage).toLocaleString()} 원`;
	}

	// 제품 삭제 함수
	function removeFromCart(index) {
		cart.splice(index, 1); // 해당 제품 삭제
		localStorage.setItem('cart', JSON.stringify(cart)); // 장바구니 업데이트
		renderCartItems(); // 장바구니 다시 렌더링
		updateBagIcon(); // 아이콘 상태 업데이트
		calculateTotal(); // 삭제 후 총 금액 다시 계산
	}

	// 수량 변경 처리 함수
	function handleQuantityChange(event) {
		const index = event.target.getAttribute('data-index'); // 고유한 index 값으로 제품을 식별
		const newQuantity = parseInt(event.target.value) || 1; // 수량이 1 미만일 경우 1로 설정

		cart[index].quantity = newQuantity; // 장바구니 데이터 업데이트
		localStorage.setItem('cart', JSON.stringify(cart)); // 로컬 스토리지 업데이트
		calculateTotal(); // 변경된 수량을 반영한 총 금액 계산
	}

	// 개별 제품 렌더링 함수
	function renderCartItem(item, index) {
		const categoryProducts = productData[item.category];
		const product = categoryProducts.find(prod => prod.name === item.product);

		if (!product) {
			console.error(`제품을 찾을 수 없습니다: ${item.product}`);
			return '';
		}

		const pricePerItem = item.price || 10000; // 제품 가격이 없으면 기본값 10000 사용
		const totalPrice = pricePerItem * item.quantity;

		return `
            <div class="card card-style">
                <div class="content mb-0">
                    <div class="d-flex mb-4">
                        <div>
                            <img src="/front/images/pictures/9s.jpg" class="rounded-m shadow-xl" width="130">
                        </div>
                        <div class="ms-3 p-relative">
                            <h5 class="font-600 mb-0">${item.product}</h5>
                            <h1 class="pt-0">${totalPrice.toLocaleString()}원</h1>
                            <a href="#" class="cart-remove color-theme opacity-50 font-12" data-index="${index}"> 
                                <i class="fa fa-times color-red-dark pe-2 pt-3"></i>삭제</a>
                        </div>
                    </div>
                    <div class="row mb-0">
                        <div class="col-4">
                            <div class="input-style input-style-always-active has-borders no-icon">
                                <label class="color-blue-dark">선택사이즈</label>
                                <select>
                                    ${product.sizes.map(size => `<option ${size === item.size ? 'selected' : ''}>${size}</option>`).join('')}
                                </select>
                            </div>
                        </div>
                        <div class="col-4">
                            <div class="input-style input-style-always-active has-borders no-icon">
                                <label class="color-blue-dark">선택색상</label>
                                <select>
                                    ${product.colors.map(color => `<option ${color === item.color ? 'selected' : ''}>${color}</option>`).join('')}
                                </select>
                            </div>
                        </div>
                        <div class="col-4">
                            <div class="input-style input-style-always-active has-borders no-icon">
                                <input required type="number" class="quantity-input form-control focus-color focus-blue"
                                    data-index="${index}" data-price="${pricePerItem}" value="${item.quantity}" min="1">
                                <label class="color-blue-dark">수량</label>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;
	}

	// 전체 장바구니 렌더링 함수
	function renderCartItems() {
		// productContainer와 cartContainer가 존재하는지 확인
		if (!productContainer || !cartContainer) {
			console.error('장바구니 관련 요소를 찾을 수 없습니다.');
			return;
		}

		productContainer.innerHTML = ''; // 초기화

		if (cart.length === 0) {
			// 장바구니 비었을 때 메시지
			cartContainer.innerHTML = `
                <div class="card card-style">
                    <div class="content mb-2">
                        <h3>제품이 없습니다.</h3>
                        <p class="mb-0">장바구니에 등록된 제품이 없습니다.</p>
                    </div>
                </div>`;
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

		calculateTotal(); // 제품 렌더링 후 금액 계산
	}

	// 포인트 적용 버튼
	const applyButton = document.getElementById('apply-button');
	if (applyButton) {  // 버튼이 존재하는지 확인
		applyButton.addEventListener('click', (event) => {
			event.preventDefault();
			calculateTotal();
		});
	}

	// 포인트 취소 버튼
	const cancelButton = document.getElementById('cancel-button');
	if (cancelButton) {  // 버튼이 존재하는지 확인
		cancelButton.addEventListener('click', (event) => {
			event.preventDefault();
			document.getElementById('point-input').value = ''; // 포인트 입력 초기화
			calculateTotal();
		});
	}


	// 초기 로드 시 렌더링 및 아이콘 업데이트
	renderCartItems();
	updateBagIcon();
});
