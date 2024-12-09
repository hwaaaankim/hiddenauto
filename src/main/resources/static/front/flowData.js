export const initialQuestion = {
	step: {
		label: '카테고리',
		value: 'category'
	},
	question: '1차 카테고리를 선택 해 주세요',
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
			next: 'color'
		},
		{
			step: 'color',
			label: '색상',
			question: '하부장의 색상을 선택하세요.',
			next: 'size'
		},
		{
			step: 'size',
			label: '사이즈',
			question: '하부장의 사이즈를 선택하세요.',
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
			next: 'washstand'
		},
		{
			step: 'washstand',
			label: '세면대',
			question: '세면대의 개수를 선택하세요.',
			options: [
				{ value: 'one', label: '1개' },
				{ value: 'two', label: '2개' },
				{ value: 'three', label: '3개' }
			],
			next: 'positionofwashstand'
		},
		{
			step: 'positionofwashstand',
			label: '세면대 위치',
			question: '세면대의 위치를 선택하세요.',
			options: [
				{ value: 'left', label: '좌측' },
				{ value: 'right', label: '우측' },
				{ value: 'center', label: '중앙' }
			],
			next: 'door'
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