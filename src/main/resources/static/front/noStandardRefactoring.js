import { initialQuestion, productFlowSteps } from './flowData.js';

let selectedBigSort = null; // 1차 카테고리 선택 값
let selectedMiddleSort = null; // 2차 카테고리 선택 값
let currentFlow = ['category']; // 기본적으로 category는 포함됨
let flapProductSelection = null;
let numberOfOption = [];
let washstandOptions = [];
let doorDirectionOptions = [];
let realFlow = []; // 선택된 제품에 맞는 흐름을 저장하는 변수
let lowDoorDirectionPlaceholder = ''; // Low 카테고리용 placeholder 문자열 저장 변수

// 전역 변수 선언
let preloadedData = {
	middleSort: [] // MiddleSort 데이터를 저장할 배열
};
const sampleDataSet = {
    "category": {
        "label": "하부장",
        "value": "low",
        "id": 2
    },
    "middleSort": 5,
    "product": 187,
    "color": 1,
    "size": "넓이: 3000, 높이: 1200, 깊이: 700",
    "form": "leg",
    "colorofmarble": "one",
    "washstand": "one",
    "positionofwashstand": "left",
    "door": "add",
    "numberofdoor": 4,
    "formofdoor": "one"
}

AOS.init({
	duration: 500,
	easing: 'ease-in-out',
	once: true
});
function getLowDoorDirectionPlaceholder() {
    const formofdoorOtherValue = selectedAnswerValue['formofdoor_other'];

    if (formofdoorOtherValue === 'open') {
        return '경첩의 방향을 입력 해 주세요.(좌-우-좌 등)';
    } else if (formofdoorOtherValue === 'drawer') {
        return '서랍의 방향을 입력 해 주세요.(좌 2서랍, 우 2서랍 등)';
    } else if (formofdoorOtherValue === 'mixed') {
        return '문의 방향을 입력 해 주세요.(좌 2여닫이, 우 2서랍 등)';
    }
    return '';
}

function assignModifiedNextValuesToCurrentFlow() {
    const categoryKey = selectedBigSort ? selectedBigSort.value : null;

    if (!categoryKey) {
        console.error("선택된 카테고리가 없습니다.");
        return;
    }

    let flowToModify = realFlow.length > 0 ? realFlow : productFlowSteps[categoryKey];

    if (!flowToModify || flowToModify.length === 0) {
        console.error("현재 진행 중인 flow를 찾을 수 없습니다.");
        return;
    }

    assignModifiedNextValues(flowToModify);
}

function assignModifiedNextValues(flow) {
    const middleSort = selectedAnswerValue['middleSort'];
    const form = selectedAnswerValue['form'];

    // 1. middleSort 조건에 따라 'CHANGED_BY_SERIES' 변경
    flow.forEach(step => {
        if (step.step === 'door') {
            if (typeof step.next === 'function') {
                // ✅ 기존 next 함수 내부에서 CHANGED_BY_SERIES 값을 변경하도록 래핑
                const originalNext = step.next;
                step.next = (selectedOption) => {
                    const nextValue = originalNext(selectedOption);
                    return nextValue === 'CHANGED_BY_SERIES'
                        ? (middleSort === 11 ? 'formofdoor_slide' : 'formofdoor_other')
                        : nextValue;
                };
            } else if (step.next === 'CHANGED_BY_SERIES') {
                step.next = (middleSort === 11) ? 'formofdoor_slide' : 'formofdoor_other';
            }
        }
    });

    // 2. form 값이 'leg'이면 'CHANGED_BY_FORM' 값을 'board'로 변경
    if (form === 'leg') {
        flow.forEach(step => {
            if (step.next === 'CHANGED_BY_FORM') {
                step.next = 'board';
            }
        });
    } 
    // form 값이 'notleg'이면 'board' 스텝의 next 값을 기존 'NEXT' 값으로 변경
    else if (form === 'notleg') {
        let boardNextValue = 'final'; // 기본값

        // 'board' 스텝에서 기존 NEXT 값 찾아 저장
        flow.forEach(step => {
            if (step.step === 'board' && typeof step.next === 'function') {
                boardNextValue = step.next('not_add'); // 기존 'NEXT' 값 가져옴
            }
        });

        // 'CHANGED_BY_FORM' 값 변경
        flow.forEach(step => {
            if (step.next === 'CHANGED_BY_FORM') {
                step.next = boardNextValue;
            }
        });
    }

    console.log(flow); // 변경된 flow 출력
}


function determineWashstandOptions(sizeOrWidth) {
    let width = parseInt(sizeOrWidth);
    if (width >= 1 && width < 1200) {
        washstandOptions = [1];  // 1개만 가능
    } else if (width < 1800) {
        washstandOptions = [1, 2];  // 1개 또는 2개 가능
    } else {
        washstandOptions = [1, 2, 3];  // 1개, 2개 또는 3개 가능
    }
}
function assignNextValues(filteredFlow) {
    const nextValuesQueue = []; // NEXT를 처리할 큐
    let lastFixedNextIndex = -1; // 직전 "NEXT"의 인덱스를 저장

    // 1. 고정된 NEXT 처리
    for (let i = 0; i < filteredFlow.length; i++) {
        const currentStep = filteredFlow[i];
        const nextStep = filteredFlow[i + 1] ? filteredFlow[i + 1].step : 'final';

        // NEXT 처리 (순서 유지)
        if (typeof currentStep.next === 'string' && currentStep.next === 'NEXT') {
            currentStep.next = nextStep;
            nextValuesQueue.push(nextStep); // 큐에 저장
            lastFixedNextIndex = nextValuesQueue.length - 1; // 마지막 NEXT 인덱스 저장
        }
    }

    // 2. NEXT_SAME 처리
    let nextSameIndex = 0; // NEXT_SAME이 첫 번째 등장했을 때 참조할 NEXT 인덱스

    for (let i = 0; i < filteredFlow.length; i++) {
        const currentStep = filteredFlow[i];

        if (typeof currentStep.next === 'string' && currentStep.next === 'NEXT_SAME') {
            if (nextSameIndex === 0 && lastFixedNextIndex >= 0) {
                // NEXT_SAME이 처음 등장했을 때, 직전 NEXT 값 가져오기
                nextSameIndex = lastFixedNextIndex; 
            }
            // NEXT_SAME을 가장 가까운 NEXT 값과 동일하게 설정
            currentStep.next = nextValuesQueue[nextSameIndex]; 
        }
    }

    // 3. 함수형 NEXT 처리
    for (let i = 0; i < filteredFlow.length; i++) {
        const currentStep = filteredFlow[i];

        if (typeof currentStep.next === 'function') {
            const originalNext = currentStep.next; // 원래 함수를 저장
            const calculatedNext = nextValuesQueue.shift(); // 큐에서 다음 값을 가져옴

            // 새로운 함수 생성: NEXT 값을 큐 값으로 대체
            currentStep.next = (...args) => {
                const result = originalNext(...args); // 기존 함수 실행
                return result === 'NEXT' ? calculatedNext : result; // NEXT는 큐 값으로 대체
            };
        }
    }

    // 마지막 단계의 next는 항상 'final'
    if (filteredFlow.length > 0) {
        const lastStep = filteredFlow[filteredFlow.length - 1];
        lastStep.next = 'final';
    }

    return filteredFlow;
}


function filterFlowBySign(product, templateFlow) {
	const skipKeywords = {
		normal: !product.normalLedAddSign,
		tissue: !product.tissueAddSign,
		dry: !product.dryAddSign,
		led: !product.lowLedAddSign,
		outlet: !product.outletAddSign,
		handle: !product.handleAddSign,
		mirror: !product.mirrorDirectionSign,
	};

	return templateFlow.filter(step => {
		return !Object.keys(skipKeywords).some(
			keyword => step.step.includes(keyword) && skipKeywords[keyword]
		);
	});
}

const optionMapping = {
	tissuePosition: 'productTissuePositions',
	dryPosition: 'productDryPositions',
	ledPosition: 'productLowLedPositions',
	outletPosition: 'productOutletPositions',
};

function generateRealFlow(product, templateFlow) {
	const copiedFlow = deepClone(templateFlow);
    const filteredFlow = filterFlowBySign(product, copiedFlow);

    filteredFlow.forEach(step => {
        const mappedKey = optionMapping[step.step];
        if (mappedKey && product[mappedKey]) {
            step.options = product[mappedKey].map(option => ({
                value: option.id,
                label: option.productOptionPositionText || option.productOptionAddText || option.productOptionText
            }));
        }
    });
    return assignNextValues(filteredFlow);
}


function deepClone(obj) {
    if (obj === null || typeof obj !== 'object') {
        return obj; // 기본 타입은 그대로 반환
    }

    if (obj instanceof Array) {
        const copy = [];
        for (let i = 0; i < obj.length; i++) {
            copy[i] = deepClone(obj[i]);
        }
        return copy;
    }

    if (obj instanceof Object) {
        const copy = {};
        for (const key in obj) {
            if (Object.prototype.hasOwnProperty.call(obj, key)) {
                copy[key] = deepClone(obj[key]);
            }
        }
        return copy;
    }

    throw new Error("Unable to copy object! Its type isn't supported.");
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

function handleNumberOfDoorSelection(doorCount, categoryKey) {
	if (categoryKey === 'top') {
		generateDoorDirectionOptions(doorCount); // doorCount에 따라 방향 조합 생성
	}
}

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

// size나 width에 따라 numberOfOption을 설정하는 함수
function determineNumberOfOptions(sizeOrWidth) {
	let width = parseInt(sizeOrWidth);
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
		numberOfOption = [4,5,6];
	}
}

function resetNumberOfOption() {
	numberOfOption = []; // 초기화
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

// 도면 및 3D 버튼 활성화 함수
function toggleButtonUsage(buttonId, enable) {
    const button = document.getElementById(buttonId);
    if (button) {
        if (enable) {
            button.classList.remove('notUsed'); // 활성화: notUsed 제거
        } else {
            button.classList.add('notUsed'); // 비활성화: notUsed 추가
        }
    } else {
        console.warn(`Button with ID '${buttonId}' not found.`);
    }
}

function autoProceed(savedSelections) {
	showOverlay();

	selectedBigSort = savedSelections.category;
	const categoryKey = getCategoryKey(selectedBigSort.label);

	if (!categoryKey) {
		hideOverlay();
		return;
	}

	// 1차 카테고리 처리
	handleCategorySelection(selectedBigSort);
	selectedAnswerValue['category'] = selectedBigSort;

	// 2초 대기 후 다음 코드 실행
	setTimeout(() => {
		// 2차 카테고리 처리
		if (savedSelections.middleSort) {
			handleMiddleSortSelection(savedSelections.middleSort);
			selectedAnswerValue['middleSort'] = savedSelections.middleSort;

			// 데이터 확인 및 초기화
			const selectedMiddleSortData = preloadedData.middleSort.find(
				middleSort => middleSort.id === savedSelections.middleSort
			);

			if (selectedMiddleSortData) {
				// 제품 데이터 기반으로 `realFlow` 생성
				const selectedProduct = selectedMiddleSortData.products.find(
					product => product.id === savedSelections.product
				);

				if (selectedProduct) {
					realFlow = generateRealFlow(selectedProduct, productFlowSteps[categoryKey]);
				} else {
					console.error("선택한 product 데이터를 찾을 수 없습니다.");
					hideOverlay();
					return;
				}
			} else {
				console.error("middleSort 데이터를 찾을 수 없습니다.");
				hideOverlay();
				return;
			}
		}

		// `realFlow` 또는 기본 `productFlowSteps` 가져오기
		const steps = realFlow.length > 0 ? realFlow : productFlowSteps[categoryKey];

		// 선택 저장 및 다음 단계로 진행
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
				determineNumberOfOptions(width);
				determineWashstandOptions(width); 
				document.getElementById('width-input').value = width;
				document.getElementById('height-input').value = height;
				if (depth) document.getElementById('depth-input').value = depth;

				document.querySelector(`#${currentStep.step}-option button.confirm`).click();

				// 다음 단계로 이동
				moveToNextStep(stepIndex);
				return;
			}
			if (currentStep.step === 'size' && typeof currentSelection === 'number') {
				const selectedProductId = selectedAnswerValue['product'];
				const selectedProductInfo = preloadedData.middleSort
					.flatMap(middleSort => middleSort.products)
					.find(product => product.id === selectedProductId);
			
				if (selectedProductInfo) {
					const selectedSize = selectedProductInfo.productSizes.find(size => size.id === currentSelection);
					if (selectedSize) {
						// 너비를 기반으로 문의 개수 설정
						determineNumberOfOptions(selectedSize.productWidth);
					}
				}
				handleProductSelection(currentSelection, categoryKey, currentStep);
				moveToNextStep(stepIndex);
				return;
			}

			// **2. 문의 방향 입력 처리**
			if (currentStep.step === 'doorDirection' && currentSelection && categoryKey === 'top') {
				const directionInput = document.getElementById('door-direction-input');
				directionInput.value = currentSelection;
				document.querySelector(`#${currentStep.step}-option button.confirm`).click();

				// 다음 단계로 이동
				moveToNextStep(stepIndex);
				return;
			}

			if (currentStep.step === 'doorRatio' && currentSelection) {
				const [value1, value2] = currentSelection.split(':').map(Number);
				document.getElementById('door-ratio-input-1').value = value1;
				document.getElementById('door-ratio-input-2').value = value2;
				document.querySelector(`#${currentStep.step}-option button.confirm`).click();
				moveToNextStep(stepIndex);
				return;
			}

			// **3. Product 단계 비동기 처리**
			if (currentStep.step === 'product') {
				const selectedMiddleSort = preloadedData.middleSort.find(
					middleSort => middleSort.id === savedSelections.middleSort
				);

				if (!selectedMiddleSort) {
					console.error('middleSort 데이터를 찾을 수 없습니다.');
					hideOverlay();
					return;
				}

				const selectedProduct = selectedMiddleSort.products.find(
					product => product.id === currentSelection
				);

				if (!selectedProduct) {
					console.error('선택한 product 데이터를 찾을 수 없습니다.');
					hideOverlay();
					return;
				}

				handleProductSelection(currentSelection, categoryKey, currentStep);

				// product와 관련된 다음 단계 동적으로 설정
				const sizes = selectedProduct.productSizes?.length > 0
					? selectedProduct.productSizes.map(size => ({
						value: size.id,
						label: size.productSizeText,
					}))
					: [{ value: 0, label: '선택 가능한 사이즈 없음' }];

				const colors = selectedProduct.productColors?.length > 0
					? selectedProduct.productColors.map(color => ({
						value: color.id,
						label: color.productColorSubject,
					}))
					: [{ value: 0, label: '선택 가능한 색상 없음' }];

				productFlowSteps[categoryKey].forEach(stepObj => {
					if (stepObj.step === 'size') {
						stepObj.options = sizes;
					}
					if (stepObj.step === 'color') {
						stepObj.options = colors;
					}
				});

				stepIndex++;
				setTimeout(() => proceedWithSelections(stepIndex), 500);
				return;
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

		// 최초 단계 실행
		setTimeout(() => proceedWithSelections(0), 1000);
	}, 3000); // 1차 카테고리 처리 후 2초 대기
}

function parseSizeText(sizeText) {
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

function updateNextValue(flowSteps, targetValue, newValue) {
    flowSteps.forEach(step => {
        if (step.next === targetValue) {
            step.next = newValue; // next 값을 변경
        }
    });
}

function changeLowProcess(width) {
    if (width > 700) {
        // width가 700 초과일 때는 기존 로직 유지
        updateNextValue(realFlow, 'CHANGED', 'formofdoor');
    } else {
        // width가 700 이하일 때, formofdoor 스텝의 next 값을 찾아서 CHANGED에 할당
        let formofdoorNextValue = 'final'; // 기본값: final
        realFlow.forEach(step => {
            if (step.step === 'formofdoor' && step.next) {
                formofdoorNextValue = step.next; // formofdoor의 next 값을 가져옴
            }
        });

        // CHANGED 값을 formofdoor의 next 값으로 업데이트
        updateNextValue(realFlow, 'CHANGED', formofdoorNextValue);
    }
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
	            label: product.name,
	            productRepImageRoad: product.productRepImageRoad
	        }));
	    }
	});
	// 제품 선택 단계로 이동
	updateProductOptions(categoryKey, 0);
}


function validateDoorDirectionInput(inputValue, numberOfDoors) {
	// 1. 입력값을 분리 (예: 좌-우-좌 → ['좌', '우', '좌'])
	const directions = inputValue.split('-');

	// 2. 길이가 문의 숫자와 같은지 확인
	if (directions.length !== numberOfDoors) {
		return {
			isValid: false,
			message: `문의 방향 입력 값은 정확히 ${numberOfDoors}개여야 합니다. (예: 좌-우-좌)`
		};
	}

	// 3. 각 요소가 '좌' 또는 '우'인지 확인
	const isValidDirections = directions.every(direction => direction === '좌' || direction === '우');
	if (!isValidDirections) {
		return {
			isValid: false,
			message: '문의 방향 입력 값은 "좌" 또는 "우"만 사용할 수 있습니다. (예: 좌-우-좌)'
		};
	}

	// 4. 모든 검증 통과
	return {
		isValid: true,
		message: ''
	};
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
	
	// renderAnswer 호출
	renderAnswer({ step: 'middleSort' }, selectedMiddleSortData.name, categoryKey);
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
	questionDiv.innerText = '시리즈를 선택 해 주세요:';
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

function handleCategorySelection(category) {
	showLoader();
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
		const selectedProductId = selectedAnswerValue['product'];
		const selectedProductInfo = preloadedData.middleSort
			.flatMap(middleSort => middleSort.products)
			.find(product => product.id === selectedProductId);

		// sizeChangeSign 확인
		if (!selectedProductInfo.sizeChangeSign) {
			alert('이 제품은 사이즈 변경이 불가능합니다.');
			return;
		}
		
		
		// width, height, depth 값 가져오기
		const width = document.getElementById('width-input').value || selectedProductInfo.productSizes[0].productWidth;
		const height = document.getElementById('height-input').value || selectedProductInfo.productSizes[0].productHeight;
		const depth = categoryKey !== 'mirror'
			? document.getElementById('depth-input').value || selectedProductInfo.productSizes[0].productDepth
			: null;

		// selectedAnswerValue에 저장
		const sizeText = `넓이: ${width}, 높이: ${height}${categoryKey !== 'mirror' ? `, 깊이: ${depth}` : ''}`;
		selectedAnswerValue[step.step] = sizeText;

		toggleButtonUsage('modeling-btn', true);
		toggleButtonUsage('three-d-btn', true);
		if (categoryKey === 'low') {
            changeLowProcess(width); // 원하는 기능 추가
        }

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
		const flow = realFlow.length > 0 ? realFlow : productFlowSteps[categoryKey]; // realFlow 우선
		const step = flow[stepIndex];

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

		const selectedProductId = selectedAnswerValue['product'];
		const selectedProductInfo = preloadedData.middleSort
			.flatMap(middleSort => middleSort.products)
			.find(product => product.id === selectedProductId);

		if(step.step === 'form' && selectedAnswerValue.category.value === 'low'){
            step.options.forEach(option => {
		        const button = document.createElement('button');
		        button.innerText = option.label;
		        button.classList.add('non-standard-btn');
		
		        // ✅ 기존 if 문 유지 + 추가 로직 포함
		        button.addEventListener('click', () => {
		            handleProductSelection(option.value, categoryKey, step);
		
		            // ✅ 조건이 만족되면 assignModifiedNextValues 실행
		            assignModifiedNextValuesToCurrentFlow();
		            resolve();
		        });
		
		        optionDiv.appendChild(button);
		    });
        }
		// 유저가 선택한 제품 정보 콘솔 출력
		else if (step.step === 'size') {
			// sizeChangeSign 체크 (추가)
			if (selectedProductInfo.sizeChangeSign) {

				// 제한값 및 기본값 설정 (추가)
				const limits = {
				    widthMin: selectedProductInfo.widthMinLimit,
				    widthMax: selectedProductInfo.widthMaxLimit,
				    heightMin: selectedProductInfo.heightMinLimit,
				    heightMax: selectedProductInfo.heightMaxLimit,
				    depthMin: selectedProductInfo.depthMinLimit,
				    depthMax: selectedProductInfo.depthMaxLimit,
				};
				
				// 기본 사이즈를 새로운 `basicWidth`, `basicHeight`, `basicDepth` 필드에서 가져옴
				const defaultSize = {
				    width: selectedProductInfo.basicWidth,
				    height: selectedProductInfo.basicHeight,
				    depth: selectedProductInfo.basicDepth
				};
				
				// 거울(mirror) 카테고리는 width, height만 사용
				const fields = categoryKey === 'mirror' ? ['width', 'height'] : ['width', 'height', 'depth'];
				
				fields.forEach(field => {
				    const label = document.createElement('label');
				    label.innerHTML = `${field.charAt(0).toUpperCase() + field.slice(1)}: `;
				
				    const input = document.createElement('input');
				    input.type = 'number';
				    input.id = `${field}-input`;
				    input.classList.add('non-standard-input');
				
				    // 제한값 설정
				    if (limits[`${field}Min`] !== null) input.min = limits[`${field}Min`];
				    if (limits[`${field}Max`] !== null) input.max = limits[`${field}Max`];
				
				    // 기본값을 `basicWidth`, `basicHeight`, `basicDepth`에서 가져옴
				    input.value = defaultSize[field];
				
				    // 제한값이 0 또는 null이면 readonly 처리
				    if (limits[`${field}Min`] === 0 || limits[`${field}Max`] === 0 || 
				        limits[`${field}Min`] === null || limits[`${field}Max`] === null) {
				        input.readOnly = true;
				    }
				
				    // 값 변경 이벤트 (최소/최대 값 검토 + A/S 불가능 경고 추가)
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
				
				        // ✅ A/S 불가능 조건 추가
				        const categoryKey = selectedBigSort ? selectedBigSort.value : null;
				        const width = parseInt(document.getElementById('width-input').value);
				        const height = parseInt(document.getElementById('height-input').value);
				
				        if (categoryKey === 'top' && (width >= 1800 || height >= 1000)) {
				            alert('넓이 1,800(mm) 이상 또는 높이 1,000(mm) 이상인 경우 A/S가 불가능 합니다.');
				        }
				        if (categoryKey === 'slide' && (width >= 1800 || height >= 1000)) {
				            alert('넓이 1,800(mm) 이상 또는 높이 1,000(mm) 이상인 경우 A/S가 불가능 합니다.');
				        }
				        if (categoryKey === 'flap' && (width >= 1500 || height >= 600)) {
				            alert('1도어 기준 넓이 1,500(mm) 이상 또는 높이 600(mm) 이상인 경우 A/S가 불가능 합니다.');
				        }
				    });
				
				    label.appendChild(input);
				    optionDiv.appendChild(label);
				});

				// 확인 버튼 추가 (기존 코드 유지)
				const confirmButton = document.createElement('button');
				confirmButton.innerText = '확인';
				confirmButton.classList.add('non-standard-btn', 'confirm');
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
					if (categoryKey === 'low') {
						determineWashstandOptions(width);
					}
					const sizeText = `넓이: ${width}, 높이: ${height}${categoryKey !== 'mirror' ? `, 깊이: ${depth}` : ''}`;
					handleDirectInput(sizeText, categoryKey, step);
					resolve();
				});
				optionDiv.appendChild(confirmButton);
			}
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
		} else if (step.step === 'sizeofmaguri' && selectedAnswerValue.category.value === 'low') {
		    // label과 input 필드 추가
		    const label = document.createElement('label');
		    label.innerHTML = '마구리 사이즈: ';
		
		    const input = document.createElement('input');
		    input.type = 'number';
		    input.id = 'sizeofmaguri-input';
		    input.classList.add('non-standard-input');
		    input.placeholder = '1 ~ 250';
		    input.min = 1;
		    input.max = 250;
		    input.required = true;
		
		    // 확인 버튼 추가
		    const confirmButton = document.createElement('button');
		    confirmButton.innerText = '확인';
		    confirmButton.classList.add('non-standard-btn', 'confirm');
		
		    confirmButton.addEventListener('click', () => {
		        const maguriSize = parseInt(input.value, 10);
		
		        if (isNaN(maguriSize) || maguriSize < 1 || maguriSize > 250) {
		            alert('마구리 사이즈를 입력해 주세요.');
		            return;
		        }
		
		        handleProductSelection(maguriSize, categoryKey, step);
		    });
		
		    // label과 input을 함께 추가
		    label.appendChild(input);
		    optionDiv.appendChild(label);
		    optionDiv.appendChild(confirmButton);
		}else if (step.step === 'formofdoor_other' && selectedAnswerValue.category.value === 'low') {
		    step.options.forEach(option => {
		        const button = document.createElement('button');
		        button.innerText = option.label;
		        button.classList.add('non-standard-btn');
		
		        button.addEventListener('click', () => {
		            handleProductSelection(option.value, categoryKey, step);
		            
		            // 선택 후 lowDoorDirectionPlaceholder 업데이트
		            lowDoorDirectionPlaceholder = getLowDoorDirectionPlaceholder();
		        });
		        optionDiv.appendChild(button);
		    });
		}
		else if (step.step === 'doorDirection' && selectedAnswerValue.category.value === 'low') {
		     // placeholder 업데이트
    		lowDoorDirectionPlaceholder = getLowDoorDirectionPlaceholder();
		    
		    // label과 input 필드 추가
		    const label = document.createElement('label');
		    label.innerHTML = '문의 방향: ';
		
		    const input = document.createElement('input');
		    input.type = 'text';
		    input.id = 'doorDirection-input';
		    input.classList.add('non-standard-input');
		    input.placeholder = lowDoorDirectionPlaceholder;
		
		    // 확인 버튼 추가
		    const confirmButton = document.createElement('button');
		    confirmButton.innerText = '확인';
		    confirmButton.classList.add('non-standard-btn', 'confirm');
		
		    confirmButton.addEventListener('click', () => {
		        const doorDirection = input.value.trim();
		
		        if (!doorDirection) {
		            alert('경첩 방향을 입력해 주세요.');
		            return;
		        }
		
		        handleProductSelection(doorDirection, categoryKey, step);
		    });
		
		    // label과 input을 함께 추가
		    label.appendChild(input);
		    optionDiv.appendChild(label);
		    optionDiv.appendChild(confirmButton);
		}
		else if (step.step === 'numberofwash' && washstandOptions.length > 0) {
		    washstandOptions.forEach(option => {
		        const button = document.createElement('button');
		        button.innerText = `${option}개`;
		        button.classList.add('non-standard-btn');
		        button.addEventListener('click', () => {
		            handleProductSelection(option, categoryKey, step);
		            resolve();
		        });
		        optionDiv.appendChild(button);
		    });
		} else if (step.step === 'positionofwash') {
		    const numberOfWash = selectedAnswerValue['numberofwash']; // 사용자가 선택한 세면대 개수
		    const placeholderText = `${numberOfWash}개의 세면대 위치를 입력 해 주세요.`; // placeholder 설정
		
		    // 🔹 label 생성
		    const label = document.createElement('label');
		    label.innerHTML = '세면대 위치: ';
		
		    // 🔹 input 생성
		    const input = document.createElement('input');
		    input.type = 'text';
		    input.id = 'positionofwash-input';
		    input.classList.add('non-standard-input');
		    input.placeholder = placeholderText; // placeholder 설정
		
		    // 🔹 확인 버튼 생성
		    const confirmButton = document.createElement('button');
		    confirmButton.innerText = '확인';
		    confirmButton.classList.add('non-standard-btn', 'confirm');
		    
		    confirmButton.addEventListener('click', () => {
		        const inputValue = input.value.trim();
		        if (!inputValue) {
		            alert('세면대 위치를 입력 해 주세요.');
		            return;
		        }
		
		        // 선택한 값 저장
		        selectedAnswerValue[step.step] = inputValue;
		
		        // 다음 단계로 이동
		        proceedToNextStep(categoryKey, step.next, inputValue);
		    });
		
		    // label에 input 추가
		    label.appendChild(input);
		    optionDiv.appendChild(label);
		    optionDiv.appendChild(confirmButton);
		} else if (step.step === 'doorDirection' && categoryKey === 'top') {
			const numberOfDoors = selectedAnswerValue['numberofdoor']; // 선택한 문의 수 가져오기

			// label과 input 필드 추가
			const label = document.createElement('label');
			label.innerHTML = '경첩 방향: ';

			const directionInput = document.createElement('input');
			directionInput.type = 'text';
			directionInput.id = 'door-direction-input';
			directionInput.classList.add('non-standard-input'); // 디자인 클래스 추가
			directionInput.required = true;
			directionInput.placeholder = `경첩 방향을 입력 해 주세요 (예: 좌-우-좌, 문의 수: ${numberOfDoors})`;

			// 입력 값 검증
			directionInput.addEventListener('change', () => {
				const validationResult = validateDoorDirectionInput(directionInput.value.trim(), numberOfDoors);

				if (!validationResult.isValid) {
					alert(validationResult.message); // 검증 실패 메시지 출력
					directionInput.value = ''; // 올바르지 않은 입력일 경우 초기화
				}
			});

			// 확인 버튼 추가
			const confirmButton = document.createElement('button');
			confirmButton.innerText = '확인';
			confirmButton.classList.add('non-standard-btn', 'confirm');
			confirmButton.addEventListener('click', () => {
				const directionValue = directionInput.value.trim();

				if (!directionValue) {
					alert('경첩 방향을 입력 해 주세요.');
					return;
				}

				// 최종 검증
				const validationResult = validateDoorDirectionInput(directionValue, numberOfDoors);
				if (!validationResult.isValid) {
					alert(validationResult.message); // 검증 실패 메시지 출력
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
			confirmButton.classList.add('non-standard-btn', 'confirm');

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
					const sizeValue = selectedAnswerValue['size'];
					let width, height, depth;
					if (typeof sizeValue === 'string' && sizeValue.includes('넓이')) {
						[width, height, depth] = parseSizeText(sizeValue);
					} else {
						size = selectedProductInfo.productSizes.find(size => size.id === sizeValue);
						width = size.productWidth;
					}
					// width 값이 유효한지 검사
					if (!width) {
						alert('사이즈 데이터에서 넓이 값을 가져오지 못했습니다.');
						return;
					}

					// 입력된 값의 합이 width와 동일한지 검증
					if (value1 + value2 !== parseInt(width, 10)) {
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
		} else if (step.step === 'product') {
			// product 단계에 이미지를 추가하는 부분
			step.options.forEach(option => {
				const button = document.createElement('button');
				button.classList.add('non-standard-btn', 'product-option-btn');

				// 이미지 추가
				if (option.productRepImageRoad) {
					const img = document.createElement('img');
					img.src = option.productRepImageRoad;
					img.alt = option.label;
					button.appendChild(img);
				}

				// 텍스트 추가
				const span = document.createElement('span');
			   span.innerHTML = option.label.split(' ').join('<br>');
				button.appendChild(span);

				// 클릭 이벤트
				button.addEventListener('click', () => {
					handleProductSelection(option.value, categoryKey, step);
					resolve();
				});
				optionDiv.appendChild(button);
			});
		} else {
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
		}, 200); // 100ms 간격으로 체크
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
				})
				.catch((error) => {
					console.error(error);
				});
		}

		const displayValue = getLabelByValue(step, product);
		answerDiv.innerText = `${displayValue}을(를) 선택하셨습니다.`;
		// 초기화 버튼 추가
		const resetButton = document.createElement('button');
		resetButton.innerText = '초기화';
		resetButton.classList.add('non-standard-btn', 'non-answer-btn');
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
	if (categoryKey === 'flap' && step.step === 'product') {
		let productId = product;
		const selectedProductInfo = preloadedData.middleSort
			.flatMap(middleSort => middleSort.products)
			.find(product => product.id === productId);
		
		if (selectedProductInfo.doorRatioSign) {
			flapProductSelection = 'complex';
		} else {
			flapProductSelection = 'notcomplex';
		}
	}
	
	selectedAnswerValue[step.step] = product;
	renderAnswer(step, product, categoryKey);

	waitForElement(`${step.step}-option`)
		.then((optionDiv) => {
			optionDiv.classList.add('disabled-option');
			// 스크롤 기능 추가
			scrollIfNeeded(optionDiv);
		})
		.catch((error) => {
			console.error(`Element ${step.step}-option not found:`, error);
		});

	if (step.step === 'size') {
		toggleButtonUsage('modeling-btn', true); // modeling-btn 활성화
		toggleButtonUsage('three-d-btn', true); 
		if(categoryKey === 'low'){
			const selectedMiddleSort = preloadedData.middleSort.find(
		        middleSort => middleSort.id === selectedAnswerValue['middleSort']
		    );
		
		    const selectedProduct = selectedMiddleSort.products.find(p => p.id === selectedAnswerValue['product']);
		    if (!selectedProduct) {
		        console.error("선택한 product 데이터를 찾을 수 없습니다.");
		        return;
		    }
			const productSize= selectedProduct.productSizes.find(size => size.id === selectedAnswerValue['size']);
			const width = productSize.productWidth;
			changeLowProcess(width);	
		}
	}

	if (step.step === 'product') {
		
		// preloadedData에서 product 정보를 가져오기
		const selectedMiddleSort = preloadedData.middleSort.find(
			middleSort => middleSort.id === selectedAnswerValue['middleSort']
		);
		if (!selectedMiddleSort) {
			console.error("middleSort 데이터가 없습니다.");
			alert("제품 데이터를 가져오는 데 실패했습니다.");
			return;
		}

		const selectedProduct = selectedMiddleSort.products.find(p => p.id === product);
		if (!selectedProduct) {
			console.error("선택한 product 데이터를 찾을 수 없습니다.");
			alert("제품 데이터를 가져오는 데 실패했습니다.");
			return;
		}

		const colors = selectedProduct.productColors?.length > 0
			? selectedProduct.productColors.map(color => ({
				value: color.id,
				label: color.productColorSubject
			}))
			: [{ value: 0, label: '선택 가능한 색상 없음' }];

		productFlowSteps[categoryKey].forEach(stepObj => {
			if (stepObj.step === 'color') {
				stepObj.options = colors;
			}
		});

		// **추가 부분: realFlow 생성 및 초기화**
		realFlow = generateRealFlow(selectedProduct, productFlowSteps[categoryKey]);
		// 다음 단계로 이동
		proceedToNextStep(categoryKey, realFlow[0].next, product); // realFlow 사용
	} else {
		// 기존 흐름 유지
		proceedToNextStep(categoryKey, step.next, product);
	}
}

// 다음 단계로 이동 처리 함수
function proceedToNextStep(categoryKey, nextStepKey, product) {
	return new Promise((resolve, reject) => {
		const flow = realFlow.length > 0 ? realFlow : productFlowSteps[categoryKey]; // realFlow 우선
		let nextStepIndex;

		if (typeof nextStepKey === 'function' && categoryKey === 'flap') {
			// 플랩 카테고리 처리 (기존 로직 유지)
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
			nextStepIndex = flow.findIndex(s => s.step === nextKey); // realFlow 사용
			currentFlow.push(nextKey);

		} else if (typeof nextStepKey === 'function' && categoryKey === 'mirror' && currentFlow[currentFlow.length - 1] === 'size') {
			// 거울 카테고리의 특수 조건 처리
			const middleSortId = selectedAnswerValue['middleSort'];
			const productId = selectedAnswerValue['product'];

			const selectedMiddleSort = preloadedData.middleSort.find(
				middleSort => middleSort.id === middleSortId
			);

			if (!selectedMiddleSort) {
				console.error("middleSort 데이터를 찾을 수 없습니다.");
				reject(new Error("middleSort 데이터가 없습니다."));
				return;
			}

			const selectedProduct = selectedMiddleSort.products.find(p => p.id === productId);

			if (!selectedProduct) {
				console.error("선택한 product 데이터를 찾을 수 없습니다.");
				reject(new Error("product 데이터를 찾을 수 없습니다."));
				return;
			}

			const normalLedSign = selectedProduct.normalLedSign;

			if (normalLedSign === undefined) {
				console.error('normalLedSign 값이 없습니다.');
				reject(new Error('normalLedSign 값을 찾을 수 없습니다.'));
				return;
			}

			const nextKey = nextStepKey(normalLedSign);
			nextStepIndex = flow.findIndex(s => s.step === nextKey); // realFlow 사용
			currentFlow.push(nextKey);

		} else {
			// 일반적인 경우
			if (typeof nextStepKey === 'function') {
				const nextKey = nextStepKey(product);
				nextStepIndex = flow.findIndex(s => s.step === nextKey); // realFlow 사용
				currentFlow.push(nextKey);
			} else {
				nextStepIndex = flow.findIndex(s => s.step === nextStepKey); // realFlow 사용
				currentFlow.push(nextStepKey);
			}
		}

		if (nextStepIndex >= 0) {
			// 다음 단계로 이동
			updateProductOptions(categoryKey, nextStepIndex)
				.then(() => resolve())
				.catch((error) => {
					console.error('옵션 업데이트 실패:', error);
					reject(error);
				});
		} else {
			// 마지막 단계 도달
			renderAnswer({ step: 'final' }, '', categoryKey);
			currentFlow.push('final');
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

	// 초기화 조건 추가: size 또는 size 이전 단계일 때만 초기화
	const sizeIndex = currentFlow.indexOf('size');
	const stepIndex = currentFlow.indexOf(step);
	if (stepIndex <= sizeIndex) {
		resetNumberOfOption(); // numberOfOption 초기화
		doorDirectionOptions = []; // doorDirectionOptions 초기화
	    toggleButtonUsage('modeling-btn', false); // 비활성화
        toggleButtonUsage('three-d-btn', false); // 비활성화
	}
	
	// 선택한 단계 이후의 모든 단계 제거
	const stepsToDelete = currentFlow.slice(currentFlow.indexOf(step) + 1);
	stepsToDelete.forEach((stepToDelete) => {
		const wrapDiv = document.getElementById(`${stepToDelete}-wrap`);
		if (wrapDiv) fadeOutElement(wrapDiv);
		delete selectedAnswerValue[stepToDelete];
	});

	if (step === 'product') {
		realFlow = []; // 제품 단계에서 realFlow 초기화
	}
	
	// 1차, 2차 카테고리 초기화
	if (step === 'category') {
		realFlow = [];
		selectedBigSort = null;
		selectedMiddleSort = null;
	}

	// 2차 카테고리 초기화
	if (step === 'middleSort') {
		realFlow = [];
		selectedMiddleSort = null;
	}
	const productIndex = currentFlow.indexOf('product');
    if (productIndex !== -1 && stepIndex > productIndex && stepIndex <= sizeIndex) {
        const selectedMiddleSort = preloadedData.middleSort.find(
            middleSort => middleSort.id === selectedAnswerValue['middleSort']
        );

        if (selectedMiddleSort) {
            const selectedProduct = selectedMiddleSort.products.find(
                product => product.id === selectedAnswerValue['product']
            );
            if (selectedProduct) {
                realFlow = generateRealFlow(selectedProduct, productFlowSteps[selectedAnswerValue['category'].value]);
            } else {
                console.error("선택된 product 데이터를 찾을 수 없습니다.");
            }
        } else {
            console.error("middleSort 데이터를 찾을 수 없습니다.");
        }
    }
	// `currentFlow` 배열 초기화
	const resetIndex = currentFlow.indexOf(step);
	currentFlow = currentFlow.slice(0, resetIndex + 1);

	// 옵션 비활성화 해제
	const optionDiv = document.getElementById(`${step}-option`);
	if (optionDiv) {
		optionDiv.classList.remove('disabled-option');
	}
	
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
	form.target = '_blank'; // 새 탭에서 열리도록 설정

	// JSON 데이터를 하나의 input으로 추가
	const input = document.createElement('input');
	input.type = 'hidden';
	input.name = 'data';
	input.value = JSON.stringify(data);
	form.appendChild(input);

	document.body.appendChild(form);
	form.submit();
	form.remove(); // 폼을 제출한 후 DOM에서 제거 (청결 유지)
}


document.getElementById('modeling-btn').addEventListener('click', () => {
	postWithForm('/modeling', selectedAnswerValue);
});

document.getElementById('three-d-btn').addEventListener('click', () => {
	postWithForm('/blueprint', selectedAnswerValue);
});

window.onload = () => {
	// 초기 질문을 렌더링하고 나서 autoProceed 호출
	renderInitialQuestion();

	// renderInitialQuestion이 완료된 후 autoProceed 실행
	//setTimeout(() => {
	//	autoProceed(sampleDataSet);
	//}, 500);  // 약간의 지연을 추가하여 DOM이 렌더링되는 시간을 확보
};

