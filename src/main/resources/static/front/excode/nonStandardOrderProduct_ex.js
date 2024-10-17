// data.js에서 productData를 import
import { productData } from './data.js';

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
	led: '',
	door: '',
	handle: '',
	numberofdoor: '',
	numberofhandle: ''
};

// 선택 항목 구성
const productSelectionSteps = {
	default: ['category', 'product', 'size', 'color', 'led', 'door', 'numberofdoor', 'handle', 'numberofhandle', 'final']
};

// 선택된 아이템을 업데이트하는 함수
function updateSelectedItems() {
	const selectedPath = Object.values(selectedOptions).filter(Boolean).join(' - ');

	const displayText = selectedPath || '선택한 항목이 없습니다';
	const selectedPathElement = document.getElementById('selected-path');
	selectedPathElement.innerText = displayText;
	selectedPathElement.style.transition = 'color 0.3s ease';
	selectedPathElement.style.color = displayText !== '선택한 항목이 없습니다' ? '#4CAF50' : '#FF5722';
}

function handleSelection(type, value) {
	selectedOptions[type] = value;
	updateSelectedItems();

	let currentStep = type;
	if (type === 'door' && value === 'add') {
		currentStep = 'numberofdoor';  
	} else if (type === 'handle' && value === 'add') {
		currentStep = 'numberofhandle';  
	}

	addChatMessage(`'${value}'을(를) 선택하셨습니다.`, 'answer', null, `${type}-options`, currentStep);

	if (type === 'door' || type === 'handle') {
		if (value === 'add') {
			addChatMessage('몇 개를 추가할까요?', 'question', [
				{ label: '1개', action: () => handleCustomCountSelection(type, 1) },
				{ label: '2개', action: () => handleCustomCountSelection(type, 2) },
				{ label: '3개', action: () => handleCustomCountSelection(type, 3) },
				{ label: '4개', action: () => handleCustomCountSelection(type, 4) }
			], `${type}-add-options`, currentStep);
		} else {
			const nextStep = getNextStep(type);
			if (nextStep) {
				addSelectionStep(nextStep);
			} else {
				addChatMessage('선택이 완료되었습니다. 다음 단계를 진행해주세요.', 'question');
			}
		}
	} else {
		const nextStep = getNextStep(type);
		if (nextStep) {
			addSelectionStep(nextStep);
		} else {
			addChatMessage('선택이 완료되었습니다. 다음 단계를 진행해주세요.', 'question');
		}
	}

	toggleButtons(`${type}-options`, true);
}

function addCustomInputWithConfirm(type, optionsContainer) {
	if (!optionsContainer) {
		return;
	}

	const inputWrapper = document.createElement('div');
	inputWrapper.classList.add('input-wrapper');
	inputWrapper.style.display = 'inline-flex';
	inputWrapper.style.alignItems = 'center';

	const inputElement = document.createElement('input');
	inputElement.setAttribute('type', 'number');
	inputElement.setAttribute('min', '1');
	inputElement.setAttribute('placeholder', '직접 입력');
	inputElement.classList.add('custom-input');
	inputElement.style.marginLeft = '20px';
	inputElement.style.height = '46px';

	const confirmButton = document.createElement('button');
	confirmButton.innerText = '확인';
	confirmButton.classList.add('option');
	confirmButton.style.marginLeft = '0px';
	confirmButton.onclick = () => {
		const customValue = parseInt(inputElement.value);
		if (customValue >= 1) {
			handleCustomCountSelection(type, customValue);
		}
	};

	inputWrapper.appendChild(inputElement);
	inputWrapper.appendChild(confirmButton);
	optionsContainer.appendChild(inputWrapper);
}

function handleCustomCountSelection(type, count) {
	selectedOptions[`numberof${type}`] = count;
	updateSelectedItems();
	addChatMessage(`'${count}개'를 선택하셨습니다.`, 'answer');

	const nextStep = type === 'door' ? 'handle' : getNextStep(type);
	
	if (nextStep) {
		addSelectionStep(nextStep);
	} else {
		addChatMessage('선택이 완료되었습니다. 다음 단계를 진행해주세요.', 'question');
	}

	toggleButtons(`${type}-add-options`, true);
}

function toggleButtons(containerId, disable = true) {
	const buttons = document.querySelectorAll(`#${containerId} button.option`);
	if (buttons.length > 0) {
		buttons.forEach(button => {
			button.disabled = disable;
		});
	}
}

function addFinalStepMessage() {
	const chatBoxItem = document.createElement('div');
	chatBoxItem.classList.add('chat-box-item', 'final-step');

	const newMessage = document.createElement('div');
	newMessage.classList.add('chat-message', 'chat-question', 'final-step');

	const textNode = document.createElement('span');
	textNode.innerText = '선택이 완료되었습니다.';
	newMessage.appendChild(textNode);

	const rightSide = document.createElement('div');
	rightSide.classList.add('right-side');

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
	quantityContainer.appendChild(quantityInput);

	rightSide.appendChild(quantityContainer);

	const buttonContainer = document.createElement('div');
	buttonContainer.classList.add('button-container');

	buttonContainer.appendChild(createButton('발주넣기', 'order-button', () => handleOrder(quantityInput.value)));
	buttonContainer.appendChild(createButton('장바구니', 'cart-button', () => handleCart(quantityInput.value)));

	rightSide.appendChild(buttonContainer);

	newMessage.appendChild(rightSide);

	chatBoxItem.appendChild(newMessage);
	document.getElementById('chat-container').appendChild(chatBoxItem);
	document.getElementById('chat-container').scrollTop = document.getElementById('chat-container').scrollHeight;
	AOS.refresh();
}

function createButton(buttonText, buttonClass, onClickHandler) {
	const button = document.createElement('button');
	button.classList.add('final-button', buttonClass);
	button.innerText = buttonText;
	button.onclick = onClickHandler;
	return button;
}

function handleOrder(quantity) {
	const confirmOrder = confirm('발주를 진행하시겠습니까?');
	if (confirmOrder) {
		window.location.href = '/orderConfirm';
	}
}

function handleCart(quantity) {
	const confirmCart = confirm('장바구니에 추가하겠습니까?');
	if (confirmCart) {
		// 장바구니 처리 로직
	}
}

function getNextStep(currentStep) {
	const steps = productSelectionSteps['default'];
	const currentIndex = steps.indexOf(currentStep);

	if (selectedOptions['category'] === '거울') {
		return currentIndex !== -1 && currentIndex < steps.length - 1 ? steps[currentIndex + 1] : 'final';
	} else {
		if (currentStep === 'color') {
			return 'door';
		} else if (currentStep === 'door') {
			return selectedOptions['door'] === 'add' ? 'numberofdoor' : 'handle';
		} else if (currentStep === 'numberofdoor') {
			return 'handle';
		} else if (currentStep === 'handle') {
			return selectedOptions['handle'] === 'add' ? 'numberofhandle' : 'final';
		} else if (currentStep === 'numberofhandle') {
			return 'final';
		} else {
			return currentIndex !== -1 && currentIndex < steps.length - 1 ? steps[currentIndex + 1] : 'final';
		}
	}
}

function addSelectionStep(type) {
	if (type === 'final') {
		addFinalStepMessage();
		return;
	}

	const options = getOptionsForStep(type);
	if (options.length) {
		addChatMessage(`${type}을(를) 선택하세요:`, 'question', options, `${type}-options`, type);
	} else {
		addChatMessage(`${type}을(를) 선택할 옵션이 없습니다.`, 'question');
	}
}

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
		case 'led':
			if (selectedOptions['category'] === '거울') {
				return ['add', 'notadd'].map(label => ({
					label: label, action: () => handleSelection('led', label)
				}));
			}
			return [];
		case 'door':
		case 'handle':
			if (selectedOptions['category'] !== '거울') {
				return ['add', 'notadd'].map(label => ({
					label: label, action: () => handleSelection(type, label)
				}));
			}
			return [];
		default:
			return [];
	}
}

function addChatMessage(message, type, options = null, containerId = '', step = '') {
	const chatBox = document.getElementById('chat-box');
	const chatBoxItem = document.createElement('div');
	chatBoxItem.classList.add('chat-box-item');
	const newMessage = document.createElement('div');
	newMessage.classList.add('chat-message', `chat-${type}`);

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
	chatBox.appendChild(chatBoxItem);
	document.getElementById('chat-container').scrollTop = document.getElementById('chat-container').scrollHeight;
	AOS.refresh();

	if (options && containerId) {
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

		if (step === 'numberofdoor' || step === 'numberofhandle') {
			addCustomInputWithConfirm(step, optionsContainer);
		}
	}
}

function fadeOut(element, duration, callback) {
	element.style.transition = `opacity ${duration}ms`;
	element.style.opacity = 0;

	setTimeout(() => {
		element.style.display = 'none';
		if (callback) callback();
	}, duration);
}

function resetStepsFrom(step) {
	const stepIndex = productSelectionSteps['default'].indexOf(step);
	const stepsToReset = productSelectionSteps['default'].slice(stepIndex + 1);

	const answerElements = document.querySelectorAll(`.chat-box-item .chat-answer`);
	answerElements.forEach(answerElement => {
		if (answerElement.innerText.includes(selectedOptions[step])) {
			fadeOut(answerElement.closest('.chat-box-item'), 300, () => {
				answerElement.closest('.chat-box-item').remove();
			});
		}
	});

	if (step === 'numberofdoor' || step === 'numberofhandle') {
		selectedOptions[`numberof${step.slice(-5)}`] = ''; 
	}

	selectedOptions[step] = '';
	stepsToReset.forEach(nextStep => {
		removeElements(nextStep);
		selectedOptions[nextStep] = '';
	});

	toggleButtons(`${step}-options`, false);
	updateSelectedItems();
}

function removeElements(step) {
	const questionElements = document.querySelectorAll(`.chat-box-item .chat-question`);
	const answerElements = document.querySelectorAll(`.chat-box-item .chat-answer`);
	const optionContainer = document.getElementById(`${step}-options`);

	questionElements.forEach(questionElement => {
		if (questionElement.closest('.chat-box-item').innerText.includes(step)) {
			fadeOut(questionElement.closest('.chat-box-item'), 300, () => {
				questionElement.closest('.chat-box-item').remove();
			});
		}
	});

	answerElements.forEach(answerElement => {
		const selectedValue = selectedOptions[step];
		if (selectedValue && answerElement.innerText.includes(selectedValue)) {
			fadeOut(answerElement.closest('.chat-box-item'), 300, () => {
				answerElement.closest('.chat-box-item').remove();
			});
		}
	});

	if (optionContainer) {
		fadeOut(optionContainer.closest('.chat-box-item'), 300, () => {
			optionContainer.closest('.chat-box-item').remove();
		});
	}
}

function scrollIfNeeded(nextOptionsContainer) {
	const chatContainer = document.getElementById('chat-container');
	const nextOptionsBottom = nextOptionsContainer.getBoundingClientRect().bottom;
	const containerBottom = chatContainer.getBoundingClientRect().bottom;

	if (nextOptionsBottom > containerBottom - 100) {
		chatContainer.scrollTo({
			top: chatContainer.scrollHeight,
			behavior: 'smooth'
		});
	}
}

addChatMessage('카테고리를 선택하세요:', 'question', getOptionsForStep('category'), 'category-options');
