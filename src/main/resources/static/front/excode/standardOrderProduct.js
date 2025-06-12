import { productData } from './data.js';

AOS.init({
	duration: 500,
	easing: 'ease-in-out',
	once: true
});

// 상수로 분리
const STEP_KEYS = ['category', 'product', 'size', 'color'];  // 필요 시 확장 가능

// 선택된 옵션 객체 초기화
let selectedOptions = Object.fromEntries(STEP_KEYS.map(step => [step, '']));

// 선택 항목 구성
const productSelectionSteps = {
	default: STEP_KEYS
};

// 선택된 아이템을 업데이트하는 함수
function updateSelectedItems() {
	const selectedPath = Object.values(selectedOptions).filter(Boolean).join(' - ');

	// 선택한 항목이 없는 경우 "None" 대신 커스텀 메시지 설정 가능
	const displayText = selectedPath || '선택한 항목이 없습니다';

	// 선택한 항목을 업데이트
	const selectedPathElement = document.getElementById('selected-path');
	selectedPathElement.innerText = displayText;

	// 선택 항목이 업데이트될 때 시각적으로 강조 (예: 색상 변화)
	selectedPathElement.style.transition = 'color 0.3s ease'; // 부드러운 전환 효과
	selectedPathElement.style.color = displayText !== '선택한 항목이 없습니다' ? '#4CAF50' : '#FF5722'; // 선택 시 녹색, 없을 시 빨간색
}

// 선택된 단계를 처리하는 함수
function handleSelection(type, value) {
	selectedOptions[type] = value;
	updateSelectedItems();
	addChatMessage(`'${value}'을(를) 선택하셨습니다.`, 'answer', null, `${type}-options`, type);  // type을 넘겨서 각 단계마다 구분

	// 다음 선택 옵션 제공
	const nextStep = getNextStep(type);
	if (nextStep) {
		addSelectionStep(nextStep);
	} else {
		// 최종 단계인 색상 선택 후 바로 최종 메시지 출력
		addFinalStepMessage();
	}

	// 현재 및 이전 단계의 버튼 비활성화
	toggleButtons(`${type}-options`, true);
}

// 버튼 비활성화/활성화 함수
function toggleButtons(containerId, disable = true) {
	const buttons = document.querySelectorAll(`#${containerId} button.option`);
	buttons.forEach(button => button.disabled = disable);
}

// 다음 선택 단계를 반환하는 함수
function getNextStep(currentStep) {
	const steps = productSelectionSteps['default'];
	const currentIndex = steps.indexOf(currentStep);
	return currentIndex !== -1 && currentIndex < steps.length - 1 ? steps[currentIndex + 1] : null;
}

// 선택 항목을 추가하는 함수 (각 단계에 맞는 id 부여)
function addSelectionStep(type) {
	const options = getOptionsForStep(type);
	const message = options.length ? `${type}을(를) 선택하세요:` : `${type}을(를) 선택할 옵션이 없습니다.`;
	addChatMessage(message, 'question', options, `${type}-options`, type);
}

function validateQuantityInput(event) {
    const input = event.target;
    // 숫자가 아닌 값이 있으면 제거
    input.value = input.value.replace(/[^0-9]/g, '');
}

// 선택 항목을 추가하는 함수 (각 단계에 맞는 id 부여)
function getOptionsForStep(type) {
	switch (type) {
		case 'category':
			return Object.keys(productData).map(category => ({
				label: category, action: () => handleSelection('category', category)
			}));
		case 'product':
			const selectedCategory = selectedOptions['category'];
			if (selectedCategory && productData[selectedCategory]) {
				return productData[selectedCategory].map(product => ({
					label: product.name, action: () => handleSelection('product', product.name)
				}));
			} else {
				return [];
			}
		case 'size':
			const selectedCategoryForSize = selectedOptions['category'];
			const selectedProductForSize = selectedOptions['product'];
			if (selectedCategoryForSize && selectedProductForSize) {
				const product = productData[selectedCategoryForSize].find(p => p.name === selectedProductForSize);
				if (product) {
					return product.sizes.map(size => ({
						label: size, action: () => handleSelection('size', size)
					}));
				}
			}
			return [];
		case 'color':
			const selectedCategoryForColor = selectedOptions['category'];
			const selectedProductForColor = selectedOptions['product'];
			if (selectedCategoryForColor && selectedProductForColor) {
				const product = productData[selectedCategoryForColor].find(p => p.name === selectedProductForColor);
				if (product) {
					return product.colors.map(color => ({
						label: color, action: () => handleSelection('color', color)
					}));
				}
			}
			return [];
		default:
			return [];
	}
}

// 버튼 생성 함수 (중복 제거)
function createButton(buttonText, buttonClass, onClickHandler) {
	const button = document.createElement('button');
	button.classList.add('final-button', buttonClass);
	button.innerText = buttonText;
	button.onclick = onClickHandler;
	return button;
}

// 애니메이션 처리 함수로 통합
function animateElement(element, action, duration = 300, delay = 1000) {
	if (action === 'fadeIn') {
		element.style.display = 'block'; // 표시
		setTimeout(() => {
			element.style.opacity = '1';  // 투명도 1로 설정 (보이게)
		}, 10); // 짧은 딜레이 후에 transition 시작
	} else if (action === 'fadeOut') {
		setTimeout(() => {
			element.style.opacity = '0'; // 투명도 0으로 설정 (숨기기 시작)
			setTimeout(() => {
				element.style.display = 'none'; // 완전히 숨김
			}, duration); // fadeOut 지속 시간 후에 display를 'none'으로
		}, delay); // fadeOut이 시작되기 전에 대기 시간
	}
}


// 최종 메시지와 수량 입력 및 버튼을 추가하는 함수
function addFinalStepMessage() {
	const chatBoxItem = document.createElement('div');
	chatBoxItem.classList.add('chat-box-item', 'final-step');  // final 단계임을 표시하는 클래스 추가

	const newMessage = document.createElement('div');
	newMessage.classList.add('chat-message', 'chat-question', 'final-step');  // final 단계임을 표시하는 선택자 추가

	// "선택이 완료되었습니다." 메시지
	const textNode = document.createElement('span');
	textNode.innerText = '선택이 완료되었습니다.';
	newMessage.appendChild(textNode);

	// 오른쪽 영역 생성 (수량 입력 필드 및 버튼들 포함)
	const rightSide = document.createElement('div');
	rightSide.classList.add('right-side');

	// 수량 입력 필드 및 레이블 추가
	const quantityContainer = document.createElement('div');
	quantityContainer.classList.add('quantity-input-container');

	const quantityLabel = document.createElement('label');
	quantityLabel.setAttribute('for', 'quantity');
	quantityLabel.innerText = '수량:';
	quantityContainer.appendChild(quantityLabel);

	const quantityInput = document.createElement('input');
	quantityInput.setAttribute('type', 'number');
	quantityInput.setAttribute('id', 'quantity');
	quantityInput.classList.add('quantity-input');
	quantityInput.setAttribute('min', '1');
	quantityInput.setAttribute('value', '1');
	// oninput 이벤트 추가
	quantityInput.oninput = validateQuantityInput;  // validateQuantityInput 함수를 oninput으로 연결
	quantityContainer.appendChild(quantityInput);

	rightSide.appendChild(quantityContainer);

	// 버튼 컨테이너 생성
	const buttonContainer = document.createElement('div');
	buttonContainer.classList.add('button-container');

	// "발주넣기"와 "장바구니" 버튼 추가
	buttonContainer.appendChild(createButton('발주넣기', 'order-button', () => handleOrder(quantityInput.value)));
	buttonContainer.appendChild(createButton('장바구니', 'cart-button', () => handleCart(quantityInput.value)));

	rightSide.appendChild(buttonContainer);

	newMessage.appendChild(rightSide);

	// 최종 메시지와 버튼을 채팅 박스에 추가
	chatBoxItem.appendChild(newMessage);
	document.getElementById('chat-container').appendChild(chatBoxItem);
	document.getElementById('chat-container').scrollTop = document.getElementById('chat-container').scrollHeight;
	AOS.refresh();
}

// 발주넣기 처리 함수
function handleOrder(quantity) {
	// Confirm 창 띄우기
	const confirmOrder = confirm('발주를 진행하시겠습니까?');
	
	if (confirmOrder) {
		console.log(`발주가 완료되었습니다. 수량: ${quantity}`);
		// 확인을 누르면 /orderConfirm으로 이동
		window.location.href = '/orderConfirm';
	} else {
		console.log('발주가 취소되었습니다.');
	}
}

// 선택 상태를 초기화하고 최초 화면으로 돌아가는 함수
function resetSelection() {
	// 선택된 항목 초기화
	selectedOptions = Object.fromEntries(STEP_KEYS.map(step => [step, '']));

	// 선택 항목을 표시하는 'selected-path'를 초기화
	const selectedPathElement = document.getElementById('selected-path');
	selectedPathElement.innerText = '선택된 항목이 없습니다.'; // 초기 상태로 리셋

	// chat-box 내부의 대화 내용만 초기화
	const chatBox = document.getElementById('chat-box');
	chatBox.innerHTML = '';  // 대화 내용 모두 제거

	// final-step도 지우기
	const finalStepElements = document.querySelectorAll('.final-step');
	finalStepElements.forEach(finalElement => {
		finalElement.remove();  // final-step을 포함한 최종 메시지 삭제
	});

	// 첫 질문 다시 추가 (최초 카테고리 선택 화면으로 돌아감)
	addChatMessage('카테고리를 선택하세요:', 'question', getOptionsForStep('category'), 'category-options');
}

// 장바구니 상태를 유지하기 위한 cart 변수
let cart = JSON.parse(localStorage.getItem('cart')) || [];

// 장바구니 처리 함수 (수정)
function handleCart(quantity) {
    // confirm 창을 띄워 유저에게 물어봄
    const confirmCart = confirm('장바구니에 추가하겠습니까?');

    if (confirmCart) {
        console.log(`장바구니에 추가되었습니다. 수량: ${quantity}`);

        // 현재 선택한 제품 정보
        const selectedProduct = {
            category: selectedOptions.category,
            product: selectedOptions.product,
            size: selectedOptions.size,
            color: selectedOptions.color,
            quantity: parseInt(quantity)
        };

        // 장바구니에 동일한 제품이 있는지 확인
        const existingProductIndex = cart.findIndex(item =>
            item.category === selectedProduct.category &&
            item.product === selectedProduct.product &&
            item.size === selectedProduct.size &&
            item.color === selectedProduct.color
        );

        if (existingProductIndex !== -1) {
            // 동일한 제품이 있으면 수량을 업데이트
            cart[existingProductIndex].quantity += selectedProduct.quantity;
            console.log(`기존 제품 수량을 ${cart[existingProductIndex].quantity}개로 업데이트 했습니다.`);
        } else {
            // 장바구니에 선택된 제품 추가
            cart.push(selectedProduct);
            console.log(`새 제품을 장바구니에 추가했습니다: ${selectedProduct.product}`);
        }

        // 장바구니에 담긴 제품의 종류(아이템 수)를 콘솔에 출력
        console.log(`현재 장바구니에 담긴 제품 종류: ${cart.length}종`);

        // localStorage에 장바구니 상태 저장
        localStorage.setItem('cart', JSON.stringify(cart));

        // 장바구니 알림 박스를 찾음
        const cartNotification = document.getElementById('cart-notification');
        if (cartNotification) {
            // fadeIn 애니메이션 적용 (바로 보이게)
            animateElement(cartNotification, 'fadeIn', 300, 0);
            // 1초 후 fadeOut 애니메이션 적용 (서서히 사라짐)
            animateElement(cartNotification, 'fadeOut', 300, 1000);
        }

        // 장바구니가 비어 있는지 확인하고 비어있으면 active 클래스 추가
        const bagIcon = document.getElementById('bag-icon');
        if (cart.length && bagIcon) {
			console.log('gdd');
            bagIcon.classList.add('active');
        }else{
			console.log('kkk');
		}

        // 0.3초 후 선택 초기화 (장바구니 추가 후 약간의 딜레이 후 초기화)
        setTimeout(() => {
            resetSelection();  // 선택 영역 초기화
        }, 300);
    } else {
        console.log('장바구니 추가가 취소되었습니다.');
    }
}


// 메시지와 옵션을 추가하는 함수 (버튼 포함)
function addChatMessage(message, type, options = null, containerId = '', step = '') {
	const chatBox = document.getElementById('chat-box'); // chat-container 대신 chat-box로 변경
	const chatBoxItem = document.createElement('div');
	chatBoxItem.classList.add('chat-box-item');
	const newMessage = document.createElement('div');
	newMessage.classList.add('chat-message', `chat-${type}`);

	// 'answer' 타입의 메시지에만 버튼 추가
	if (type === 'answer') {
		chatBoxItem.classList.add('right-box');

		const backButton = document.createElement('button');
		backButton.classList.add('back-button');
		backButton.style.marginRight = '10px';
		backButton.innerHTML = '<i class="fa-regular fa-circle-up" aria-hidden="true"></i>';
		backButton.onclick = () => resetStepsFrom(step);
		newMessage.appendChild(backButton);
	}

	const textNode = document.createElement('span');
	textNode.innerText = message;
	newMessage.appendChild(textNode);
	newMessage.setAttribute('data-aos', type === 'question' ? 'fade-right' : 'fade-left');
	chatBoxItem.appendChild(newMessage);
	chatBox.appendChild(chatBoxItem); // chat-container 대신 chat-box에 추가
	document.getElementById('chat-container').scrollTop = document.getElementById('chat-container').scrollHeight;
	AOS.refresh();

	if (options && containerId) {
		setTimeout(() => {
			const optionsContainer = document.createElement('div');
			optionsContainer.setAttribute('id', containerId);
			optionsContainer.classList.add('chat-options');

			options.forEach(option => {
				const button = document.createElement('button');
				button.classList.add('option');
				button.innerText = option.label;
				button.onclick = option.action;
				optionsContainer.appendChild(button);
			});

			chatBoxItem.appendChild(optionsContainer);
			AOS.refresh();
			scrollIfNeeded(optionsContainer);
		}, 500);
	}
}


// 요소에 fadeOut 효과를 적용하는 함수
function fadeOut(element, duration, callback) {
	element.style.transition = `opacity ${duration}ms`;
	element.style.opacity = 0;

	setTimeout(() => {
		element.style.display = 'none';
		if (callback) callback(); // 애니메이션 후 콜백 실행 (요소 제거 등)
	}, duration);
}

// 선택 초기화 및 버튼 활성화 처리
function resetStepsFrom(step) {
	const stepIndex = productSelectionSteps['default'].indexOf(step);
	const stepsToReset = productSelectionSteps['default'].slice(stepIndex + 1);

	// 현재 단계의 답변만 제거 (옵션 버튼은 남김)
	const answerElements = document.querySelectorAll(`.chat-box-item .chat-answer`);
	answerElements.forEach(answerElement => {
		if (answerElement.innerText.includes(selectedOptions[step])) {
			fadeOut(answerElement.closest('.chat-box-item'), 300, () => {
				answerElement.closest('.chat-box-item').remove(); // 현재 단계의 답변만 제거
			});
		}
	});

	// 현재 단계의 선택값을 제거
	selectedOptions[step] = '';

	// 이후 단계의 선택 초기화
	stepsToReset.forEach(nextStep => {
		removeElements(nextStep); // 이후 단계의 질문, 답변, 옵션 제거
		selectedOptions[nextStep] = '';  // 이후 단계 선택 항목 초기화
	});

	// 최종 완료 메시지 제거
	const finalStepElements = document.querySelectorAll('.final-step');
	finalStepElements.forEach(finalElement => {
		fadeOut(finalElement, 300, () => {
			finalElement.remove();
		});
	});

	// 현재 단계의 버튼 활성화
	toggleButtons(`${step}-options`, false);

	// 선택된 항목 업데이트
	updateSelectedItems();
	console.log(`${step} 이후의 모든 단계를 초기화했습니다.`);
}

// removeElements 함수 (옵션 및 질문을 포함한 fadeOut 적용)
function removeElements(step) {
	const questionElements = document.querySelectorAll(`.chat-box-item .chat-question`);
	const answerElements = document.querySelectorAll(`.chat-box-item .chat-answer`);
	const optionContainer = document.getElementById(`${step}-options`);

	// 질문 제거: 해당 단계 이후의 질문만 제거
	questionElements.forEach(questionElement => {
		if (questionElement.closest('.chat-box-item').innerText.includes(step)) {
			fadeOut(questionElement.closest('.chat-box-item'), 300, () => {
				questionElement.closest('.chat-box-item').remove();
			});
		}
	});

	// 답변 제거: 해당 단계 이후의 답변만 제거
	answerElements.forEach(answerElement => {
		const selectedValue = selectedOptions[step];
		if (selectedValue && answerElement.innerText.includes(selectedValue)) {
			fadeOut(answerElement.closest('.chat-box-item'), 300, () => {
				answerElement.closest('.chat-box-item').remove();
			});
		}
	});

	// 옵션 제거
	if (optionContainer) {
		fadeOut(optionContainer.closest('.chat-box-item'), 300, () => {
			optionContainer.closest('.chat-box-item').remove();
		});
	}
}

// 스크롤이 필요한지 체크하고, 필요하면 스크롤하는 함수
function scrollIfNeeded(nextOptionsContainer) {
	const chatContainer = document.getElementById('chat-container');
	const nextOptionsBottom = nextOptionsContainer.getBoundingClientRect().bottom;
	const containerBottom = chatContainer.getBoundingClientRect().bottom;

	// 만약 다음 선택 옵션이 화면 아래로 사라지면 스크롤
	if (nextOptionsBottom > containerBottom - 200) {
		chatContainer.scrollTo({
			top: chatContainer.scrollHeight,
			behavior: 'smooth'
		});
	}
}

// 첫 질문 추가
addChatMessage('카테고리를 선택하세요:', 'question', getOptionsForStep('category'), 'category-options');
