
const initialQuestion = {
	step: { label: '카테고리', value: 'category' }, // label과 value로 구분
	options: [
		{ label: '거울', value: 'mirror' },
		{ label: '상부장', value: 'top' },
		{ label: '하부장', value: 'low' },
		{ label: '플랩장', value: 'flap' },
		{ label: '슬라이드장', value: 'slide' }
	],
	next: (selectedCategoryValue) => {
		switch (selectedCategoryValue) {
			case 'mirror': return 'mirror';
			case 'top': return 'top';
			case 'low': return 'low';
			case 'flap': return 'flap';
			case 'slide': return 'slide';
			default: return null;
		}
	}
};

const productFlowSteps = {
	mirror: [
		{
			step: 'product', label: '제품', options: [
				{ value: 'product1', label: '제품 1' },
				{ value: 'product2', label: '제품 2' }
			], next: 'frame'
		},
		{
			step: 'frame', label: '프레임', options: [
				{ value: 'slim', label: '슬림' },
				{ value: 'cycle', label: '사이클' },
				{ value: 'luxury', label: '럭셔리' },
				{ value: 'nude', label: '누드' }
			], next: 'size'
		},
		{
			step: 'size', label: '사이즈', options: [
				{ value: 'size01', label: '소형' },
				{ value: 'size02', label: '중형' },
				{ value: 'size03', label: '대형' }
			], next: 'color'
		},
		{
			step: 'color', label: '색상', options: [
				{ value: 'Red', label: '빨간색' },
				{ value: 'Blue', label: '파란색' }
			], next: 'led'
		},
		{
			step: 'led', label: 'LED 추가', options: [
				{ value: 'ADD', label: '추가' },
				{ value: 'NOT_ADD', label: '추가 안 함' }
			], next: (selectedOption) => selectedOption === 'ADD' ? 'ledcolor' : 'final'
		},
		{
			step: 'ledcolor', label: 'LED 색상', options: [
				{ value: 'Warm', label: '따뜻한 색' },
				{ value: 'Cool', label: '차가운 색' }
			], next: 'final'
		}
	],
	top: [
		{
			step: 'product', label: '제품', options: [
				{ value: 'simple', label: '심플' },
				{ value: 'nude', label: '누드' },
				{ value: 'round', label: '라운드' }
			], next: 'color'
		},
		{
			step: 'color', label: '색상', options: [
				{ value: 'Red', label: '빨간색' },
				{ value: 'Blue', label: '파란색' }
			], next: 'size'
		},
		{
			step: 'size', label: '사이즈', options: [
				{ value: 'size01', label: '소형' },
				{ value: 'size02', label: '중형' },
				{ value: 'size03', label: '대형' },
				{ value: 'size04', label: '특대형' },
				{ value: 'size05', label: '초대형' }
			], next: 'numberofdoor'
		},
		{
			step: 'numberofdoor', label: '문 수량', options: [
				{ value: '1', label: '1개' },
				{ value: '2', label: '2개' },
				{ value: '3', label: '3개' }
			], next: 'doorDirection'
		},
		{
			step: 'doorDirection', label: '문 방향', options: [
				{ value: 'left', label: '좌측' },
				{ value: 'right', label: '우측' }
			], next: 'handle'
		},
		{
			step: 'handle', label: '손잡이 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: 'led'
		},
		{
			step: 'led', label: 'LED 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: (selectedOption) => selectedOption === 'add' ? 'ledcolor' : 'tissue'
		},
		{
			step: 'ledcolor', label: 'LED 색상', options: [
				{ value: 'one', label: '색상 1' },
				{ value: 'two', label: '색상 2' },
				{ value: 'three', label: '색상 3' }
			], next: 'tissue'
		},
		{
			step: 'tissue', label: '휴지걸이 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: 'dry'
		},
		{
			step: 'dry', label: '드라이기 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: (selectedOption) => selectedOption === 'add' ? 'drydirection' : 'outlet'
		},
		{
			step: 'drydirection', label: '드라이기 방향', options: [
				{ value: 'left', label: '좌측' },
				{ value: 'right', label: '우측' }
			], next: 'outlet'
		},
		{
			step: 'outlet', label: '콘센트 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: (selectedOption) => selectedOption === 'add' ? 'outletdirection' : 'final'
		},
		{
			step: 'outletdirection', label: '콘센트 방향', options: [
				{ value: 'left', label: '좌측' },
				{ value: 'right', label: '우측' }
			], next: 'final'
		}
	],
	low: [
		{
			step: 'product', label: '제품', options: [
				{ value: 'simple', label: '심플' },
				{ value: 'clean', label: '클린' },
				{ value: 'premium', label: '프리미엄' }
			], next: 'color'
		},
		{
			step: 'color', label: '색상', options: [
				{ value: 'Red', label: '빨간색' },
				{ value: 'Blue', label: '파란색' }
			], next: 'form'
		},
		{
			step: 'form', label: '형태', options: [
				{ value: 'leg', label: '다리형' },
				{ value: 'wall', label: '벽걸이형' }
			], next: 'size'
		},
		{
			step: 'size', label: '사이즈', options: [
				{ value: 'size01', label: '소형' },
				{ value: 'size02', label: '중형' },
				{ value: 'size03', label: '대형' },
				{ value: 'size04', label: '특대형' },
				{ value: 'size05', label: '초대형' }
			], next: 'washstand'
		},
		{
			step: 'colorofmarble', label: '대리석 색상', options: [
				{ value: 'Red', label: '빨간색' },
				{ value: 'Blue', label: '파란색' }
			], next: 'washstand'
		},
		{
			step: 'washstand', label: '세면대', options: [
				{ value: 'one', label: '1구' },
				{ value: 'two', label: '2구' },
				{ value: 'three', label: '3구' },
				{ value: 'four', label: '4구' },
				{ value: 'five', label: '5구' }
			], next: 'positionofwashstand'
		},
		{
			step: 'positionofwashstand', label: '세면대 위치', options: [
				{ value: 'left', label: '좌측' },
				{ value: 'right', label: '우측' },
				{ value: 'center', label: '중앙' }
			], next: 'formofdoor'
		},
		{
			step: 'formofdoor', label: '문 형태', options: [
				{ value: 'one', label: '단일문' },
				{ value: 'two', label: '2단문' },
				{ value: 'three', label: '3단문' },
				{ value: 'four', label: '4단문' }
			], next: 'numberofdoor'
		},
		{
			step: 'numberofdoor', label: '문 수량', options: [
				{ value: '0', label: '없음' },
				{ value: '1', label: '1개' },
				{ value: '2', label: '2개' },
				{ value: '3', label: '3개' }
			], next: 'doorDirection'
		},
		{
			step: 'doorDirection', label: '문 방향', options: [
				{ value: 'left', label: '좌측' },
				{ value: 'right', label: '우측' }
			], next: 'handle'
		},
		{
			step: 'handle', label: '손잡이 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: (selectedOption) => selectedOption === 'add' ? 'handletype' : 'outlet'
		},
		{
			step: 'handletype', label: '손잡이 종류', options: [
				{ value: 'one', label: '종류 1' },
				{ value: 'two', label: '종류 2' },
				{ value: 'three', label: '종류 3' },
				{ value: 'four', label: '종류 4' }
			], next: 'handlecolor'
		},
		{
			step: 'handlecolor', label: '손잡이 색상', options: [
				{ value: 'one', label: '색상 1' },
				{ value: 'two', label: '색상 2' },
				{ value: 'three', label: '색상 3' },
				{ value: 'four', label: '색상 4' }
			], next: 'outlet'
		},
		{
			step: 'outlet', label: '콘센트 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: (selectedOption) => selectedOption === 'add' ? 'outletdirection' : 'final'
		},
		{
			step: 'outletdirection', label: '콘센트 방향', options: [
				{ value: 'left', label: '좌측' },
				{ value: 'right', label: '우측' }
			], next: 'final'
		}
	],
	flap: [
		{
			step: 'product', label: '제품', options: [
				{ value: 'round', label: '라운드' },
				{ value: 'normal', label: '일반형' },
				{ value: 'complex', label: '복합형' }
			], next: 'color'
		},
		{
			step: 'color', label: '색상', options: [
				{ value: 'Red', label: '빨간색' },
				{ value: 'Blue', label: '파란색' }
			], next: 'size'
		},
		{
			step: 'size', label: '사이즈', options: [
				{ value: 'size01', label: '소형' },
				{ value: 'size02', label: '중형' },
				{ value: 'size03', label: '대형' }
			], next: 'door'
		},
		{
			step: 'door', label: '문 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: (selectedOption) => selectedOption === 'add' ? 'doorDirection' : 'led'
		},
		{
			step: 'doorDirection', label: '문 방향', options: [
				{ value: 'left', label: '좌측' },
				{ value: 'right', label: '우측' }
			], next: 'led'
		},
		{
			step: 'led', label: 'LED 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: (selectedOption) => selectedOption === 'add' ? 'ledcolor' : 'tissue'
		},
		{
			step: 'ledcolor', label: 'LED 색상', options: [
				{ value: 'one', label: '색상 1' },
				{ value: 'two', label: '색상 2' },
				{ value: 'three', label: '색상 3' }
			], next: 'tissue'
		},
		{
			step: 'tissue', label: '휴지걸이 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: 'dry'
		},
		{
			step: 'dry', label: '드라이기 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: (selectedOption) => selectedOption === 'add' ? 'drydirection' : 'outlet'
		},
		{
			step: 'drydirection', label: '드라이기 방향', options: [
				{ value: 'left', label: '좌측' },
				{ value: 'right', label: '우측' }
			], next: 'outlet'
		},
		{
			step: 'outlet', label: '콘센트 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: (selectedOption) => selectedOption === 'add' ? 'outletdirection' : 'final'
		},
		{
			step: 'outletdirection', label: '콘센트 방향', options: [
				{ value: 'left', label: '좌측' },
				{ value: 'right', label: '우측' }
			], next: 'final'
		}
	],
	slide: [
		{
			step: 'product', label: '제품', options: [
				{ value: 'premium', label: '프리미엄' },
				{ value: 'base', label: '베이스' },
				{ value: 'enough', label: '충분한' },
				{ value: 'perfect', label: '완벽한' }
			], next: 'color'
		},
		{
			step: 'color', label: '색상', options: [
				{ value: 'Red', label: '빨간색' },
				{ value: 'Blue', label: '파란색' }
			], next: 'size'
		},
		{
			step: 'size', label: '사이즈', options: [
				{ value: 'size01', label: '소형' },
				{ value: 'size02', label: '중형' },
				{ value: 'size03', label: '대형' }
			], next: 'door'
		},
		{
			step: 'door', label: '문 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: 'handle'
		},
		{
			step: 'handle', label: '손잡이 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: 'led'
		},
		{
			step: 'led', label: 'LED 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: (selectedOption) => selectedOption === 'add' ? 'ledcolor' : 'tissue'
		},
		{
			step: 'ledcolor', label: 'LED 색상', options: [
				{ value: 'one', label: '색상 1' },
				{ value: 'two', label: '색상 2' },
				{ value: 'three', label: '색상 3' }
			], next: 'tissue'
		},
		{
			step: 'tissue', label: '휴지걸이 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: 'dry'
		},
		{
			step: 'dry', label: '드라이기 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: (selectedOption) => selectedOption === 'add' ? 'drydirection' : 'outlet'
		},
		{
			step: 'drydirection', label: '드라이기 방향', options: [
				{ value: 'left', label: '좌측' },
				{ value: 'right', label: '우측' }
			], next: 'outlet'
		},
		{
			step: 'outlet', label: '콘센트 추가', options: [
				{ value: 'add', label: '추가' },
				{ value: 'notadd', label: '추가 안 함' }
			], next: (selectedOption) => selectedOption === 'add' ? 'outletdirection' : 'final'
		},
		{
			step: 'outletdirection', label: '콘센트 방향', options: [
				{ value: 'left', label: '좌측' },
				{ value: 'right', label: '우측' }
			], next: 'final'
		}
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

let numberOfOption = [];

// size나 width에 따라 numberOfOption을 설정하는 함수
function determineNumberOfOptions(sizeOrWidth) {
	
	if (typeof sizeOrWidth === 'string') { // size 선택된 경우
		if (sizeOrWidth === 'size01') {
			numberOfOption = [0, 1, 2];
		} else if (sizeOrWidth === 'size02') {
			numberOfOption = [0, 2, 3];
		} else if (sizeOrWidth === 'size03') {
			numberOfOption = [0, 3, 4];
		} else if (sizeOrWidth === 'size04') {
			numberOfOption = [0, 3, 4];
		} else if (sizeOrWidth === 'size05') {
			numberOfOption = [0, 3, 5];
		} else {
			numberOfOption = []; // 범위를 벗어나는 경우 기본값 설정
		}
	} else if (typeof sizeOrWidth === 'number') { // width 입력된 경우
		if (sizeOrWidth >= 1 && sizeOrWidth <= 500) {
			numberOfOption = [0, 1, 2];
		} else if (sizeOrWidth <= 800) {
			numberOfOption = [0, 2, 3];
		} else if (sizeOrWidth <= 1200) {
			numberOfOption = [0, 3, 4];
		} else if (sizeOrWidth <= 2000) {
			numberOfOption = [0, 3, 4, 5];
		} else {
			numberOfOption = []; // 범위를 벗어나는 경우 기본값 설정
		}
	}
}

function resetNumberOfOption() {
	numberOfOption = []; // 초기화
}

// size 선택 시 호출
function handleSizeSelection(size, categoryKey) {
	if (categoryKey === 'top' || categoryKey === 'low') {
		determineNumberOfOptions(size); // 선택된 size에 따라 numberOfOption 설정
	}
	updateProductOptions(categoryKey, nextStepIndex); // 다음 단계로 진행
}

// width 입력 시 확인 버튼 클릭 시 호출
function handleWidthInput(width, categoryKey) {
	if (categoryKey === 'top' || categoryKey === 'low') {
		determineNumberOfOptions(width); // 입력된 width에 따라 numberOfOption 설정
	}
	updateProductOptions(categoryKey, nextStepIndex); // 다음 단계로 진행
}

function showOverlay() {
	let overlay = document.getElementById('order-overlay');
	if (!overlay) {
		overlay = document.createElement('div');
		overlay.id = 'order-overlay';
		document.body.appendChild(overlay);
	}
	overlay.style.pointerEvents = 'auto';  // 클릭 차단 활성화
	overlay.style.opacity = '1';  // fadeIn
}

function hideOverlay() {
	const overlay = document.getElementById('order-overlay');
	if (overlay) {
		overlay.style.opacity = '0';  // fadeOut
		overlay.style.pointerEvents = 'none';  // 클릭 차단 해제
		setTimeout(() => {
			overlay.remove();
		}, 500);  // fadeOut 애니메이션이 끝난 후 제거
	}
}

let renderedSteps = []; // 이미 그려진 단계들을 추적

const savedSelections = {
	category: '하부장',
	product: 'simple',
	color: 'Blue',
	form: 'leg',
};


function autoProceed(savedSelections) {
    // 오버레이 표시
    showOverlay();

    // 카테고리 선택 및 시작 흐름 설정
    const categoryKey = savedSelections.category === '거울' ? 'mirror' :
                    savedSelections.category === '상부장' ? 'top' :
                    savedSelections.category === '하부장' ? 'low' :
                    savedSelections.category === '플랩장' ? 'flap' :
                    savedSelections.category === '슬라이드장' ? 'slide' :
                    savedSelections.category;

    if (!categoryKey || !productFlowSteps[categoryKey]) {
        console.error(`알 수 없는 카테고리: ${savedSelections.category}`);
        hideOverlay();
        return;
    }

    // 카테고리 선택 처리
    handleCategorySelection(categoryKey);
    const steps = productFlowSteps[categoryKey];

    // 자동 진행 함수 정의
    function proceedWithSelections(stepIndex = 0) {
        const currentStep = steps[stepIndex];
        const currentSelection = savedSelections[currentStep.step];

        // 저장된 선택 값이 없는 경우 자동 진행 멈춤
        if (!currentSelection) {
            hideOverlay();
            return;
        }

        // 현재 단계에서 선택 자동 처리
        handleProductSelection(currentSelection, categoryKey, currentStep);

        // 다음 단계 계산
        const nextStepKey = currentStep.next;
        let nextStepIndex;

        if (typeof nextStepKey === 'function') {
            const nextStep = nextStepKey(currentSelection);
            nextStepIndex = steps.findIndex(step => step.step === nextStep);
        } else {
            nextStepIndex = steps.findIndex(step => step.step === nextStepKey);
        }

        // 다음 단계가 존재하면 일정 시간 후 자동 진행
        if (nextStepIndex >= 0) {
            setTimeout(() => proceedWithSelections(nextStepIndex), 500);
        } else {
            // 마지막 단계 도달 시 오버레이 제거
            hideOverlay();
        }
    }

    // 첫 번째 단계부터 자동 진행 시작
    setTimeout(() => proceedWithSelections(0), 500);
}



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
	questionDiv.innerText = `${initialQuestion.step.label}을(를) 선택하세요:`; // step의 label을 표시
	categoryWrap.appendChild(questionDiv);

	// 옵션 버튼 추가
	const optionDiv = document.createElement('div');
	optionDiv.id = 'category-option';
	optionDiv.classList.add('non-standard-option');

	initialQuestion.options.forEach(option => {
		const button = document.createElement('button');
		button.innerText = option.label; // label로 버튼 텍스트 설정
		button.classList.add('non-standard-btn');
		button.onclick = () => handleCategorySelection(option.value); // value를 이벤트에 전달
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

	const nextStep = typeof nextStepKey === 'function' ? nextStepKey(product) : nextStepKey;
	nextStepIndex = productFlowSteps[categoryKey].findIndex(s => s.step === nextStep);
	currentFlow.push(nextStep);


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
	questionDiv.innerText = `${step.label}을(를) 선택하세요:`; // label을 사용해 질문 텍스트 생성
	stepWrap.appendChild(questionDiv);

	// 옵션 추가 (옵션을 동적으로 처리)
	const optionDiv = document.createElement('div');
	optionDiv.id = `${step.step}-option`;
	optionDiv.classList.add('non-standard-option');
	if (step.step === 'size') {
		// 사이즈 옵션 추가
		step.options.forEach(option => {
			const button = document.createElement('button');
			button.innerText = option.label;
			button.classList.add('non-standard-btn');
			button.addEventListener('click', () => {
				// top 또는 low 카테고리에서만 determineNumberOfOptions 호출
				if (categoryKey === 'top' || categoryKey === 'low') {
					determineNumberOfOptions(option.value);
				}
				handleProductSelection(option.value, categoryKey, step);
			});
			optionDiv.appendChild(button);
		});

		// 입력 필드 추가
		const fields = categoryKey === 'mirror' ? ['width', 'height'] : ['width', 'height', 'depth'];
		fields.forEach(field => {
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
			const width = parseInt(document.getElementById('width-input').value);
			const height = parseInt(document.getElementById('height-input').value);
			const depth = categoryKey === 'mirror' ? null : parseInt(document.getElementById('depth-input').value);

			if (!width || !height || (categoryKey !== 'mirror' && !depth)) {
				alert('모든 필드를 입력하세요.');
				return;
			}

			if (categoryKey === 'top' || categoryKey === 'low') {
				determineNumberOfOptions(width); // 입력값에 따라 numberOfOption 설정
			}

			const sizeText = `Width: ${width}, Height: ${height}${categoryKey !== 'mirror' ? `, Depth: ${depth}` : ''}`;
			handleDirectInput(sizeText, categoryKey, step);
		});
		optionDiv.appendChild(confirmButton);
	} else if (step.step === 'numberofdoor' && numberOfOption.length > 0) {
		// numberofdoor 단계의 경우, numberOfOption에 따른 동적 옵션 렌더링
		numberOfOption.forEach(option => {
			const button = document.createElement('button');
			button.innerText = `${option}개`;
			button.classList.add('non-standard-btn');
			button.addEventListener('click', () => handleProductSelection(option, categoryKey, step));
			optionDiv.appendChild(button);
		});
	} else {
		// 일반적인 단계 - 옵션 버튼 추가
		step.options.forEach(option => {
			const button = document.createElement('button');
			button.innerText = option.label;
			button.classList.add('non-standard-btn');
			button.addEventListener('click', () => handleProductSelection(option.value, categoryKey, step));
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
	scrollIfNeeded(stepWrap);
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

	// 선택한 단계 이후의 모든 단계 제거
	const stepsToDelete = currentFlow.slice(currentFlow.indexOf(step) + 1);
	stepsToDelete.forEach((stepToDelete) => {
		const wrapDiv = document.getElementById(`${stepToDelete}-wrap`);
		if (wrapDiv) fadeOutElement(wrapDiv); // fadeOut 적용
	});

	// 현재 단계까지만 currentFlow 유지
	currentFlow = currentFlow.slice(0, currentFlow.indexOf(step) + 1);

	// 선택된 단계의 옵션 비활성화 해제
	const optionDiv = document.getElementById(`${step}-option`);
	if (optionDiv) {
		optionDiv.classList.remove('disabled-option');
	}

	// size 및 그 이전 단계에서 초기화 시 numberOfOption 초기화
	const sizeIndex = currentFlow.indexOf('size');
	const resetIndex = currentFlow.indexOf(step);

	if (sizeIndex !== -1 && resetIndex <= sizeIndex) {
		resetNumberOfOption(); // numberOfOption 초기화
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



window.onload = () => {
	// 초기 질문을 렌더링하고 나서 autoProceed 호출
	renderInitialQuestion();

	// renderInitialQuestion이 완료된 후 autoProceed 실행
	setTimeout(() => {
		autoProceed(savedSelections);
	}, 500);  // 약간의 지연을 추가하여 DOM이 렌더링되는 시간을 확보
};

