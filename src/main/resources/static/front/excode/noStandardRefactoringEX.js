const initialQuestion = {
	step: { label: '카테고리', value: 'category' }, // 대분류를 의미
	question: '1차 카테고리를 선택 해 주세요',
	options: [
		{ label: '상부장', value: 'top', id: 1 },
		{ label: '하부장', value: 'low', id: 2 },
		{ label: '슬라이드장', value: 'slide', id: 3 },
		{ label: '거울', value: 'mirror', id: 4 },
		{ label: '플랩장', value: 'flap', id: 6 }
	],
	next: (selectedCategoryValue) => {
		switch (selectedCategoryValue) {
			case 'top': return 'top';
			case 'low': return 'low';
			case 'slide': return 'slide';
			case 'mirror': return 'mirror';
			case 'flap': return 'flap';
			default: return null;
		}
	}
};

/**
 * 유저 선택, sign 값, 두 개의 스탭을 받아 다음 단계를 결정하는 함수
 * @param {string} userSelection - 유저의 현재 단계 선택 값
 * @param {boolean} signValue - 현재 단계의 sign 값 (true/false)
 * @param {string} trueStep - sign 값이 true일 때 이동할 단계
 * @param {string} falseStep - sign 값이 false일 때 이동할 단계
 * @returns {string} - 다음 단계 이름
 */
function determineNextStep(userSelection, signValue, trueStep, falseStep) {
    // 유저 선택 또는 sign 값을 기반으로 다음 단계를 결정
    if (userSelection === 'add') {
        return signValue ? trueStep : falseStep;
    }
    return falseStep; // 기본적으로 falseStep 반환
}

const flapOptions = {};
const productFlowSteps = {
	mirror: [
		{
			step: 'product', label: '제품', question: '거울의 제품을 선택하세요.', next: 'size'
		},
		{
			step: 'size', label: '사이즈', question: '거울의 사이즈를 선택하세요.', next: 'color'
		},
		{
			step: 'color', label: '색상', question: '거울의 색상을 선택하세요.', 
			next: (selectedOption) => selectedOption === true ? 'led' : 'final'
		},
		{
			step: 'led', label: 'LED 추가', question: 'LED를 추가하시겠습니까?', next: (selectedOption) => selectedOption === 'ADD' ? 'ledcolor' : 'final'
		},
		{
			step: 'ledcolor', label: 'LED 색상', question: 'LED의 색상을 선택하세요.', next: 'final'
		}
	],
	top: [
		{
			step: 'product', label: '제품', question: '상부장의 제품을 선택하세요.', next: 'color'
		},
		{
			step: 'color', label: '색상', question: '상부장의 색상을 선택하세요.', next: 'size'
		},
		{
			step: 'size', label: '사이즈', question: '상부장의 사이즈를 선택하세요.', next: 'door'
		},
		{
			step: 'door', label: '문 추가', question: '문을 추가하시겠습니까?', next: (selectedOption) => selectedOption === 'add' ? 'numberofdoor' : 'handle'
		},
		{
			step: 'numberofdoor', label: '문 수량', question: '문 수량을 선택하세요.', next: 'doorDirection'
		},
		{
			step: 'doorDirection', label: '문 방향', question: '문의 방향을 선택하세요.', next: 'handle'
		},
		{
			step: 'handle', label: '손잡이 추가', question: '손잡이를 추가하시겠습니까?', next: 'led'
		},
		{
			step: 'led', label: '하부LED 추가 여부', question: 'LED를 추가하시겠습니까?', next: (selectedOption) => selectedOption === 'add' ? 'ledcolor' : 'tissue'
		},
		{
			step: 'ledcolor', label: 'LED 색상', question: 'LED의 색상을 선택하세요.', next: 'tissue'
		},
		{
			step: 'tissue', label: '휴지걸이 추가', question: '휴지걸이를 추가하시겠습니까?', next: 'dry'
		},
		{
			step: 'dry', label: '드라이기 추가', question: '드라이기를 추가하시겠습니까?', next: (selectedOption) => selectedOption === 'add' ? 'drydirection' : 'outlet'
		},
		{
			step: 'drydirection', label: '드라이기 방향', question: '드라이기의 방향을 선택하세요.', next: 'outlet'
		},
		{
			step: 'outlet', label: '콘센트 추가', question: '콘센트를 추가하시겠습니까?', next: (selectedOption) => selectedOption === 'add' ? 'outletdirection' : 'final'
		},
		{
			step: 'outletdirection', label: '콘센트 방향', question: '콘센트의 방향을 선택하세요.', next: 'final'
		}
	],
	low: [
		{
			step: 'product', label: '제품', question: '하부장의 제품을 선택하세요.', next: 'color'
		},
		{
			step: 'color', label: '색상', question: '하부장의 색상을 선택하세요.', next: 'form'
		},
		{
			step: 'form', label: '형태', question: '하부장의 형태를 선택하세요.', next: 'size'
		},
		{
			step: 'size', label: '사이즈', question: '하부장의 사이즈를 선택하세요.', next: 'washstand'
		},
		{
			step: 'colorofmarble', label: '대리석 색상', question: '대리석의 색상을 선택하세요.', next: 'washstand'
		},
		{
			step: 'washstand', label: '세면대', question: '세면대의 개수를 선택하세요.', next: 'positionofwashstand'
		},
		{
			step: 'positionofwashstand', label: '세면대 위치', question: '세면대의 위치를 선택하세요.', next: 'door'
		},
		{
			step: 'door', label: '문 추가여부', question: '문을 추가하시겠습니까?', next: (selectedOption) => selectedOption === 'add' ? 'formofdoor' : 'handle'
		},
		{
			step: 'formofdoor', label: '문 형태', question: '문의 형태를 선택하세요.', next: 'numberofdoor'
		},
		{
			step: 'numberofdoor', label: '문 수량', question: '문의 수량을 선택하세요.', next: 'handle'
		},
		{
			step: 'handle', label: '손잡이 추가', question: '손잡이를 추가하시겠습니까?', next: (selectedOption) => selectedOption === 'add' ? 'handletype' : 'outlet'
		},
		{
			step: 'handletype', label: '손잡이 종류', question: '손잡이의 종류를 선택하세요.', next: 'handlecolor'
		},
		{
			step: 'handlecolor', label: '손잡이 색상', question: '손잡이의 색상을 선택하세요.', next: 'outlet'
		},
		{
			step: 'outlet', label: '콘센트 추가', question: '콘센트를 추가하시겠습니까?', next: (selectedOption) => selectedOption === 'add' ? 'outletdirection' : 'final'
		},
		{
			step: 'outletdirection', label: '콘센트 방향', question: '콘센트의 방향을 선택하세요.', next: 'final'
		}
	],
	flap: [
		{
			step: 'product', label: '제품', question: '플랩장의 제품을 선택하세요.', next: 'color'
		},
		{
			step: 'color', label: '색상', question: '플랩장의 색상을 선택하세요.', next: 'size'
		},
		{
			step: 'size', label: '사이즈', question: '플랩장의 사이즈를 선택하세요.', next: 'door'
		},
		{
			step: 'door', label: '문 추가', question: '문을 추가하시겠습니까?', next: (selectedOption, currentSelection) => {
				if (selectedOption === 'notadd') return 'led';
				else if (selectedOption === 'add' && currentSelection === 'complex') return 'doorDirection';
				else return 'led';
			}
		},
		{
			step: 'doorDirection', label: '문 방향', question: '문의 방향을 선택하세요.', next: 'doorRatio'
		},
		{
			step: 'doorRatio', label: '문 비율', question: '문의 비율을 선택하세요.', next: 'led'
		},
		{
			step: 'led', label: 'LED 추가', question: 'LED를 추가하시겠습니까?', next: (selectedOption) => selectedOption === 'add' ? 'ledcolor' : 'tissue'
		},
		{
			step: 'ledcolor', label: 'LED 색상', question: 'LED의 색상을 선택하세요.', next: 'tissue'
		},
		{
			step: 'tissue', label: '휴지걸이 추가', question: '휴지걸이를 추가하시겠습니까?', next: 'dry'
		},
		{
			step: 'dry', label: '드라이기 추가', question: '드라이기를 추가하시겠습니까?', next: (selectedOption) => selectedOption === 'add' ? 'drydirection' : 'outlet'
		},
		{
			step: 'drydirection', label: '드라이기 방향', question: '드라이기의 방향을 선택하세요.', next: 'outlet'
		},
		{
			step: 'outlet', label: '콘센트 추가', question: '콘센트를 추가하시겠습니까?', next: (selectedOption) => selectedOption === 'add' ? 'outletdirection' : 'final'
		},
		{
			step: 'outletdirection', label: '콘센트 방향', question: '콘센트의 방향을 선택하세요.', next: 'final'
		}
	],
	slide: [
		{
			step: 'product', label: '제품', question: '슬라이드장의 제품을 선택하세요.', next: 'color'
		},
		{
			step: 'color', label: '색상', question: '슬라이드장의 색상을 선택하세요.', next: 'size'
		},
		{
			step: 'size', label: '사이즈', question: '슬라이드장의 사이즈를 선택하세요.', next: 'door'
		},
		{
			step: 'door', label: '문 추가', question: '문을 추가하시겠습니까?', next: 'handle'
		},
		{
			step: 'handle', label: '손잡이 추가', question: '손잡이를 추가하시겠습니까?', next: 'led'
		},
		{
			step: 'led', label: 'LED 추가', question: 'LED를 추가하시겠습니까?', next: (selectedOption) => selectedOption === 'add' ? 'ledcolor' : 'tissue'
		},
		{
			step: 'ledcolor', label: 'LED 색상', question: 'LED의 색상을 선택하세요.', next: 'tissue'
		},
		{
			step: 'tissue', label: '휴지걸이 추가', question: '휴지걸이를 추가하시겠습니까?', next: 'dry'
		},
		{
			step: 'dry', label: '드라이기 추가', question: '드라이기를 추가하시겠습니까?', next: (selectedOption) => selectedOption === 'add' ? 'drydirection' : 'outlet'
		},
		{
			step: 'drydirection', label: '드라이기 방향', question: '드라이기의 방향을 선택하세요.', next: 'outlet'
		},
		{
			step: 'outlet', label: '콘센트 추가', question: '콘센트를 추가하시겠습니까?', next: (selectedOption) => selectedOption === 'add' ? 'outletdirection' : 'final'
		},
		{
			step: 'outletdirection', label: '콘센트 방향', question: '콘센트의 방향을 선택하세요.', next: 'final'
		}
	]

};
let selectedBigSort = null;    // 1차 카테고리 선택 값
let selectedMiddleSort = null; // 2차 카테고리 선택 값
let currentFlow = ['category']; // 기본적으로 category는 포함됨
let categorySelections = {};
let flapProductSelection = null;
AOS.init({
	duration: 500,
	easing: 'ease-in-out',
	once: true
});
function getProductDetails(productId) {
	return new Promise((resolve, reject) => {
		$.ajax({
			url: `/api/products/getProductDetails`,
			method: 'GET',
			data: { productId },
			success: (response) => resolve(response),
			error: (error) => reject(error)
		});
	});
}

// 로더 표시 함수
function showLoader() {
	const overlay = document.getElementById('loader-overlay');
	overlay.classList.add('show');
}

// 로더 숨김 함수
function hideLoader() {
	const overlay = document.getElementById('loader-overlay');
	overlay.classList.remove('show');
}

const fadeOutElement = (element) => {
	element.style.transition = 'opacity 0.5s ease-out';
	element.style.opacity = '0';
	setTimeout(() => {
		element.remove();
	}, 500); // 0.5초 후 요소 제거
};

let numberOfOption = [];

// 1. 문의 방향을 위한 변수를 선언
let doorDirectionOptions = [];

// 2. numberofdoor에 따른 방향 조합을 생성하는 함수
function generateDoorDirectionOptions(doorCount) {
	doorDirectionOptions = []; // 초기화

	// 문의 수에 따른 방향 조합 생성
	function getDirectionCombinations(current, depth) {
		if (depth === doorCount) {
			doorDirectionOptions.push({
				value: current.join('-').toLowerCase(),
				label: current.join('')
			});
			return;
		}

		getDirectionCombinations([...current, '좌'], depth + 1);
		getDirectionCombinations([...current, '우'], depth + 1);
	}

	getDirectionCombinations([], 0);

}


function handleNumberOfDoorSelection(doorCount, categoryKey) {
	if (categoryKey === 'top') {
		generateDoorDirectionOptions(doorCount); // doorCount에 따라 방향 조합 생성
	}
}

// size나 width에 따라 numberOfOption을 설정하는 함수
function determineNumberOfOptions(sizeOrWidth) {
	let width;

	// sizeOrWidth가 문자열 형태인 경우 (예: "600*800*400")
	if (typeof sizeOrWidth === 'string' && sizeOrWidth.includes('*')) {
		// 첫 번째 값을 width로 추출
		width = parseInt(sizeOrWidth.split('*')[0], 10);
	} else if (typeof sizeOrWidth === 'string') {
		// 기존의 size01, size02 등의 문자열 처리
		if (sizeOrWidth === 'size01') {
			numberOfOption = [1, 2];
			return;
		} else if (sizeOrWidth === 'size02') {
			numberOfOption = [2, 3];
			return;
		} else if (sizeOrWidth === 'size03') {
			numberOfOption = [3, 4];
			return;
		} else if (sizeOrWidth === 'size04') {
			numberOfOption = [3, 4];
			return;
		} else if (sizeOrWidth === 'size05') {
			numberOfOption = [3, 4, 5];
			return;
		} else {
			numberOfOption = [];
			return;
		}
	} else if (typeof sizeOrWidth === 'number') {
		// 입력된 width가 숫자일 경우 그대로 사용
		width = sizeOrWidth;
	}

	// width 값이 존재할 경우, 문의 갯수 설정
	if (width >= 1 && width <= 500) {
		numberOfOption = [1, 2];
	} else if (width <= 800) {
		numberOfOption = [2, 3];
	} else if (width <= 1200) {
		numberOfOption = [3, 4];
	} else if (width <= 2000) {
		numberOfOption = [3, 4, 5];
	} else {
		numberOfOption = [];
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

// 도면 및 3D 버튼 활성화 함수
function enableModelingAndThreeDButtons() {
	const modelingBtn = document.getElementById('modeling-btn');
	const threeDBtn = document.getElementById('three-d-btn');

	if (modelingBtn && threeDBtn) {
		modelingBtn.classList.remove('notUsed');
		threeDBtn.classList.remove('notUsed');
	}
}

function autoProceed(savedSelections) {
	showOverlay();

	selectedBigSort = savedSelections.category;
	const categoryKey = getCategoryKey(selectedBigSort);

	if (!categoryKey) {
		hideOverlay();
		return;
	}

	handleCategorySelection(selectedBigSort);
	selectedAnswerValue['category'] = selectedBigSort;

	if (savedSelections.middleSort) {
		setTimeout(() => handleMiddleSortSelection(savedSelections.middleSort), 500);
		selectedAnswerValue['middleSort'] = savedSelections.middleSort;
	}

	const steps = productFlowSteps[categoryKey];
	const stepKeys = Object.keys(savedSelections);

	function proceedWithSelections(stepIndex = 0) {
		if (stepIndex >= steps.length) {
			hideOverlay();
			return;
		}

		const currentStep = steps[stepIndex];
		const currentSelection = savedSelections[currentStep.step];

		if (!currentSelection) {
			hideOverlay();
			return;
		}

		selectedAnswerValue[currentStep.step] = currentSelection;

		// **1. 사이즈 입력 처리**
		if (currentStep.step === 'size' && typeof currentSelection === 'string' && currentSelection.includes('넓이')) {
			const [width, height, depth] = parseSizeText(currentSelection);
			document.getElementById('width-input').value = width;
			document.getElementById('height-input').value = height;
			if (depth) document.getElementById('depth-input').value = depth;

			document.querySelector(`#${currentStep.step}-option button`).click();

			// 다음 단계로 이동
			moveToNextStep(stepIndex);
			return;
		}

		// **2. 문의 방향 입력 처리**
		if (currentStep.step === 'doorDirection' && currentSelection && categoryKey === 'top') {
			console.log('doorDirection');
			const directionInput = document.getElementById('door-direction-input');
			directionInput.value = currentSelection;
			document.querySelector(`#${currentStep.step}-option button`).click();

			// 다음 단계로 이동
			moveToNextStep(stepIndex);
			return;
		}

		if (currentStep.step === 'doorRatio' && currentSelection) {
			const [value1, value2] = currentSelection.split(':').map(Number);
			document.getElementById('door-ratio-input-1').value = value1;
			document.getElementById('door-ratio-input-2').value = value2;
			document.querySelector(`#${currentStep.step}-option button`).click();
			moveToNextStep(stepIndex);
			return;
		}

		// **3. Product 단계 비동기 처리**
		if (currentStep.step === 'product') {
			getProductDetails(currentSelection)
				.then(() => {
					handleProductSelection(currentSelection, categoryKey, currentStep);
					// 제품 정보 조회 후 다음 단계로 이동
					moveToNextStep(stepIndex);
				})
				.catch((error) => {
					console.error("제품 정보 조회 실패:", error);
					hideOverlay();
				});
			return; // 비동기 처리 중이므로 여기서 반환
		}

		// **기본 버튼 선택 로직**
		handleProductSelection(currentSelection, categoryKey, currentStep);
		moveToNextStep(stepIndex);
	}

	// 다음 단계로 이동하는 함수
	function moveToNextStep(stepIndex) {
		const nextStepKey = steps[stepIndex].next;
		let nextStepIndex;

		// 1. 플랩장(flap) 카테고리에서 `next` 함수가 사용될 때
		if (typeof nextStepKey === 'function' && categoryKey === 'flap') {
			const currentSelection = selectedAnswerValue[steps[stepIndex].step];
			const nextKey = nextStepKey(currentSelection, flapProductSelection);
			nextStepIndex = steps.findIndex(step => step.step === nextKey);
		}
		// 2. 일반적인 경우, `next`가 함수일 때
		else if (typeof nextStepKey === 'function') {
			const nextKey = nextStepKey(selectedAnswerValue[steps[stepIndex].step]);
			nextStepIndex = steps.findIndex(step => step.step === nextKey);
		}
		// 3. `next`가 문자열일 때
		else {
			nextStepIndex = steps.findIndex(step => step.step === nextStepKey);
		}

		if (nextStepIndex >= 0) {
			setTimeout(() => proceedWithSelections(nextStepIndex), 500);
		} else {
			hideOverlay();
		}
	}


	setTimeout(() => proceedWithSelections(0), 1000);
}

function fetchSizeDataById(sizeId) {
	return new Promise((resolve, reject) => {
		$.ajax({
			url: `/api/size/getSizeById`,
			method: 'GET',
			data: { sizeId: sizeId },
			success: (response) => resolve(response),
			error: (error) => reject(error)
		});
	});
}

async function parseSizeText(sizeText) {
	// `sizeText`가 숫자인 경우, DB에서 사이즈 데이터를 가져옴
	if (typeof sizeText === 'number') {
		try {
			const sizeData = await fetchSizeDataById(sizeText);
			console.log("DB에서 가져온 사이즈 데이터:", sizeData);
			if (sizeData) {
				const { width, height, depth } = sizeData;
				return [width, height, depth];
			}
		} catch (error) {
			console.error("DB에서 사이즈 데이터를 가져오는 중 오류 발생:", error);
		}
		return [null, null, null];
	}

	// 문자열인 경우 기존 로직 유지
	if (typeof sizeText === 'string') {
		const regex = /넓이:\s*(\d+),\s*높이:\s*(\d+)(?:,\s*깊이:\s*(\d+))?/;
		const match = sizeText.match(regex);
		if (match) {
			const width = parseInt(match[1], 10);
			const height = parseInt(match[2], 10);
			const depth = match[3] ? parseInt(match[3], 10) : null;
			return [width, height, depth];
		}
	}

	return [null, null, null];
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
	questionDiv.innerText = initialQuestion.question || `${initialQuestion.step.label}을(를) 선택하세요:`; // question 속성 사용
	categoryWrap.appendChild(questionDiv);

	// 옵션 버튼 추가
	const optionDiv = document.createElement('div');
	optionDiv.id = 'category-option';
	optionDiv.classList.add('non-standard-option');

	initialQuestion.options.forEach(option => {
		const button = document.createElement('button');
		button.innerText = option.label; // label로 버튼 텍스트 설정
		button.classList.add('non-standard-btn');
		button.onclick = () => handleCategorySelection(option); // 전체 객체 전달
		optionDiv.appendChild(button);
	});


	categoryWrap.appendChild(optionDiv);
	chatBox.appendChild(categoryWrap);
	AOS.refresh(); // AOS 초기화
}

function updateProductFlowOptions(productList) {
	console.log('selectedBigSort : ', selectedBigSort);
	const categoryKey = selectedBigSort.label === '거울' ? 'mirror' :
		selectedBigSort.label === '상부장' ? 'top' :
			selectedBigSort.label === '하부장' ? 'low' :
				selectedBigSort.label === '플랩장' ? 'flap' :
					selectedBigSort.label === '슬라이드장' ? 'slide' : null;

	if (!categoryKey) return;

	// product 단계의 옵션 업데이트
	productFlowSteps[categoryKey].forEach(step => {
		if (step.step === 'product') {
			step.options = productList.map(product => ({
				value: product.id,
				label: product.name
			}));
		}
	});
	// 제품 선택 단계로 이동
	updateProductOptions(categoryKey, 0);
}

function handleMiddleSortSelection(middleSortId) {
	selectedMiddleSort = middleSortId;

	// categoryKey 계산
	const categoryKey = selectedBigSort === '거울' ? 'mirror' :
		selectedBigSort === '상부장' ? 'top' :
			selectedBigSort === '하부장' ? 'low' :
				selectedBigSort === '플랩장' ? 'flap' :
					selectedBigSort === '슬라이드장' ? 'slide' : '';

	// currentFlow에 'product' 추가
	if (!currentFlow.includes('product')) {
		currentFlow.push('product');
	}

	// renderAnswer 호출
	renderAnswer({ step: 'middleSort' }, middleSortId, categoryKey);

	// middleSort-wrap 확인 및 생성
	let middleSortWrap = document.getElementById('middleSort-wrap');
	if (!middleSortWrap) {
		middleSortWrap = document.createElement('div');
		middleSortWrap.id = 'middleSort-wrap';
		document.getElementById('chat-box').appendChild(middleSortWrap);
	}

	// 옵션 비활성화
	const optionDiv = document.getElementById(`middleSort-option`);
	optionDiv.classList.add('disabled-option');

	selectedAnswerValue['middleSort'] = selectedMiddleSort;

	// 미리 로드된 데이터에서 제품 목록 찾기
	const selectedMiddleSortData = preloadedData.middleSort.find(
		middleSort => middleSort.id === middleSortId
	);
	console.log(selectedMiddleSortData);
	if (selectedMiddleSortData) {
		// 제품 목록 가져오기
		const productList = selectedMiddleSortData.products || [];
		// 다음 옵션을 그리는 부분
		updateProductFlowOptions(productList);
	} else {
		console.error('해당 2차 카테고리에 대한 데이터를 찾을 수 없습니다.');
	}
}


function renderMiddleSortQuestion(middleSortList) {
	const chatBox = document.getElementById('chat-box');
	const middleSortWrap = document.createElement('div');
	middleSortWrap.id = 'middleSort-wrap'; // 'middle-sort-wrap'에서 수정
	middleSortWrap.classList.add('non-standard-wrap');
	middleSortWrap.setAttribute('data-aos', 'fade-in');

	// 질문 텍스트 추가
	const questionDiv = document.createElement('div');
	questionDiv.classList.add('non-standard-question');
	questionDiv.innerText = '2차 카테고리를 선택하세요:';
	middleSortWrap.appendChild(questionDiv);

	const optionDiv = document.createElement('div');
	optionDiv.id = 'middleSort-option';
	optionDiv.classList.add('non-standard-option');

	// MiddleSort 옵션 버튼 추가
	middleSortList.forEach(middleSort => {
		const button = document.createElement('button');
		button.innerText = middleSort.name; // 사용자에게 보이는 이름 (한글)
		button.classList.add('non-standard-btn');
		button.onclick = () => handleMiddleSortSelection(middleSort.id); // ID를 전송
		optionDiv.appendChild(button);
	});

	middleSortWrap.appendChild(optionDiv);
	chatBox.appendChild(middleSortWrap);
	AOS.refresh();
}

// 전역 변수 선언
let preloadedData = {
	middleSort: [] // MiddleSort 데이터를 저장할 배열
};

function handleCategorySelection(category) {
	showLoader();
	console.log(category);
	selectedBigSort = category;

	// 선택한 카테고리 값에 대한 답변 렌더링
	const categoryKey = selectedBigSort === '거울' ? 'mirror' :
		selectedBigSort === '상부장' ? 'top' :
			selectedBigSort === '하부장' ? 'low' :
				selectedBigSort === '플랩장' ? 'flap' :
					selectedBigSort === '슬라이드장' ? 'slide' : '';

	renderAnswer({ step: 'category' }, category.label, categoryKey);
	selectedAnswerValue['category'] = selectedBigSort; // 1차 카테고리 저장
	const optionDiv = document.getElementById(`category-option`);
	optionDiv.classList.add('disabled-option');

	// currentFlow에 'middleSort' 추가
	if (!currentFlow.includes('middleSort')) {
		currentFlow.push('middleSort');
	}

	// AJAX 요청을 통해 데이터를 가져오고 preloadedData에 저장
	$.ajax({
		url: `/api/bigSort/${category.id}`, // bigSortId로 MiddleSort 가져오기
		method: 'GET',
		success: (middleSortList) => {
			preloadedData.middleSort = middleSortList; // 데이터를 저장
			console.log(preloadedData);
			renderMiddleSortQuestion(middleSortList); // 2차 카테고리 렌더링
			hideLoader();
		},
		error: (error) => {
			console.error('MiddleSort 조회 실패:', error);
			hideLoader();
		}
	});
}



function handleDirectInput(inputValue, categoryKey, step) {

	if (step.step === 'size') {
		const width = document.getElementById('width-input').value;
		const height = document.getElementById('height-input').value;
		const depth = categoryKey !== 'mirror' ? document.getElementById('depth-input').value : null;
		const sizeText = `넓이: ${width}, 높이: ${height}${categoryKey !== 'mirror' ? `, 깊이: ${depth}` : ''}`;

		// selectedAnswerValue에 저장
		selectedAnswerValue[step.step] = sizeText;

		enableModelingAndThreeDButtons();
	}

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
	return new Promise((resolve, reject) => {
		const steps = productFlowSteps[categoryKey];
		const step = steps[stepIndex];
		if (!step) {
			reject('Invalid step provided.');
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
		questionDiv.innerText = step.question || `${step.label}을(를) 선택하세요:`;
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
					if (categoryKey === 'top' || categoryKey === 'low') {
						determineNumberOfOptions(option.value);
					}
					handleProductSelection(option.value, categoryKey, step);
				});
				optionDiv.appendChild(button);
			});

			// 사이즈 입력 필드 추가
			const fields = categoryKey === 'mirror' ? ['width', 'height'] : ['width', 'height', 'depth'];
			fields.forEach(field => {
				const label = document.createElement('label');
				label.innerHTML = `${field.charAt(0).toUpperCase() + field.slice(1)}: `;

				const input = document.createElement('input');
				input.type = 'number';
				input.id = `${field}-input`;
				input.classList.add('non-standard-input');

				if (field === 'width') {
					input.min = 100;
					input.max = 2000;
				} else if (field === 'height') {
					input.min = 100;
					input.max = 3000;
				} else if (field === 'depth') {
					input.min = 50;
					input.max = 800;
				}

				input.addEventListener('change', () => {
					const minValue = parseInt(input.min);
					const maxValue = parseInt(input.max);
					const value = parseInt(input.value);

					if (value < minValue) {
						input.value = minValue;
						alert(`${field.charAt(0).toUpperCase() + field.slice(1)} 값은 최소 ${minValue} 이상이어야 합니다.`);
					} else if (value > maxValue) {
						input.value = maxValue;
						alert(`${field.charAt(0).toUpperCase() + field.slice(1)} 값은 최대 ${maxValue} 이하이어야 합니다.`);
					}
				});

				label.appendChild(input);
				optionDiv.appendChild(label);
			});

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
					determineNumberOfOptions(width);
				}

				const sizeText = `넓이: ${width}, 높이: ${height}${categoryKey !== 'mirror' ? `, 깊이: ${depth}` : ''}`;
				handleDirectInput(sizeText, categoryKey, step);
				resolve();
			});
			optionDiv.appendChild(confirmButton);
		} else if (step.step === 'numberofdoor' && numberOfOption.length > 0) {
			numberOfOption.forEach(option => {
				const button = document.createElement('button');
				button.innerText = `${option}개`;
				button.classList.add('non-standard-btn');
				button.addEventListener('click', () => {
					handleNumberOfDoorSelection(option, categoryKey);
					handleProductSelection(option, categoryKey, step);
					resolve();
				});
				optionDiv.appendChild(button);
			});
		} else if (step.step === 'doorDirection' && categoryKey === 'top') {
			// label과 input 필드 추가
			const label = document.createElement('label');
			label.innerHTML = '경첩 방향: ';

			const directionInput = document.createElement('input');
			directionInput.type = 'text';
			directionInput.id = 'door-direction-input';
			directionInput.classList.add('non-standard-input'); // 디자인 클래스 추가
			directionInput.required = true;
			directionInput.placeholder = '경첩 방향을 입력 해 주세요 예시) 좌-우-좌';

			// 입력 값 검증
			directionInput.addEventListener('change', () => {
				const isValid = /^[좌우](-[좌우])*$/.test(directionInput.value.trim());
				if (!isValid) {
					alert('올바른 형식으로 입력 해 주세요. 예시) 좌-우-좌');
					directionInput.value = ''; // 올바르지 않은 입력일 경우 초기화
				}
			});

			// 확인 버튼 추가
			const confirmButton = document.createElement('button');
			confirmButton.innerText = '확인';
			confirmButton.classList.add('non-standard-btn');
			confirmButton.addEventListener('click', () => {
				const directionValue = directionInput.value.trim();

				if (!directionValue) {
					alert('경첩 방향을 입력 해 주세요.');
					return;
				}

				handleProductSelection(directionValue, categoryKey, step);
				resolve();
			});

			// label과 input을 함께 추가
			label.appendChild(directionInput);
			optionDiv.appendChild(label);
			optionDiv.appendChild(confirmButton);
		} else if (step.step === 'doorRatio' && categoryKey === 'flap') {
			// label과 input 필드 추가
			const label = document.createElement('label');
			label.innerHTML = '문 비율 입력: ';

			// 첫 번째 input 필드
			const input1 = document.createElement('input');
			input1.type = 'number';
			input1.id = 'door-ratio-input-1';
			input1.classList.add('non-standard-input');
			input1.placeholder = '첫 번째 비율';
			input1.min = 1; // 최소값 설정
			input1.required = true;

			// 두 번째 input 필드
			const input2 = document.createElement('input');
			input2.type = 'number';
			input2.id = 'door-ratio-input-2';
			input2.classList.add('non-standard-input');
			input2.placeholder = '두 번째 비율';
			input2.min = 1; // 최소값 설정
			input2.required = true;

			// 확인 버튼
			const confirmButton = document.createElement('button');
			confirmButton.innerText = '확인';
			confirmButton.classList.add('non-standard-btn');

			// 확인 버튼 클릭 시 검증 로직
			confirmButton.addEventListener('click', async () => {
				const value1 = parseInt(input1.value, 10);
				const value2 = parseInt(input2.value, 10);

				// 유효성 검사: 입력 값이 숫자가 아니거나 0 이하일 때
				if (isNaN(value1) || isNaN(value2) || value1 <= 0 || value2 <= 0) {
					alert('모든 비율 값을 올바르게 입력하세요.');
					input1.value = '';
					input2.value = '';
					return;
				}

				try {
					// size에서 width 값 가져오기
					const sizeText = selectedAnswerValue['size'];
					const [width] = await parseSizeText(sizeText);

					// width 값이 유효한지 검사
					if (!width) {
						alert('사이즈 데이터에서 넓이 값을 가져오지 못했습니다.');
						return;
					}

					// 입력된 값의 합이 width와 동일한지 검증
					if (value1 + value2 !== width) {
						alert(`입력한 비율의 합이 ${width}와 일치해야 합니다.`);
						input1.value = '';
						input2.value = '';
						return;
					}

					// 검증 통과 시 다음 단계로 이동
					const ratioText = `${value1}:${value2}`;
					handleProductSelection(ratioText, categoryKey, step);
				} catch (error) {
					console.error("비율 검증 중 오류 발생:", error);
					alert('비율 검증 중 오류가 발생했습니다.');
				}
			});

			// label, input, 버튼을 추가
			label.appendChild(input1);
			label.appendChild(input2);
			optionDiv.appendChild(label);
			optionDiv.appendChild(confirmButton);
		}
		else {
			step.options.forEach(option => {
				const button = document.createElement('button');
				button.innerText = option.label;
				button.classList.add('non-standard-btn');
				button.addEventListener('click', () => {
					handleProductSelection(option.value, categoryKey, step);
					resolve();
				});
				optionDiv.appendChild(button);
			});
		}

		stepWrap.appendChild(optionDiv);
		document.getElementById('chat-box').appendChild(stepWrap);

		setTimeout(() => {
			stepWrap.style.opacity = '1';
			AOS.refresh();
			scrollIfNeeded(stepWrap);
		}, 10);
	});
}

function waitForElement(selector, timeout = 3000) {
	return new Promise((resolve, reject) => {
		const startTime = Date.now();

		const interval = setInterval(() => {
			const element = document.getElementById(selector);
			if (element) {
				clearInterval(interval);
				resolve(element);
			} else if (Date.now() - startTime > timeout) {
				clearInterval(interval);
				reject(new Error(`Element ${selector} not found within timeout`));
			}
		}, 100); // 100ms 간격으로 체크
	});
}

function renderAnswer(step, product, categoryKey = '') {
	let answerDiv = document.getElementById(`${step.step}-answer`);

	// final이 아닌 단계의 answer 처리
	if (step.step !== 'final') {
		if (!answerDiv) {
			answerDiv = document.createElement('div');
			answerDiv.id = `${step.step}-answer`;
			answerDiv.classList.add('non-standard-answer');
			answerDiv.style.opacity = '0'; // 초기 상태에서 투명하게 설정
			answerDiv.setAttribute('data-aos', 'fade-in'); // AOS 애니메이션 적용

			// 요소가 DOM에 추가될 때까지 기다림
			waitForElement(`${step.step}-wrap`)
				.then((wrapElement) => {
					wrapElement.appendChild(answerDiv);
					console.log(`${step.step}-wrap 요소에 answerDiv 추가 완료`);
				})
				.catch((error) => {
					console.error(error);
				});
		}

		const displayValue = getLabelByValue(step, product);
		answerDiv.innerText = `${displayValue}을(를) 선택하셨습니다.`;
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

		// low 카테고리인 경우 textarea와 파일 업로드 필드를 추가
		//if (categoryKey === 'low') {
		const additionalInfo = document.createElement('textarea');
		additionalInfo.placeholder = '추가 정보 입력';
		additionalInfo.classList.add('non-standard-textarea');
		finalWrap.appendChild(additionalInfo);

		const fileUpload = document.createElement('input');
		fileUpload.type = 'file';
		fileUpload.classList.add('non-standard-file-upload');
		finalWrap.appendChild(fileUpload);
		//}

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
		if (cartButton) {
			cartButton.addEventListener('click', () => {
				if (confirm('장바구니에 담으시겠습니까?')) {
					addToCart();
				}
			});
		}
		finalWrap.appendChild(cartButton);

		const orderButton = document.createElement('button');
		orderButton.id = 'order-btn';
		orderButton.innerText = '발주하기';
		orderButton.classList.add('non-standard-btn');
		if (orderButton) {
			orderButton.addEventListener('click', () => {
				if (confirm('발주 하시겠습니까?')) {
					addToOrder();
				}
			});
		}

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

function getLabelByValue(step, value) {
	const options = step.options || [];
	const selectedOption = options.find(option => option.value.toString() === value.toString());
	return selectedOption ? selectedOption.label : value;
}
function handleProductSelection(product, categoryKey, step) {
	console.log('selectedAnswerValue : ', selectedAnswerValue);
	if (categoryKey === 'flap' && step.step === 'product') {
		// 제품명을 가져오기 위해 `getLabelByValue` 함수 호출
		const productName = getLabelByValue(step, product);
		// 제품명에 '복합'이라는 단어가 포함되어 있는지 검사
		if (productName.includes('복합')) {
			flapProductSelection = 'complex';
		} else {
			flapProductSelection = 'notcomplex';
		}
	}
	selectedAnswerValue[step.step] = product;
	renderAnswer(step, product, categoryKey); // categoryKey 추가

	waitForElement(`${step.step}-option`)
		.then((optionDiv) => {
			optionDiv.classList.add('disabled-option');

			// 스크롤 기능 추가
			scrollIfNeeded(optionDiv);
		})
		.catch((error) => {
			console.error(`Element ${step.step}-option not found:`, error);
		});


	const nextStepKey = step.next;

	if (step.step === 'size') {
		enableModelingAndThreeDButtons();
	}

	// 제품을 선택했을 때, step이 'product'인 경우 추가 로직
	if (step.step === 'product') {
		// AJAX로 제품 정보를 조회 (Promise 사용)
		getProductDetails(product)
			.then((response) => {
				// 조회된 데이터에서 size와 color 옵션을 업데이트
				const sizes = response.productSizes.length > 0
					? response.productSizes.map(size => ({
						value: size.id,
						label: size.productSizeText
					}))
					: [{ value: 0, label: '선택 가능한 사이즈 없음' }];

				const colors = response.productColors.length > 0
					? response.productColors.map(color => ({
						value: color.id,
						label: color.productColorSubject
					}))
					: [{ value: 0, label: '선택 가능한 색상 없음' }];

				// productFlowSteps의 size 및 color 옵션 업데이트
				productFlowSteps[categoryKey].forEach(stepObj => {
					if (stepObj.step === 'size') {
						stepObj.options = sizes;
					}
					if (stepObj.step === 'color') {
						stepObj.options = colors;
					}
				});

				// 다음 단계로 이동
				return proceedToNextStep(categoryKey, nextStepKey, product);
			})
			.catch((error) => {
				console.error("제품 정보 조회 실패:", error);
				alert("제품 정보를 불러오는 데 실패했습니다.");
			});
	} else {
		// 제품 선택이 아닌 경우 바로 다음 단계로 이동
		proceedToNextStep(categoryKey, nextStepKey, product);
	}
}

// 다음 단계로 이동 처리 함수
function proceedToNextStep(categoryKey, nextStepKey, product) {
	return new Promise((resolve, reject) => {
		let nextStepIndex;

		if (typeof nextStepKey === 'function' && categoryKey === 'flap') {
			const currentSelections = {};
			currentFlow.forEach((stepKey) => {
				const answerDiv = document.getElementById(`${stepKey}-answer`);
				if (answerDiv) {
					currentSelections[stepKey] = answerDiv.innerText
						.replace('을(를) 선택하셨습니다.', '')
						.replace('[초기화]', '')
						.trim();
				}
			});
			const nextKey = nextStepKey(product, flapProductSelection);
			nextStepIndex = productFlowSteps[categoryKey].findIndex(s => s.step === nextKey);
			currentFlow.push(nextKey);
		} else {
			if (typeof nextStepKey === 'function') {
				const nextKey = nextStepKey(product);
				nextStepIndex = productFlowSteps[categoryKey].findIndex(s => s.step === nextKey);
				currentFlow.push(nextKey);
			} else {
				nextStepIndex = productFlowSteps[categoryKey].findIndex(s => s.step === nextStepKey);
				currentFlow.push(nextStepKey);
			}
		}

		if (nextStepIndex >= 0) {
			// 다음 단계로 이동하고 Promise 반환
			updateProductOptions(categoryKey, nextStepIndex)
				.then(() => resolve())
				.catch((error) => {
					console.error('옵션 업데이트 실패:', error);
					reject(error);
				});
		} else {
			// 마지막 단계 도달 시
			renderAnswer({ step: 'final' }, '', categoryKey);
			resolve();
		}
	});
}

function getCategoryKey(selectedBigSort) {
	switch (selectedBigSort) {
		case '거울':
			return 'mirror';
		case '상부장':
			return 'top';
		case '하부장':
			return 'low';
		case '플랩장':
			return 'flap';
		case '슬라이드장':
			return 'slide';
		default:
			return null; // 알 수 없는 카테고리인 경우
	}
}


function resetStep(step) {
	const answerDiv = document.getElementById(`${step}-answer`);
	if (answerDiv) {
		fadeOutElement(answerDiv);
	}

	// 선택한 단계 이후의 모든 단계 제거
	const stepsToDelete = currentFlow.slice(currentFlow.indexOf(step) + 1);
	stepsToDelete.forEach((stepToDelete) => {
		const wrapDiv = document.getElementById(`${stepToDelete}-wrap`);
		if (wrapDiv) fadeOutElement(wrapDiv);
		delete selectedAnswerValue[stepToDelete];
	});

	// 1차, 2차 카테고리 초기화
	if (step === 'category') {
		selectedBigSort = null;
		selectedMiddleSort = null;
	}

	// 2차 카테고리 초기화
	if (step === 'middleSort') {
		selectedMiddleSort = null;
	}

	// `currentFlow` 배열 초기화
	const resetIndex = currentFlow.indexOf(step);
	currentFlow = currentFlow.slice(0, resetIndex + 1);

	// 옵션 비활성화 해제
	const optionDiv = document.getElementById(`${step}-option`);
	if (optionDiv) {
		optionDiv.classList.remove('disabled-option');
	}

	// 초기화 시 `numberOfOption`과 `doorDirectionOptions` 초기화
	resetNumberOfOption();
	doorDirectionOptions = [];

	// 초기 질문 렌더링
	if (step === 'category') {
		renderInitialQuestion();
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

// 장바구니, 발주 버튼 클릭 시 localStorage에 데이터 저장 및 flow 초기화 함수
let selectedAnswerValue = {}; // 선택한 값을 저장할 객체

// 장바구니에 항목 추가 후 초기화
function addToCart() {
	const cartData = JSON.parse(localStorage.getItem('cart')) || [];
	const currentSelection = { ...selectedAnswerValue, quantity: parseInt(document.getElementById('final-quantity').value) };
	let itemExists = false;

	cartData.forEach(item => {
		const isSameProduct = Object.keys(selectedAnswerValue).every(key => item[key] === currentSelection[key]);
		if (isSameProduct) {
			item.quantity += currentSelection.quantity;
			itemExists = true;
		}
	});

	if (!itemExists) cartData.push(currentSelection);

	localStorage.setItem('cart', JSON.stringify(cartData));

	// 초기화 수행
	resetSelections();
}

// 발주하기에 항목 추가 후 초기화
function addToOrder() {
	const orderData = JSON.parse(localStorage.getItem('order')) || [];
	const currentOrder = { ...selectedAnswerValue, quantity: parseInt(document.getElementById('final-quantity').value) };

	orderData.push(currentOrder);
	localStorage.setItem('order', JSON.stringify(orderData));

	// 초기화 수행
	resetSelections();
}

// 공통 초기화 함수
function resetSelections() {
	// selectedAnswerValue와 currentFlow 초기화
	selectedAnswerValue = {};
	currentFlow = ['category'];
	document.getElementById('chat-box').innerHTML = ''; // 이전 내용 초기화
	renderInitialQuestion();
}

function postWithForm(url, data) {
	const form = document.createElement('form');
	form.method = 'POST';
	form.action = url;

	// JSON 데이터를 하나의 input으로 추가
	const input = document.createElement('input');
	input.type = 'hidden';
	input.name = 'data';
	input.value = JSON.stringify(data);
	form.appendChild(input);

	document.body.appendChild(form);
	form.submit();
}

document.getElementById('modeling-btn').addEventListener('click', () => {
	postWithForm('/modeling', selectedAnswerValue);
});

document.getElementById('three-d-btn').addEventListener('click', () => {
	postWithForm('/threed', selectedAnswerValue);
});

window.onload = () => {
	// 초기 질문을 렌더링하고 나서 autoProceed 호출
	renderInitialQuestion();

	// renderInitialQuestion이 완료된 후 autoProceed 실행
	/*setTimeout(() => {
		autoProceed(sampleDataSet);
	}, 500);*/ // 약간의 지연을 추가하여 DOM이 렌더링되는 시간을 확보
};

