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

// 화면을 동적으로 그리는 함수
function renderInitialQuestion() {
    const chatBox = document.getElementById('chat-box');
    chatBox.innerHTML = ''; // 이전 내용 초기화

    // 카테고리 wrap 생성
    const categoryWrap = document.createElement('div');
    categoryWrap.id = 'category-wrap';

    // 질문 추가
    const questionDiv = document.createElement('div');
    questionDiv.id = 'category-question';
    questionDiv.innerText = initialQuestion.step + '을(를) 선택하세요:';
    categoryWrap.appendChild(questionDiv);

    // 옵션 버튼 추가
    const optionDiv = document.createElement('div');
    optionDiv.id = 'category-option';
    initialQuestion.options.forEach(option => {
        const button = document.createElement('button');
        button.innerText = option;
        button.onclick = () => handleCategorySelection(option);
        optionDiv.appendChild(button);
    });
    categoryWrap.appendChild(optionDiv);

    document.getElementById('chat-box').appendChild(categoryWrap);
}

// 카테고리 선택 처리
function handleCategorySelection(category) {
    const answerDiv = document.createElement('div');
    answerDiv.id = 'category-answer';
    answerDiv.innerText = `${category}을(를) 선택하셨습니다.`;
    document.getElementById('category-wrap').appendChild(answerDiv);

    // 선택한 카테고리에 따른 흐름 가져오기
    const nextStep = initialQuestion.next(category); // 'mirror', 'top', 'low', 'flap', 'slide'
    currentFlow.push(productFlowSteps[nextStep][0].step); // 현재 흐름에 카테고리 추가

    // 제품 옵션 업데이트
    if (nextStep) {
        updateProductOptions(nextStep, 0); // 첫 번째 단계로 업데이트
    }
}

// 제품 옵션 업데이트 함수
function updateProductOptions(categoryKey, stepIndex) {
    const steps = productFlowSteps[categoryKey]; // 선택한 카테고리에 따른 단계
    const step = steps[stepIndex]; // 현재 단계 가져오기

    if (!step) {
        console.error(`No steps found for category: ${categoryKey}`);
        return; // 단계가 없으면 함수 종료
    }

    // 단계별 wrap, question, option, answer를 동적으로 생성
    const stepWrap = document.createElement('div');
    stepWrap.id = `${step.step}-wrap`; // 동적으로 ID 할당

    // 질문 추가
    const questionDiv = document.createElement('div');
    questionDiv.id = `${step.step}-question`;
    questionDiv.innerText = `${step.step}을(를) 선택하세요:`; // 질문 추가
    stepWrap.appendChild(questionDiv);
    
    // 옵션 추가
    const optionDiv = document.createElement('div');
    optionDiv.id = `${step.step}-option`; // 단계에 맞는 ID 추가
    step.options.forEach(option => {
        const button = document.createElement('button');
        button.innerText = option; // 제품 이름
        button.onclick = () => handleProductSelection(option, categoryKey, step); // 제품 선택 처리
        optionDiv.appendChild(button);
    });
    stepWrap.appendChild(optionDiv);
    
    // 답변 placeholder 추가 (선택되면 여기에 답변 추가)
    const answerDiv = document.createElement('div');
    answerDiv.id = `${step.step}-answer`;
    stepWrap.appendChild(answerDiv);

    document.getElementById('chat-box').appendChild(stepWrap);
}

// 제품 선택 처리
function handleProductSelection(product, categoryKey, step) {
    const answerDiv = document.getElementById(`${step.step}-answer`);
    answerDiv.innerText = `${product}을(를) 선택하셨습니다.`; // 선택한 답변 추가

    // 다음 단계로 진행하기 위한 로직을 추가합니다.
    const nextStepKey = step.next; // 다음 단계의 next
    let nextStepIndex;

    // 선택된 옵션에 따른 next 단계 업데이트
    if (typeof nextStepKey === 'function') {
        const nextKey = nextStepKey(product); // 선택된 옵션에 따라 다음 단계 결정
        nextStepIndex = productFlowSteps[categoryKey].findIndex(s => s.step === nextKey); // 다음 단계 인덱스 찾기
        currentFlow.push(step.next(product)); 
    } else {
        nextStepIndex = productFlowSteps[categoryKey].findIndex(s => s.step === nextStepKey);
        currentFlow.push(step.next); 
    }

    // 현재 흐름에 다음 단계 추가
    if (nextStepIndex >= 0) {
        updateProductOptions(categoryKey, nextStepIndex); // 다음 단계의 질문 및 옵션 업데이트
    } else {
        // 마지막 단계인 경우
        const finalWrap = document.createElement('div');
        finalWrap.id = 'final-wrap'; // final 단계 wrap 생성

        const finalAnswer = document.createElement('div');
        finalAnswer.id = 'final-answer'; // final 단계 answer 추가
        finalAnswer.innerText = '모든 선택이 완료되었습니다.';

        finalWrap.appendChild(finalAnswer);
        document.getElementById('chat-box').appendChild(finalWrap);
    }
}

// 페이지가 로드될 때 초기 질문을 렌더링
window.onload = renderInitialQuestion;
