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
				{ value: 'ADD', label: '추가' },
				{ value: 'NOT_ADD', label: '추가 안 함' }
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
				{ value: 'ADD', label: '추가' }, 
				{ value: 'NOT_ADD', label: '추가 안 함' }], 
			next: 'NEXT' 
		},
		{ 
			step: 'led', 
			label: 'LED 추가 여부', 
			question: 'LED를 추가하시겠습니까?', 
			options: [
				{ value: 'ADD', label: '추가' }, 
				{ value: 'NOT_ADD', label: '추가 안 함' }], 
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
			next: 'NEXT' 
		},
		{ 
			step: 'tissue', 
			label: '휴지걸이 추가', 
			question: '휴지걸이를 추가하시겠습니까?', 
			options: [
				{ value: 'ADD', label: '추가' }, 
				{ value: 'NOT_ADD', label: '추가 안 함' }], 
			next: (selectedOption) => selectedOption === 'add' ? 'tissueDirection' : 'NEXT' 
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
				{ value: 'ADD', label: '추가' }, 
				{ value: 'NOT_ADD', label: '추가 안 함' }], 
			next: (selectedOption) => selectedOption === 'add' ? 'drydirection' : 'NEXT' 
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
				{ value: 'ADD', label: '추가' }, 
				{ value: 'NOT_ADD', label: '추가 안 함' }
				], 
			next: (selectedOption) => selectedOption === 'add' ? 'outletdirection' : 'final' 
		},
		{ 
			step: 'outletPosition', 
			label: '콘센트 방향', 
			question: '콘센트의 방향을 선택하세요.', 
			next: 'final' 
		}
	],