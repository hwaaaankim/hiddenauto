export const initialQuestion = {
	step: {
		label: '카테고리',
		value: 'category'
	},
	question: '제품 분를 선택 해 주세요',
	options: [
		{
			label: '상부장',
			value: 'top',
			id: 1
		},
		{
			label: '하부장',
			value: 'low',
			id: 2
		},
		{
			label: '슬라이드장',
			value: 'slide',
			id: 3
		},
		{
			label: '거울',
			value: 'mirror',
			id: 4
		},
		{
			label: '플랩장',
			value: 'flap',
			id: 6
		}
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

export const productFlowSteps = {
	mirror: [
		{
			step: 'product',
			label: '제품',
			question: '거울의 제품을 선택하세요.',
			next: 'color'
		},
		{
			step: 'color',
			label: '색상',
			question: '거울의 색상을 선택하세요.',
			next: 'size'
		},
		{
			step: 'size',
			label: '사이즈',
			question: '거울의 사이즈를 선택하세요.',
			next: 'NEXT'
		},
		{
			step: 'normalled',
			label: 'LED 추가',
			question: 'LED를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'ADD' ? 'normalledcolor' : 'final' // 선택에 따라 이동
		},
		{
			step: 'normalledcolor',
			label: 'LED 색상',
			question: 'LED의 색상을 선택하세요.',
			options: [
				{ value: 'one', label: '색상01' },
				{ value: 'two', label: '색상02' },
				{ value: 'three', label: '색상03' }
			],
			next: 'final'
		}
	],
	top: [
		{
			step: 'product',
			label: '제품',
			question: '상부장의 제품을 선택하세요.',
			next: 'color'
		},
		{
			step: 'color',
			label: '색상',
			question: '상부장의 색상을 선택하세요.',
			next: 'size'
		},
		{
			step: 'size',
			label: '사이즈',
			question: '상부장의 사이즈를 선택하세요.',
			next: 'door'
		},
		{
			step: 'door',
			label: '문 추가',
			question: '문을 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'numberofdoor' : 'NEXT'
		},
		{
			step: 'numberofdoor',
			label: '문 수량',
			question: '문 수량을 선택하세요.',
			next: 'doorDirection'
		},
		{
			step: 'doorDirection',
			label: '문 방향',
			question: '문의 방향을 선택하세요.',
			next: 'NEXT'
		},
		{
			step: 'handle',
			label: '손잡이 추가',
			question: '손잡이를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: 'NEXT'
		},
		{
			step: 'led',
			label: 'LED 추가 여부',
			question: 'LED를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'ledPosition' : 'NEXT'
		},
		{
			step: 'ledPosition',
			label: 'LED 위치',
			question: 'LED의 위치를 선택하세요.',
			next: 'ledColor'
		},
		{
			step: 'ledColor',
			label: 'LED 색상',
			question: 'LED의 색상을 선택하세요.',
			options: [
				{ value: 'one', label: '색상01' },
				{ value: 'two', label: '색상02' },
				{ value: 'three', label: '색상03' }
			],
			next: 'NEXT'
		},
		{
			step: 'tissue',
			label: '휴지걸이 추가',
			question: '휴지걸이를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'tissuePosition' : 'NEXT'
		},
		{
			step: 'tissuePosition',
			label: '휴지걸이 위치',
			question: '휴지걸이 위치를 선택하세요',
			next: 'NEXT'
		},
		{
			step: 'dry',
			label: '드라이걸이 추가',
			question: '드라이걸이를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'dryPosition' : 'NEXT'
		},
		{
			step: 'dryPosition',
			label: '드라이걸이 방향',
			question: '드라이걸이의 방향을 선택하세요.',
			next: 'NEXT'
		},
		{
			step: 'outlet',
			label: '콘센트 추가',
			question: '콘센트를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'outletPosition' : 'final'
		},
		{
			step: 'outletPosition',
			label: '콘센트 방향',
			question: '콘센트의 방향을 선택하세요.',
			next: 'final'
		}
	],
	low: [
		{
			step: 'product',
			label: '제품',
			question: '하부장의 제품을 선택하세요.',
			next: 'form'
		},
		{
			step: 'form',
			label: '형태',
			question: '하부장의 형태를 선택하세요.',
			options: [
				{ value: 'leg', label: '다리형' },
				{ value: 'notleg', label: '벽걸이형' }
			],
			next: 'color'
		},
		{
			step: 'color',
			label: '색상',
			question: '하부장의 색상을 선택하세요.',
			next: 'size'
		},
		
		// 사이즈선택시 문의수량과 세면대의 수량, 문의 방향 옵션 확정됨.
		{
			step: 'size',
			label: '사이즈',
			question: '하부장의 사이즈를 선택하세요.',
			next: 'formofwash'
		},
		
		// 세면대 형태 선택 세면대와 대리석 중 하나로 선택됨.
		{
			step: 'formofwash',
			label: '세면대 형태',
			question: '세면대의 형태를 선택 해 주세요.',
			options: [
				{ value: 'under', label: '언더볼' },
				{ value: 'dogi', label: '도기매립' },
				{ value: 'marble', label: '대리석' }
			],
			next: (selectedOption) => {
		        if (selectedOption === 'under') {
		            return 'sortofunder'; // 언더볼
		        } else if (selectedOption === 'dogi') {
		            return 'sortofdogi'; // 도기매립
		        } else if (selectedOption === 'marble') {
		            return 'colorofmarble'; // 대리석
		        }
		        return null; // 기본값 (없을 경우)
		    }
		},
		{
			step: 'sortofdogi',
			label: '세면대 종류',
			question: '세면대의 종류를 선택 해 주세요.',
			options: [
				{ value: 'one', label: '타입01' },
				{ value: 'two', label: '타입02' },
				{ value: 'three', label: '타입03' }
			],
			next: 'numberofwash'
		},
		{
			step: 'sortofunder',
			label: '세면대 종류',
			question: '세면대의 종류를 선택 해 주세요.',
			options: [
				{ value: 'one', label: '타입01' },
				{ value: 'two', label: '타입02' },
				{ value: 'three', label: '제공 언더볼' }
			],
			next: 'numberofwash'
		},
		{
			step: 'numberofwash',
			label: '세면대 수량',
			question: '세면대의 수량을 선택 해 주세요.',
			next: 'positionofwash'
		},
		{
			step: 'positionofwash',
			label: '세면대 위치',
			question: '세면대의 위치를 입력 해 주세요.',
			next: 'colorofmarble'
		},
		{
			step: 'colorofmarble',
			label: '대리석 색상',
			question: '대리석의 색상을 선택하세요.',
			options: [
				{ value: 'one', label: '색상01' },
				{ value: 'two', label: '색상02' },
				{ value: 'three', label: '색상03' }
			],
			next: 'maguri'
		},
		{
			step: 'maguri',
			label: '마구리 추가여부',
			question: '마구리 추가여부를 선택하세요.',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'directionofmaguri' : ''
		},
		{
			step: 'directionofmaguri',
			label: '마구리 설치방향',
			question: '마구리 설치방향을 선택하세요.',
			options: [
				{ value: 'one', label: '전면' },
				{ value: 'two', label: '전면/좌측면' },
				{ value: 'three', label: '전면/우측면' },
				{ value: 'four', label: '전면/좌측면/우측면' },
			],
			next: 'sizeofmaguri'
		},
		{
			step: 'sizeofmaguri',
			label: '마구리 사이즈',
			question: '마구리 사이즈를 입력해주세요.(1 ~ 250mm)',
			next: 'colorofmarble'
		},
		{
			step: 'backhump',
			label: '뒷턱 추가여부',
			question: '뒷턱 추가여부를 선택하세요.',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'directionofbackhump' : ''
		},
		{
			step: 'directionofbackhump',
			label: '뒷턱 설치방향',
			question: '뒷턱 설치방향을 선택하세요.',
			options: [
				{ value: 'one', label: '후면' },
				{ value: 'two', label: '후면/좌측면' },
				{ value: 'three', label: '후면/우측면' },
				{ value: 'four', label: '후면/좌측면/우측면' },
			],
			next: 'board'
		},
		{
			step: 'board',
			label: '걸레받이 추가여부',
			question: '걸레받이 추가여부를 선택하세요.',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'directionofboard' : ''
		},
		{
			step: 'directionofboard',
			label: '걸레받이 설치방향',
			question: '걸레받이 설치방향을 선택하세요.',
			options: [
				{ value: 'one', label: '전면' },
				{ value: 'two', label: '전면/좌측면' },
				{ value: 'three', label: '전면/우측면' },
				{ value: 'four', label: '전면/좌측면/우측면' },
			],
			next: 'hole'
		},
		{
			step: 'hole',
			label: '타공 추가여부',
			question: '타공 여부를 선택하세요.',
			options: [
				{ value: 'add', label: '타공안함' },
				{ value: 'not_add', label: '타공함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'positionofhole' : ''
		},
		{
			step: 'door',
			label: '문 추가여부',
			question: '문을 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'numberofdoor' : 'NEXT'
		},
		{
			step: 'numberofdoor',
			label: '문 수량',
			question: '문의 수량을 선택하세요.',
			next: 'CHANGED'
		},
		{
			step: 'formofdoor',
			label: '문 형태',
			question: '문의 형태를 선택하세요.',
			options: [
				{ value: 'one', label: '여닫이' },
				{ value: 'two', label: '슬라이드' },
				{ value: 'three', label: '서랍식' },
				{ value: 'four', label: '혼합식' }
			],
			next: 'doorDirection'
		},
		{
			step: 'doorDirection',
			label: '문 방향',
			question: '문의 방향을 선택하세요.',
			next: 'NEXT'
		},
		{
			step: 'handle',
			label: '손잡이 추가',
			question: '손잡이를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'handletype' : 'NEXT'
		},
		{
			step: 'handletype',
			label: '손잡이 종류',
			question: '손잡이의 종류를 선택하세요.',
			options: [
				{ value: 'one', label: '손잡이01' },
				{ value: 'two', label: '손잡이02' },
				{ value: 'three', label: '손잡이03' }
			],
			next: 'handlecolor'
		},
		{
			step: 'handlecolor',
			label: '손잡이 색상',
			question: '손잡이의 색상을 선택하세요.',
			options: [
				{ value: 'one', label: '색상01' },
				{ value: 'two', label: '색상02' },
				{ value: 'three', label: '색상03' }
			],
			next: 'NEXT'
		},
		{
			step: 'led',
			label: 'LED 추가 여부',
			question: 'LED를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'ledPosition' : 'NEXT'
		},
		{
			step: 'ledPosition',
			label: 'LED 위치',
			question: 'LED의 위치를 선택하세요.',
			next: 'ledColor'
		},
		{
			step: 'ledColor',
			label: 'LED 색상',
			question: 'LED의 색상을 선택하세요.',
			options: [
				{ value: 'one', label: '색상01' },
				{ value: 'two', label: '색상02' },
				{ value: 'three', label: '색상03' }
			],
			next: 'NEXT'
		},
		{
			step: 'tissue',
			label: '휴지걸이 추가',
			question: '휴지걸이를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'tissuePosition' : 'NEXT'
		},
		{
			step: 'tissuePosition',
			label: '휴지걸이 위치',
			question: '휴지걸이 위치를 선택하세요',
			next: 'NEXT'
		},
		{
			step: 'dry',
			label: '드라이걸이 추가',
			question: '드라이걸이를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'dryPosition' : 'NEXT'
		},
		{
			step: 'dryPosition',
			label: '드라이걸이 방향',
			question: '드라이걸이의 방향을 선택하세요.',
			next: 'NEXT'
		},
		{
			step: 'outlet',
			label: '콘센트 추가',
			question: '콘센트를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'outletPosition' : 'final'
		},
		{
			step: 'outletPosition',
			label: '콘센트 방향',
			question: '콘센트의 방향을 선택하세요.',
			next: 'final'
		}
	],
	flap: [
		{
			step: 'product',
			label: '제품',
			question: '플랩장의 제품을 선택하세요.',
			next: 'color'
		},
		{
			step: 'color',
			label: '색상',
			question: '플랩장의 색상을 선택하세요.',
			next: 'size'
		},
		{
			step: 'size',
			label: '사이즈',
			question: '플랩장의 사이즈를 선택하세요.',
			next: 'door'
		},
		{
			step: 'door',
			label: '문 추가',
			question: '문을 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption, currentSelection) => {
				if (selectedOption === 'not_add') return 'NEXT';
				else if (selectedOption === 'add' && currentSelection === 'complex') return 'doorDirection';
				else return 'NEXT';
			}
		},
		{
			step: 'doorDirection',
			label: '문 방향',
			question: '문의 방향을 선택하세요.',
			options: [
				{ value: 'left', label: '좌플랩' },
				{ value: 'right', label: '우플랩' }
			],
			next: 'doorRatio'
		},
		{
			step: 'doorRatio',
			label: '문 비율',
			question: '문의 비율을 선택하세요.',
			next: 'NEXT'
		},
		{
			step: 'led',
			label: 'LED 추가 여부',
			question: 'LED를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'ledPosition' : 'NEXT'
		},
		{
			step: 'ledPosition',
			label: 'LED 위치',
			question: 'LED의 위치를 선택하세요.',
			next: 'ledColor'
		},
		{
			step: 'ledColor',
			label: 'LED 색상',
			question: 'LED의 색상을 선택하세요.',
			options: [
				{ value: 'one', label: '색상01' },
				{ value: 'two', label: '색상02' },
				{ value: 'three', label: '색상03' }
			],
			next: 'NEXT'
		},
		{
			step: 'tissue',
			label: '휴지걸이 추가',
			question: '휴지걸이를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'tissuePosition' : 'NEXT'
		},
		{
			step: 'tissuePosition',
			label: '휴지걸이 위치',
			question: '휴지걸이 위치를 선택하세요',
			next: 'NEXT'
		},
		{
			step: 'dry',
			label: '드라이걸이 추가',
			question: '드라이걸이를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'dryPosition' : 'NEXT'
		},
		{
			step: 'dryPosition',
			label: '드라이걸이 방향',
			question: '드라이걸이의 방향을 선택하세요.',
			next: 'NEXT'
		},
		{
			step: 'outlet',
			label: '콘센트 추가',
			question: '콘센트를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'outletPosition' : 'final'
		},
		{
			step: 'outletPosition',
			label: '콘센트 방향',
			question: '콘센트의 방향을 선택하세요.',
			next: 'final'
		}
	],
	slide: [
		{
			step: 'product',
			label: '제품',
			question: '슬라이드장의 제품을 선택하세요.',
			next: 'color'
		},
		{
			step: 'color',
			label: '색상',
			question: '슬라이드장의 색상을 선택하세요.',
			next: 'size'
		},
		{
			step: 'size',
			label: '사이즈',
			question: '슬라이드장의 사이즈를 선택하세요.',
			next: 'door'
		},
		{
			step: 'door',
			label: '문 추가',
			question: '문을 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: 'NEXT'
		},
		{
			step: 'handle',
			label: '손잡이 추가',
			question: '손잡이를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: 'NEXT'
		},
		{
			step: 'led',
			label: 'LED 추가 여부',
			question: 'LED를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'ledPosition' : 'NEXT'
		},
		{
			step: 'ledPosition',
			label: 'LED 위치',
			question: 'LED의 위치를 선택하세요.',
			next: 'ledColor'
		},
		{
			step: 'ledColor',
			label: 'LED 색상',
			question: 'LED의 색상을 선택하세요.',
			options: [
				{ value: 'one', label: '색상01' },
				{ value: 'two', label: '색상02' },
				{ value: 'three', label: '색상03' }
			],
			next: 'NEXT'
		},
		{
			step: 'tissue',
			label: '휴지걸이 추가',
			question: '휴지걸이를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'tissuePosition' : 'NEXT'
		},
		{
			step: 'tissuePosition',
			label: '휴지걸이 위치',
			question: '휴지걸이 위치를 선택하세요',
			next: 'NEXT'
		},
		{
			step: 'dry',
			label: '드라이걸이 추가',
			question: '드라이걸이를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'dryPosition' : 'NEXT'
		},
		{
			step: 'dryPosition',
			label: '드라이걸이 방향',
			question: '드라이걸이의 방향을 선택하세요.',
			next: 'NEXT'
		},
		{
			step: 'outlet',
			label: '콘센트 추가',
			question: '콘센트를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'outletPosition' : 'final'
		},
		{
			step: 'outletPosition',
			label: '콘센트 방향',
			question: '콘센트의 방향을 선택하세요.',
			next: 'final'
		}
	]

};