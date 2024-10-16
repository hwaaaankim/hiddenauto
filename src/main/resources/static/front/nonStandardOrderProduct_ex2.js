import { productData } from './data.js';

AOS.init({
    duration: 500,
    easing: 'ease-in-out',
    once: true
});

// 선택된 옵션 객체 초기화
let selectedOptions = {
    category: '',
    product: '',
    size: '',
    color: '',
    led: '',
    ledcolor: '',
    door: '',
    handle: '',
    numberofdoor: '',
    numberofhandle: ''
};

// 유저가 진행한 단계를 추적하는 currentFlow 배열
let currentFlow = [];

// 카테고리별 흐름 설정
const productFlowSteps = {
    mirror: [
        { step: 'category', options: ['거울'], next: 'product' },
        { step: 'product', options: ['Product 1', 'Product 2'], next: 'size' },
        { step: 'size', options: ['Small', 'Medium', 'Large'], next: 'color' },
        { step: 'color', options: ['Red', 'Blue'], next: 'led' },
        { step: 'led', options: ['ADD', 'NOT ADD'], next: (selectedOption) => selectedOption === 'ADD' ? 'ledcolor' : 'final' },
        { step: 'ledcolor', options: ['Warm', 'Cool'], next: 'final' },
        { step: 'final'}
    ],
    top: [
        { step: 'category', options: ['상부장'], next: 'product' },
        { step: 'product', options: ['Product A', 'Product B'], next: 'size' },
        { step: 'size', options: ['Small', 'Medium', 'Large'], next: 'color' },
        { step: 'color', options: ['Red', 'Blue'], next: 'door' },
        { step: 'door', options: ['add', 'notadd'], next: (selectedOption) => selectedOption === 'add' ? 'numberofdoor' : 'handle' },
        { step: 'numberofdoor', options: ['1', '2', '3'], next: 'handle' },
        { step: 'handle', options: ['add', 'notadd'], next: (selectedOption) => selectedOption === 'add' ? 'numberofhandle' : 'final' },
        { step: 'numberofhandle', options: ['1', '2', '3'], next: 'final' },
        { step: 'final'}
    ],
    low: [
        { step: 'category', options: ['하부장'], next: 'product' },
        { step: 'product', options: ['Product A', 'Product B'], next: 'size' },
        { step: 'size', options: ['Small', 'Medium', 'Large'], next: 'color' },
        { step: 'color', options: ['Red', 'Blue'], next: 'door' },
        { step: 'door', options: ['add', 'notadd'], next: (selectedOption) => selectedOption === 'add' ? 'numberofdoor' : 'handle' },
        { step: 'numberofdoor', options: ['1', '2', '3'], next: 'handle' },
        { step: 'handle', options: ['add', 'notadd'], next: (selectedOption) => selectedOption === 'add' ? 'numberofhandle' : 'final' },
        { step: 'numberofhandle', options: ['1', '2', '3'], next: 'final' },
        { step: 'final'}
    ],
    flap: [
        { step: 'category', options: ['플랩장'], next: 'product' },
        { step: 'product', options: ['Product A', 'Product B'], next: 'size' },
        { step: 'size', options: ['Small', 'Medium', 'Large'], next: 'color' },
        { step: 'color', options: ['Red', 'Blue'], next: 'door' },
        { step: 'door', options: ['add', 'notadd'], next: (selectedOption) => selectedOption === 'add' ? 'numberofdoor' : 'handle' },
        { step: 'numberofdoor', options: ['1', '2', '3'], next: 'handle' },
        { step: 'handle', options: ['add', 'notadd'], next: (selectedOption) => selectedOption === 'add' ? 'numberofhandle' : 'final' },
        { step: 'numberofhandle', options: ['1', '2', '3'], next: 'final' },
        { step: 'final'}
    ],
    slide: [
        { step: 'category', options: ['슬라이드장'], next: 'product' },
        { step: 'product', options: ['Product A', 'Product B'], next: 'size' },
        { step: 'size', options: ['Small', 'Medium', 'Large'], next: 'color' },
        { step: 'color', options: ['Red', 'Blue'], next: 'door' },
        { step: 'door', options: ['add', 'notadd'], next: (selectedOption) => selectedOption === 'add' ? 'numberofdoor' : 'handle' },
        { step: 'numberofdoor', options: ['1', '2', '3'], next: 'handle' },
        { step: 'handle', options: ['add', 'notadd'], next: (selectedOption) => selectedOption === 'add' ? 'numberofhandle' : 'final' },
        { step: 'numberofhandle', options: ['1', '2', '3'], next: 'final' },
        { step: 'final'}
    ]
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

// 선택된 단계를 처리하는 함수
function handleSelection(type, value) {
    selectedOptions[type] = value;
    currentFlow.push(type);
    updateSelectedItems();
    addChatMessage(`'${value}'을(를) 선택하셨습니다.`, 'answer', null, `${type}-options`, type);

    const nextStep = getNextStep(type);
    if (nextStep) {
        addSelectionStep(nextStep);
    } else {
        addFinalStepMessage();
    }

    toggleButtons(`${type}-options`, true);
}

// 다음 선택 단계를 반환하는 함수
function getNextStep(currentStep) {
    const selectedCategory = selectedOptions['category'];
    const flow = productFlowSteps[selectedCategory];

    const currentStepObj = flow.find(f => f.step === currentStep);
    if (typeof currentStepObj.next === 'function') {
        return currentStepObj.next(selectedOptions[currentStep]);
    }
    return currentStepObj.next;
}

// 선택 항목을 추가하는 함수 (각 단계에 맞는 id 부여)
function addSelectionStep(type) {
    const options = getOptionsForStep(type);
    const message = options.length ? `${type}을(를) 선택하세요:` : `${type}을(를) 선택할 옵션이 없습니다.`;
    addChatMessage(message, 'question', options, `${type}-options`, type);
}

// 버튼 비활성화/활성화 함수
function toggleButtons(containerId, disable = true) {
    const buttons = document.querySelectorAll(`#${containerId} button.option`);
    buttons.forEach(button => button.disabled = disable);
}

// 선택 항목을 추가하는 함수 (각 단계에 맞는 id 부여)
function getOptionsForStep(type) {
    switch (type) {
        case 'category':
            return Object.keys(productFlowSteps).map(category => ({
                label: category, action: () => handleSelection('category', category)
            }));
        case 'product':
        case 'size':
        case 'color':
        case 'led':
        case 'ledcolor':
        case 'door':
        case 'handle':
        case 'numberofdoor':
        case 'numberofhandle':
            return productFlowSteps[selectedOptions['category']].find(step => step.step === type).options.map(option => ({
                label: option, action: () => handleSelection(type, option)
            }));
        default:
            return [];
    }
}

// 메시지와 옵션을 추가하는 함수 (버튼 포함)
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

// 최종 메시지와 수량 입력 및 버튼을 추가하는 함수
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
    quantityInput.oninput = validateQuantityInput;
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

// 선택 초기화 및 버튼 활성화 처리
function resetStepsFrom(step) {
    const stepIndex = currentFlow.indexOf(step);
    const stepsToReset = currentFlow.slice(stepIndex + 1);

    // 현재 단계의 답변도 제거
    removeElements(step);
    stepsToReset.forEach(nextStep => {
        removeElements(nextStep);
        selectedOptions[nextStep] = '';
    });

    currentFlow = currentFlow.slice(0, stepIndex + 1);
    toggleButtons(`${step}-options`, false);
    updateSelectedItems();
}

// removeElements 함수 (옵션 및 질문을 포함한 fadeOut 적용)
function removeElements(step) {
    const questionElements = document.querySelectorAll(`.chat-box-item .chat-question`);
    const answerElements = document.querySelectorAll(`.chat-box-item .chat-answer`);
    const optionContainer = document.getElementById(`${step}-options`);

    // 질문 제거
    questionElements.forEach(questionElement => {
        if (questionElement.closest('.chat-box-item').innerText.includes(step)) {
            fadeOut(questionElement.closest('.chat-box-item'), 300, () => {
                questionElement.closest('.chat-box-item').remove();
            });
        }
    });

    // 답변 제거
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

// 요소에 fadeOut 효과를 적용하는 함수
function fadeOut(element, duration, callback) {
    element.style.transition = `opacity ${duration}ms`;
    element.style.opacity = 0;

    setTimeout(() => {
        element.style.display = 'none';
        if (callback) callback();
    }, duration);
}

// 스크롤 체크 및 필요 시 스크롤
function scrollIfNeeded(nextOptionsContainer) {
    const chatContainer = document.getElementById('chat-container');
    const nextOptionsBottom = nextOptionsContainer.getBoundingClientRect().bottom;
    const containerBottom = chatContainer.getBoundingClientRect().bottom;

    if (nextOptionsBottom > containerBottom - 200) {
        chatContainer.scrollTo({
            top: chatContainer.scrollHeight,
            behavior: 'smooth'
        });
    }
}

// 첫 질문 추가
addChatMessage('카테고리를 선택하세요:', 'question', getOptionsForStep('category'), 'category-options');
