window.addEventListener('DOMContentLoaded', () => {

	const bagIcon = document.getElementById('bag-icon');
	const cartAmount = document.getElementById('cart-product-amount');
	const cartContainer = document.getElementById('cart-container');
	const productContainer = document.getElementById('product-container');

	function fetchLocalizedOption(optionJson) {
		return fetch('/api/v1/translate', {
			method: 'POST',
			headers: {
				'Content-Type': 'application/json'
			},
			body: JSON.stringify(optionJson)
		})
			.then(res => res.json())
			.catch(err => {
				console.error('옵션 한글화 실패', err);
				return optionJson;
			});
	}

	async function updateCartAmount() {
		const cart = await fetchCartFromServer();
		if (cartAmount) {
			const uniqueProductCount = cart.length;
			cartAmount.textContent = uniqueProductCount;

			if (uniqueProductCount > 0) {
				cartAmount.style.display = 'flex';
			} else {
				cartAmount.style.display = 'none';
			}
		}
	}

	async function updateBagIcon() {
		const cart = await fetchCartFromServer();
		if (cart.length && bagIcon) {
			bagIcon.classList.add('active');
		} else if (bagIcon) {
			bagIcon.classList.remove('active');
		}
		await updateCartAmount();
	}

	function handleQuantityChange(event) {
		const index = event.target.getAttribute('data-index');
		const newQuantity = parseInt(event.target.value) || 1;

		const cart = JSON.parse(localStorage.getItem('cart')) || [];
		const item = cart.find(item => String(item.id) === index);
		if (item) {
			item.quantity = newQuantity;
			localStorage.setItem('cart', JSON.stringify(cart));
		}
	}

	async function fetchCartFromServer() {
		try {
			const res = await fetch('/api/v2/cartSelect');
			if (!res.ok) {
				console.error('장바구니 조회 실패', res.status, res.statusText);
				return [];
			}
			return await res.json();
		} catch (err) {
			console.error('장바구니 조회 중 오류 발생:', err);
			return [];
		}
	}

	async function deleteCartItem(cartId) {
		if (!confirm('장바구니에서 삭제하시겠습니까?')) return;
		await fetch(`/api/v2/cartDelete/${cartId}`);
		await renderCartItems();
	}

	function renderCartItem(item) {
		const pricePerItem = item.price || 10000;
		const totalPrice = pricePerItem * item.quantity;
		const option = item.localizedOption || {};

		const productName = option["제품명"] || option["제품"] || '제품명 없음';

		let itemHTML = `
		<div class="card card-style">
			<div class="content mb-0">
				<div class="form-check icon-check">
					<input class="form-check-input product-checkbox" id="product-checkbox-${item.id}" type="checkbox" data-id="${item.id}" checked>
					<label class="form-check-label" for="product-checkbox-${item.id}">선택</label>
					<i class="icon-check-1 fa fa-square color-gray-dark font-16"></i>
					<i class="icon-check-2 fa fa-check-square font-16 color-highlight"></i>
				</div>
				<div class="d-flex mb-4 preview-list">
					<div style="width: 50%;" class="preview-list">`;

		if (item.images && item.images.length > 0) {
			item.images.forEach(image => {
				itemHTML += `
						<div class="preview-item">
							<img src="${image.imageUrl}" width="100">
						</div>`;
			});
		} else {
			itemHTML += `
						<div class="preview-item">
							<img src="/front/images/pictures/9s.jpg" width="130">
						</div>`;
		}

		itemHTML += `</div>
					<div class="ms-3 p-relative">
						<h5 class="font-600 mb-0">${productName}</h5>
						<h1 class="pt-0">${totalPrice.toLocaleString()}원</h1>
						<a href="#" class="cart-remove color-theme opacity-50 font-12" data-id="${item.id}">
							<i class="fa fa-times color-red-dark pe-2 pt-3"></i>삭제</a>
					</div>
				</div>
				<div class="row mb-0">
					<div class="col-3">
						<div class="input-style input-style-always-active has-borders no-icon">
							<input required type="number" class="quantity-input form-control focus-color focus-blue"
								data-index="${item.id}" data-price="${pricePerItem}" value="${item.quantity}" min="1">
							<label class="color-blue-dark">수량</label>
						</div>
					</div>`;

		for (const [key, value] of Object.entries(option)) {
			if (['제품', '제품명', 'code'].includes(key)) continue;
			itemHTML += `
					<div class="col-3">
						<div class="input-style input-style-always-active has-borders no-icon">
							<label class="color-blue-dark">${key}</label>
							<input type="text" value="${value}" readonly>
						</div>
					</div>`;
		}

		itemHTML += `</div></div></div>`;
		return itemHTML;
	}

	async function renderCartItems() {
		if (!productContainer || !cartContainer) return;

		showPreloader();
		const cartList = await fetchCartFromServer();
		productContainer.innerHTML = '';

		if (cartList.length === 0) {
			cartContainer.innerHTML = `
				<div class="card card-style">
					<div class="content mb-2">
						<h3>제품이 없습니다.</h3>
						<p class="mb-0">장바구니에 등록된 제품이 없습니다.</p>
					</div>
				</div>`;
			await updateBagIcon();
			hidePreloader();
			return;
		}

		for (let i = 0; i < cartList.length; i++) {
			const item = cartList[i];
			const parsedOption = JSON.parse(item.localizedOptionJson || '{}');

			// ✅ 규격 제품 여부에 따른 처리
			if (item.standard === true) {
				item.localizedOption = parsedOption;
			} else {
				item.localizedOption = await fetchLocalizedOption(parsedOption);
			}

			productContainer.innerHTML += renderCartItem(item);
		}

		document.querySelectorAll('.cart-remove').forEach(btn => {
			btn.addEventListener('click', (e) => {
				e.preventDefault();
				const id = btn.getAttribute('data-id');
				deleteCartItem(id);
			});
		});

		document.querySelectorAll('.quantity-input').forEach((input) => {
			input.addEventListener('input', handleQuantityChange);
		});

		await updateBagIcon();
		hidePreloader();
	}

	function showPreloader() {
		const preloader = document.getElementById("preloader");
		if (preloader) preloader.classList.remove("preloader-hide");
	}

	function hidePreloader() {
		const preloader = document.getElementById("preloader");
		if (preloader) preloader.classList.add("preloader-hide");
	}

	const goToOrderBtn = document.getElementById('go-to-order');
	if (goToOrderBtn) {
		goToOrderBtn.addEventListener('click', () => {
			const checkedCheckboxes = [...document.querySelectorAll('.product-checkbox')].filter(cb => cb.checked);
			if (checkedCheckboxes.length === 0) {
				alert('발주할 제품을 한 개 이상 선택해주세요.');
				return;
			}
			if (!confirm('선택된 제품으로 발주하시겠습니까?')) return;

			const orderList = checkedCheckboxes.map(cb => {
				const cartId = cb.dataset.id;
				const quantityInput = document.querySelector(`.quantity-input[data-index="${cartId}"]`);
				const quantity = quantityInput ? parseInt(quantityInput.value) || 1 : 1;
				return { cartId: Number(cartId), quantity };
			});

			const form = document.createElement('form');
			form.method = 'POST';
			form.action = '/orderConfirm';
			form.style.display = 'none';

			const ordersInput = document.createElement('input');
			ordersInput.type = 'hidden';
			ordersInput.name = 'ordersJson';
			ordersInput.value = JSON.stringify(orderList);
			form.appendChild(ordersInput);

			const fromInput = document.createElement('input');
			fromInput.type = 'hidden';
			fromInput.name = 'from';
			fromInput.value = 'cart';
			form.appendChild(fromInput);

			document.body.appendChild(form);
			form.submit();
		});
	}

	// 초기 실행
	if (productContainer && cartContainer) {
		renderCartItems();
	}

	updateBagIcon();

	window.updateCartAmount = updateCartAmount;
	window.updateBagIcon = updateBagIcon;
	window.renderCartItems = renderCartItems;
});
