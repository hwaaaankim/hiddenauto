AOS.init({
	duration: 500,
	easing: 'ease-in-out',
	once: true
});

let selectedOptions = {
	category: '',
	product: '',
	size: '',
	color: '',
	option: '',
	led: '',
	door: '',
	handle: ''
};

// 선택 항목 구성
const productSelectionSteps = {
	default: ['category', 'product', 'size', 'color', 'option', 'led', 'door', 'handle']
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
		addChatMessage('선택이 완료되었습니다. 다음 단계를 진행해주세요.', 'question');
	}

	// 현재 및 이전 단계의 버튼 비활성화
	toggleButtons(`${type}-options`, true);
}

// 버튼 비활성화/활성화 함수
function toggleButtons(containerId, disable = true) {
	const buttons = document.querySelectorAll(`#${containerId} button.option`);
	if (buttons.length > 0) {
		buttons.forEach(button => {
			button.disabled = disable;
		});
	}
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
	if (options.length) {
		addChatMessage(`${type}을(를) 선택하세요:`, 'question', options, `${type}-options`, type); // 각 단계에 맞는 id와 type을 부여
	} else {
		addChatMessage(`${type}을(를) 선택할 옵션이 없습니다.`, 'question');
	}
}

// 각 단계별 옵션을 반환하는 함수
function getOptionsForStep(type) {
	switch (type) {
		case 'category':
			return [...Array(6).keys()].map(i => ({
				label: `대분류0${i + 1}`, action: () => handleSelection('category', `대분류0${i + 1}`)
			}));
		case 'product':
			return [...Array(6).keys()].map(i => ({
				label: `PRODUCT0${i + 1}`, action: () => handleSelection('product', `PRODUCT0${i + 1}`)
			}));
		case 'size':
			return [...Array(6).keys()].map(i => ({
				label: `SIZE0${i + 1}`, action: () => handleSelection('size', `SIZE0${i + 1}`)
			}));
		case 'color':
			return ['Red', 'Blue', 'Green', 'Yellow', 'Black', 'White'].map(color => ({
				label: color, action: () => handleSelection('color', color)
			}));
		case 'option':
			return [...Array(6).keys()].map(i => ({
				label: `옵션0${i + 1}`, action: () => handleSelection('option', `옵션0${i + 1}`)
			}));
		case 'led':
			return ['LED 추가', 'LED 미추가'].map(label => ({
				label, action: () => handleSelection('led', label)
			}));
		case 'door':
			return ['문 추가', '문 미추가'].map(label => ({
				label, action: () => handleSelection('door', label)
			}));
		case 'handle':
			return ['손잡이 추가', '손잡이 미추가'].map(label => ({
				label, action: () => handleSelection('handle', label)
			}));
		default:
			return [];
	}
}

// 메시지와 옵션을 추가하는 함수 (버튼 포함)
function addChatMessage(message, type, options = null, containerId = '', step = '') {
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
	document.getElementById('chat-container').appendChild(chatBoxItem);
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
	if (nextOptionsBottom > containerBottom - 100) {
		chatContainer.scrollTo({
			top: chatContainer.scrollHeight,
			behavior: 'smooth'
		});
	}
}

// 첫 질문 추가
addChatMessage('카테고리를 선택하세요:', 'question', getOptionsForStep('category'), 'category-options');
