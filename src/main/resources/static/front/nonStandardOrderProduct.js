const initialQuestion = {
	step: 'category',
	options: ['거울', '상부장', '하부장', '플랩장', '슬라이드장'],
	next: (selectedCategory) => {
		switch (selectedCategory) {
			case '거울': return 'mirror';
			case '상부장': return 'top';
			case '하부장': return 'low';
			case '플랩장': return 'flap';
			case '슬라이드장': return 'slide';
			default: return null;
		}
	}
};

const productFlowSteps = {
	mirror: [
		{ step: 'product', options: ['Product 1', 'Product 2'], next: 'size' },
		{ step: 'size', options: ['Small', 'Medium', 'Large'], next: 'color' },
		{ step: 'color', options: ['Red', 'Blue'], next: 'led' },
		{ step: 'led', options: ['ADD', 'NOT ADD'], next: (selectedOption) => selectedOption === 'ADD' ? 'ledcolor' : 'final' },
		{ step: 'ledcolor', options: ['Warm', 'Cool'], next: 'final' }
	],
	top: [
		{ step: 'product', options: ['Product A', 'Product B'], next: 'size' },
		{ step: 'size', options: ['Small', 'Medium', 'Large'], next: 'color' },
		{ step: 'color', options: ['Red', 'Blue'], next: 'door' },
		{ step: 'door', options: ['add', 'notadd'], next: (selectedOption) => selectedOption === 'add' ? 'numberofdoor' : 'handle' },
		{ step: 'numberofdoor', options: ['1', '2', '3'], next: 'handle' },
		{ step: 'handle', options: ['add', 'notadd'], next: (selectedOption) => selectedOption === 'add' ? 'numberofhandle' : 'final' },
		{ step: 'numberofhandle', options: ['1', '2', '3'], next: 'final' }
	],
	low: [
		{ step: 'product', options: ['Product A', 'Product B'], next: 'size' },
		{ step: 'size', options: ['Small', 'Medium', 'Large'], next: 'color' },
		{ step: 'color', options: ['Red', 'Blue'], next: 'door' },
		{ step: 'door', options: ['add', 'notadd'], next: (selectedOption) => selectedOption === 'add' ? 'numberofdoor' : 'handle' },
		{ step: 'numberofdoor', options: ['1', '2', '3'], next: 'handle' },
		{ step: 'handle', options: ['add', 'notadd'], next: (selectedOption) => selectedOption === 'add' ? 'numberofhandle' : 'final' },
		{ step: 'numberofhandle', options: ['1', '2', '3'], next: 'final' }
	],
	flap: [
		{ step: 'product', options: ['Product A', 'Product B'], next: 'size' },
		{ step: 'size', options: ['Small', 'Medium', 'Large'], next: 'color' },
		{ step: 'color', options: ['Red', 'Blue'], next: 'door' },
		{ step: 'door', options: ['add', 'notadd'], next: (selectedOption) => selectedOption === 'add' ? 'numberofdoor' : 'handle' },
		{ step: 'numberofdoor', options: ['1', '2', '3'], next: 'handle' },
		{ step: 'handle', options: ['add', 'notadd'], next: (selectedOption) => selectedOption === 'add' ? 'numberofhandle' : 'final' },
		{ step: 'numberofhandle', options: ['1', '2', '3'], next: 'final' }
	],
	slide: [
		{ step: 'product', options: ['Product A', 'Product B'], next: 'size' },
		{ step: 'size', options: ['Small', 'Medium', 'Large'], next: 'color' },
		{ step: 'color', options: ['Red', 'Blue'], next: 'door' },
		{ step: 'door', options: ['add', 'notadd'], next: (selectedOption) => selectedOption === 'add' ? 'numberofdoor' : 'handle' },
		{ step: 'numberofdoor', options: ['1', '2', '3'], next: 'handle' },
		{ step: 'handle', options: ['add', 'notadd'], next: (selectedOption) => selectedOption === 'add' ? 'numberofhandle' : 'final' },
		{ step: 'numberofhandle', options: ['1', '2', '3'], next: 'final' }
	]
};


let currentFlow = ['category']; // 기본적으로 category는 포함됨

AOS.init({
	duration: 500,
	easing: 'ease-in-out',
	once: true
});

const fadeOutElement = (element) => {
	element.style.transition = 'opacity 0.5s ease-out';
	element.style.opacity = '0';
	setTimeout(() => {
		element.remove();
	}, 500); // 0.5초 후 요소 제거
};

// 초기 질문 렌더링 함수
function renderInitialQuestion() {
	const chatBox = document.getElementById('chat-box');
	chatBox.innerHTML = ''; // 이전 내용 초기화

	// 카테고리 wrap 생성
	const categoryWrap = document.createElement('div');
	categoryWrap.id = 'category-wrap';
	categoryWrap.classList.add('non-standard-wrap');
	categoryWrap.setAttribute('data-aos', 'fade-in'); // AOS 애니메이션 적용

	// 질문 추가
	const questionDiv = document.createElement('div');
	questionDiv.id = 'category-question';
	questionDiv.classList.add('non-standard-question');
	questionDiv.innerText = initialQuestion.step + '을(를) 선택하세요:';
	categoryWrap.appendChild(questionDiv);

	// 옵션 버튼 추가
	const optionDiv = document.createElement('div');
	optionDiv.id = 'category-option';
	optionDiv.classList.add('non-standard-option');
	initialQuestion.options.forEach(option => {
		const button = document.createElement('button');
		button.innerText = option;
		button.classList.add('non-standard-btn');
		button.onclick = () => handleCategorySelection(option);
		optionDiv.appendChild(button);
	});
	categoryWrap.appendChild(optionDiv);

	chatBox.appendChild(categoryWrap);
	AOS.refresh(); // AOS 초기화
}

function handleCategorySelection(category) {
	const answerDiv = document.createElement('div');
	answerDiv.id = 'category-answer';
	answerDiv.classList.add('non-standard-answer'); // 디자인 클래스 추가
	answerDiv.setAttribute('data-aos', 'fade-in'); // AOS 애니메이션 적용
	answerDiv.innerText = `${category}을(를) 선택하셨습니다.`;

	// 초기화 버튼 추가
	const resetButton = document.createElement('button');
	resetButton.innerText = '[초기화]';
	resetButton.classList.add('non-standard-btn'); // 디자인 클래스 추가
	resetButton.onclick = () => resetStep('category'); // 초기화 처리
	answerDiv.appendChild(resetButton);

	// Answer div를 category wrap에 추가
	document.getElementById('category-wrap').appendChild(answerDiv);
	AOS.refresh(); // AOS 초기화

	// 선택한 카테고리의 옵션을 비활성화 (덮개 추가)
	const categoryOptionDiv = document.getElementById('category-option');
	categoryOptionDiv.classList.add('disabled-option'); // 덮개 효과 추가

	// 선택한 카테고리에 따른 흐름 가져오기
	const nextStep = initialQuestion.next(category);
	currentFlow.push(productFlowSteps[nextStep][0].step); // 현재 흐름에 카테고리 추가

	// 제품 옵션 업데이트
	if (nextStep) {
		updateProductOptions(nextStep, 0); // 첫 번째 단계로 업데이트
	}
}

function handleDirectInput(inputValue, categoryKey, step) {
	// answer를 동적으로 생성
	let answerDiv = document.getElementById(`${step.step}-answer`);
	if (!answerDiv) {
		answerDiv = document.createElement('div');
		answerDiv.id = `${step.step}-answer`;
		answerDiv.classList.add('non-standard-answer'); // 디자인 클래스 추가
		answerDiv.setAttribute('data-aos', 'fade-in'); // AOS 애니메이션 적용
		document.getElementById(`${step.step}-wrap`).appendChild(answerDiv);
		// fadeIn 애니메이션 처리
		setTimeout(() => {
			answerDiv.style.opacity = '1';
		}, 10);
		// AOS 및 스크롤 처리 추가
		AOS.refresh();
	}

	// 입력한 값으로 답변을 표시
	answerDiv.innerText = `${inputValue}을(를) 입력하셨습니다.`;

	// 초기화 버튼 추가
	const resetButton = document.createElement('button');
	resetButton.innerText = '[초기화]';
	resetButton.classList.add('non-standard-btn'); // 디자인 클래스 추가
	resetButton.onclick = () => resetStep(step.step); // 해당 단계 초기화 처리
	answerDiv.appendChild(resetButton);

	// 현재 단계의 옵션 비활성화
	const optionDiv = document.getElementById(`${step.step}-option`);
	optionDiv.classList.add('disabled-option');

	// 다음 단계로 이동
	const nextStepKey = step.next;
	let nextStepIndex;

	if (typeof nextStepKey === 'function') {
		const nextKey = nextStepKey(inputValue); // 입력한 값으로 다음 단계 결정
		nextStepIndex = productFlowSteps[categoryKey].findIndex(s => s.step === nextKey);
		currentFlow.push(nextKey);
	} else {
		nextStepIndex = productFlowSteps[categoryKey].findIndex(s => s.step === nextStepKey);
		currentFlow.push(nextStepKey);
	}

	// 다음 단계가 있으면 그 단계의 옵션을 업데이트
	if (nextStepIndex >= 0) {
		updateProductOptions(categoryKey, nextStepIndex); // 다음 단계로 진행
	} else {
		// 마지막 단계 처리
		renderAnswer({ step: 'final' }, ''); // final 단계 처리
	}
}


function updateProductOptions(categoryKey, stepIndex) {
	const steps = productFlowSteps[categoryKey];
	const step = steps[stepIndex];

	if (!step) {
		console.error(`No steps found for category: ${categoryKey}`);
		return;
	}

	// 단계별 wrap 생성
	const stepWrap = document.createElement('div');
	stepWrap.id = `${step.step}-wrap`;
	stepWrap.classList.add('non-standard-wrap');
	stepWrap.style.opacity = '0'; // 초기 상태에서 투명하게 설정
	stepWrap.setAttribute('data-aos', 'fade-in'); // AOS 애니메이션 적용

	// 질문 추가
	const questionDiv = document.createElement('div');
	questionDiv.id = `${step.step}-question`;
	questionDiv.classList.add('non-standard-question');
	questionDiv.innerText = `${step.step}을(를) 선택하세요:`;
	stepWrap.appendChild(questionDiv);

	// 옵션 추가 (옵션을 동적으로 처리)
	const optionDiv = document.createElement('div');
	optionDiv.id = `${step.step}-option`;
	optionDiv.classList.add('non-standard-option');

	// size 단계에 대해 입력 필드와 확인 버튼 추가
	if (step.step === 'size') {
		// 사이즈 옵션 추가
		step.options.forEach(option => {
			const button = document.createElement('button');
			button.innerText = option;
			button.classList.add('non-standard-btn');
			button.addEventListener('click', () => handleProductSelection(option, categoryKey, step));
			optionDiv.appendChild(button);
		});

		// 입력 필드 추가
		['width', 'height', 'depth', 'leg-height'].forEach(field => {
			const label = document.createElement('label');
			label.innerHTML = `${field.charAt(0).toUpperCase() + field.slice(1)}: `;
			const input = document.createElement('input');
			input.type = 'number';
			input.id = `${field}-input`;
			input.classList.add('non-standard-input');
			label.appendChild(input);
			optionDiv.appendChild(label);
		});

		// 확인 버튼 추가
		const confirmButton = document.createElement('button');
		confirmButton.innerText = '확인';
		confirmButton.classList.add('non-standard-btn');
		confirmButton.addEventListener('click', () => {
			const width = document.getElementById('width-input').value;
			const height = document.getElementById('height-input').value;
			const depth = document.getElementById('depth-input').value;
			const legHeight = document.getElementById('leg-height-input').value;

			// 필수 입력 필드가 비어있을 경우 경고 표시
			if (!width || !height || !depth || !legHeight) {
				alert('모든 필드를 입력하세요.');
				return;
			}

			const sizeText = `Width: ${width}, Height: ${height}, Depth: ${depth}, Leg Height: ${legHeight}`;
			handleDirectInput(sizeText, categoryKey, step);
		});
		optionDiv.appendChild(confirmButton);

	} else if (step.step === 'numberofdoor' || step.step === 'numberofhandle') {
		// 갯수 선택 및 직접 입력 필드 추가
		step.options.forEach(option => {
			const button = document.createElement('button');
			button.innerText = `${option}개`;
			button.classList.add('non-standard-btn');
			button.addEventListener('click', () => handleProductSelection(option, categoryKey, step));
			optionDiv.appendChild(button);
		});

		const customInputLabel = document.createElement('label');
		customInputLabel.innerHTML = '직접입력: ';
		const customInput = document.createElement('input');
		customInput.type = 'number';
		customInput.id = 'custom-number-input';
		customInput.classList.add('non-standard-input');
		customInputLabel.appendChild(customInput);
		optionDiv.appendChild(customInputLabel);

		const customConfirmButton = document.createElement('button');
		customConfirmButton.innerText = '확인';
		customConfirmButton.classList.add('non-standard-btn');
		customConfirmButton.addEventListener('click', () => {
			const customNumber = document.getElementById('custom-number-input').value;
			if (!customNumber) {
				alert('숫자를 입력하세요.');
				return;
			}
			handleDirectInput(`${customNumber}개`, categoryKey, step); // 숫자 입력 후 확인 처리
		});
		optionDiv.appendChild(customConfirmButton);

	} else {
		// 일반적인 단계 - 옵션 버튼 추가
		step.options.forEach(option => {
			const button = document.createElement('button');
			button.innerText = option;
			button.classList.add('non-standard-btn');
			button.addEventListener('click', () => handleProductSelection(option, categoryKey, step));
			optionDiv.appendChild(button);
		});
	}

	stepWrap.appendChild(optionDiv);
	document.getElementById('chat-box').appendChild(stepWrap);

	// fadeIn 애니메이션 처리
	setTimeout(() => {
		stepWrap.style.opacity = '1';
	}, 10);

	// AOS 및 스크롤 처리 추가
	AOS.refresh();
	scrollIfNeeded(stepWrap);  // 스크롤 처리
}

function renderAnswer(step, product) {
	let answerDiv = document.getElementById(`${step.step}-answer`);

	// final이 아닌 단계의 answer 처리
	if (step.step !== 'final') {
		if (!answerDiv) {
			answerDiv = document.createElement('div');
			answerDiv.id = `${step.step}-answer`;
			answerDiv.classList.add('non-standard-answer');
			answerDiv.style.opacity = '0'; // 초기 상태에서 투명하게 설정
			answerDiv.setAttribute('data-aos', 'fade-in'); // AOS 애니메이션 적용
			document.getElementById(`${step.step}-wrap`).appendChild(answerDiv);
		}
		answerDiv.innerText = `${product}을(를) 선택하셨습니다.`;

		// 초기화 버튼 추가
		const resetButton = document.createElement('button');
		resetButton.innerText = '[초기화]';
		resetButton.classList.add('non-standard-btn');
		resetButton.onclick = () => resetStep(step.step); // 해당 단계 초기화 처리
		answerDiv.appendChild(resetButton);

		// fadeIn 애니메이션 처리
		setTimeout(() => {
			answerDiv.style.opacity = '1';
		}, 10);

		// AOS 및 스크롤 처리 추가
		AOS.refresh();
		scrollIfNeeded(answerDiv);  // 스크롤 처리

	} else {
		// final 단계 처리
		const finalWrap = document.createElement('div');
		finalWrap.id = 'final-wrap';
		finalWrap.classList.add('non-standard-answer');
		finalWrap.style.opacity = '0'; // 초기 상태에서 투명하게 설정

		const finalMessage = document.createElement('span');
		finalMessage.innerText = '선택이 완료되었습니다.';
		finalWrap.appendChild(finalMessage);

		const quantityLabel = document.createElement('label');
		quantityLabel.innerText = '수량: ';
		finalWrap.appendChild(quantityLabel);

		const quantityInput = document.createElement('input');
		quantityInput.type = 'number';
		quantityInput.id = 'final-quantity';
		quantityInput.value = 1; // 기본값 설정
		quantityInput.classList.add('non-standard-input');
		quantityLabel.appendChild(quantityInput);

		const cartButton = document.createElement('button');
		cartButton.id = 'cart-btn';
		cartButton.innerText = '장바구니';
		cartButton.classList.add('non-standard-btn');
		finalWrap.appendChild(cartButton);

		const orderButton = document.createElement('button');
		orderButton.id = 'order-btn';
		orderButton.innerText = '발주하기';
		orderButton.classList.add('non-standard-btn');
		finalWrap.appendChild(orderButton);

		const lastStep = currentFlow[currentFlow.length - 2]; // 마지막 이전 단계
		const lastStepWrap = document.getElementById(`${lastStep}-wrap`);

		if (lastStepWrap) {
			lastStepWrap.insertAdjacentElement('afterend', finalWrap);
		} else {
			answerDiv.appendChild(finalWrap);
		}

		// fadeIn 애니메이션 처리
		setTimeout(() => {
			finalWrap.style.opacity = '1';
		}, 10);

		// AOS 및 스크롤 처리 추가
		AOS.refresh();
		scrollIfNeeded(finalWrap);  // 스크롤 처리
	}
}


function handleProductSelection(product, categoryKey, step) {
	renderAnswer(step, product);

	const optionDiv = document.getElementById(`${step.step}-option`);
	optionDiv.classList.add('disabled-option');

	const nextStepKey = step.next;
	let nextStepIndex;

	if (typeof nextStepKey === 'function') {
		const nextKey = nextStepKey(product);
		nextStepIndex = productFlowSteps[categoryKey].findIndex(s => s.step === nextKey);
		currentFlow.push(nextKey);
	} else {
		nextStepIndex = productFlowSteps[categoryKey].findIndex(s => s.step === nextStepKey);
		currentFlow.push(nextStepKey);
	}

	if (nextStepIndex >= 0) {
		updateProductOptions(categoryKey, nextStepIndex);
	} else {
		renderAnswer({ step: 'final' }, '');
	}
}

function resetStep(step) {
	const answerDiv = document.getElementById(`${step}-answer`);
	if (answerDiv) {
		fadeOutElement(answerDiv); // fadeOut을 통해 부드럽게 제거
	}

	const stepsToDelete = currentFlow.slice(currentFlow.indexOf(step) + 1);
	stepsToDelete.forEach((stepToDelete) => {
		const wrapDiv = document.getElementById(`${stepToDelete}-wrap`);
		if (wrapDiv) fadeOutElement(wrapDiv); // fadeOut 적용
	});

	currentFlow = currentFlow.slice(0, currentFlow.indexOf(step) + 1);

	const optionDiv = document.getElementById(`${step}-option`);
	if (optionDiv) {
		optionDiv.classList.remove('disabled-option');
	}
}

function scrollIfNeeded(nextOptionsContainer) {
	const chatBox = document.getElementById('chat-box');
	const chatContainer = document.getElementById('chat-container');
	const nextOptionsBottom = nextOptionsContainer.getBoundingClientRect().bottom;
	const containerBottom = chatBox.getBoundingClientRect().bottom;

	// 만약 다음 선택 옵션이 화면 아래로 사라지면 스크롤
	if (nextOptionsBottom > containerBottom - 200) {
		// chat-box 스크롤
		chatBox.scrollTo({
			top: chatBox.scrollHeight + 200,
			behavior: 'smooth'
		});

		// chat-container 스크롤
		chatContainer.scrollTo({
			top: chatContainer.scrollHeight,
			behavior: 'smooth'
		});
	}
}



// 페이지가 로드될 때 초기 질문을 렌더링
window.onload = renderInitialQuestion;

