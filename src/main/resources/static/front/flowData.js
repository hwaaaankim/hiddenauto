export const initialQuestion = {
	step: {
		label: '카테고리',
		value: 'category'
	},
	question: '제품 분류를 선택 해 주세요',
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
			id: 5
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
			question: '거울의 사이즈를 입력 해 주세요.',
			next: 'type'
		},
		{
			step: 'type',
			label: '설치 방향',
			question: '거울의 설치 방식을 선택 해 주세요.',
			options: [
				{ value: 'vertical', label: '세로형' },
				{ value: 'horizontal', label: '가로형' }
			],
			next: 'NEXT'
		},
		{
			step: 'normal',
			label: 'LED 추가',
			question: 'LED를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'normalpower' : 'final' // 선택에 따라 이동
		},
		{
			step: 'normalpower',
			label: '전원 방식 선택',
			question: '전원 방식을 선택 해 주세요.',
			options: [
				{ value: 'touch_three', label: '터치식 3컬러 변환' },
				{ value: 'direct_one', label: '직결식 단컬러' },
				{ value: 'touch_one', label: '터치식 단컬러' }
			],
			next: (selectedOption) => {
			    console.log('selectedOption:', selectedOption);
			    return ['direct_one', 'touch_one'].includes(selectedOption) ? 'normalcolor' : 'final';
			}
		},
		{
			step: 'normalcolor',
			label: 'LED 색상',
			question: 'LED의 색상을 선택하세요.',
			options: [
				{ value: 'one', label: '3000K(전구색/주황색)' },
				{ value: 'two', label: '4000K(주백색)' },
				{ value: 'three', label: '5700K(주광색/백색)' }
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
			question: '상부장의 사이즈를 입력하세요.',
			next: 'door'
		},
		{
			step: 'door',
			label: '문 추가',
			question: '문을 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함(바디만)' }
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
			question: '문의 방향을 입력 해 주세요.',
			next: 'NEXT'
		},
		{
			step: 'mirrorDirection',
			label: '거울방향',
			question: '좌측경 또는 우측경을 선택 해 주세요.',
			options: [
				{ value: 'left', label: '좌측경' },
				{ value: 'right', label: '우측경' }
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
			question: '손잡이 종류를 선택 해 주세요.',
			options: [
				{ value: 'dolche', label: '히든 돌체 손잡이' },
				{ value: 'd195', label: '히든 D형(195) 손잡이' },
				{ value: 'half', label: '히든 하프 손잡이' },
				{ value: 'circle', label: '원형 손잡이' },
				{ value: 'd310', label: '히든 D형(310) 손잡이' }
			],
			next: (selectedOption) =>
				selectedOption === 'dolche' ? 'handle_dolche_color' :
				selectedOption === 'd195' ? 'handle_d195_color' :
				selectedOption === 'half' ? 'handle_half_color' :
				selectedOption === 'circle' ? 'handle_circle_color' : 
				'handle_d310_color'
		},
		{
			step: 'handle_dolche_color',
			label: '손잡의 색상',
			question: '손잡이 색상을 선택 해 주세요.',
			options: [
				{ value: '크롬', label: '크롬' },
				{ value: '니켈', label: '니켈' },
				{ value: '골드', label: '골드' }
			],
			next: 'NEXT_SAME'
		},
		{
			step: 'handle_d195_color',
			label: '손잡의 색상',
			question: '손잡이 색상을 선택 해 주세요.',
			options: [
				{ value: '크롬', label: '크롬' },
				{ value: '니켈', label: '니켈' },
				{ value: '골드', label: '골드' }
			],
			next: 'NEXT_SAME'
		},
		{
			step: 'handle_half_color',
			label: '손잡의 색상',
			question: '손잡이 색상을 선택 해 주세요.',
			options: [
				{ value: '화이트', label: '화이트' },
				{ value: '크림', label: '크림' },
				{ value: '그레이', label: '그레이' },
				{ value: '블랙', label: '블랙' }
			],
			next: 'NEXT_SAME'
		},
		{
			step: 'handle_circle_color',
			label: '손잡의 색상',
			question: '손잡이 색상을 선택 해 주세요.',
			options: [
				{ value: '실버', label: '실버' },
				{ value: '골드', label: '골드' }
			],
			next: 'NEXT_SAME'
		},
		{
			step: 'handle_d310_color',
			label: '손잡의 색상',
			question: '손잡이 색상을 선택 해 주세요.',
			options: [
				{ value: '크롬', label: '크롬' },
				{ value: '니켈', label: '니켈' },
				{ value: '골드', label: '골드' }
			],
			next: 'NEXT_SAME'
		},
		{
			step: 'led',
			label: 'LED 추가 여부',
			question: 'LED를 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가안함(조명공간없음)' },
				{ value: 'space', label: '추가안함(조명공간추가)' }
			],
			next: (selectedOption) => {
			    return ['not_add', 'space'].includes(selectedOption) ? 'NEXT' : 'ledPosition';
			}
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
				{ value: 'one', label: '3000K(전구색/주황색)' },
				{ value: 'two', label: '4000K(주백색)' },
				{ value: 'three', label: '5700K(주광색/백색)' }
			],
			next: 'NEXT'
		},
		{
			step: 'outletPosition',
			label: '콘센트 옵션',
			question: '콘센트의 추가여부 혹은 위치를 선택하세요.',
			next: 'NEXT'
		},
		{
			step: 'dryPosition',
			label: '드라이걸이 옵션',
			question: '드라이걸이 추가여부 혹은 위치를 선택하세요.',
			next: 'NEXT'
		},
		{
			step: 'tissuePosition',
			label: '티슈홀캡(타공) 옵션',
			question: '티슈홀캡(타공) 추가여부 혹은 위치를 선택하세요.',
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
			question: '하부장의 사이즈를 입력 해 주세요.',
			next: 'CHANGED_BY_SERIES_ONLY'
		},
		// 세면대 형태 선택 세면대와 대리석 중 하나로 선택됨.
		{
			step: 'formofwash',
			label: '세면대 형태',
			question: '세면대의 형태를 선택 해 주세요.',
			options: [
				{ value: 'under', label: '언더볼' },
				{ value: 'dogi', label: '도기매립' },
				{ value: 'marble', label: '대리석' },
				{ value: 'body', label: '바디만(상판없음)' } // ✅ 새로운 옵션 추가
			],
			next: (selectedOption) => {
				if (selectedOption === 'under') {
					return 'sortofunder'; // 언더볼
				} else if (selectedOption === 'dogi') {
					return 'sortofdogi'; // 도기매립
				} else if (selectedOption === 'marble') {
					return 'colorofmarble'; // 대리석
				} else if (selectedOption === 'body') {
					return 'door'; // ✅ '바디만' 선택 시 door 단계로
				}
				return null;
			}
		},
		{
			step: 'sortofdogi',
			label: '세면대 종류',
			question: '세면대의 종류를 선택 해 주세요.',
			options: [
				{ value: '35', label: 'TB-060(비누대 O)' },
				{ value: '35', label: 'E-60(비누대 X)' },
				{ value: '35', label: 'PL-3040(비누대 O)' },
				{ value: '35', label: 'PL-3060(비누대 O)' }
			],
			next: 'numberofwash'
		},
		{
			step: 'sortofunder',
			label: '세면대 종류',
			question: '세면대의 종류를 선택 해 주세요.',
			options: [
				{ value: '32', label: 'CL-603(사각)' },
				{ value: '33', label: 'CL-509(타원형)' },
				{ value: '34', label: '제공 언더볼' }
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
				{ value: '페블 프러스트', label: '페블 프러스트' },
				{ value: '스노우', label: '스노우' },
				{ value: '사라토가', label: '사라토가' },
				{ value: '츄파로사', label: '츄파로사' },
				{ value: '아스펜 그레이', label: '아스펜 그레이' },
				{ value: '페블 에버니', label: '페블 에버니' },
				{ value: '라토나', label: '라토나' },
				{ value: '퓨어', label: '퓨어' },
				{ value: '아스펜 페퍼', label: '아스펜 페퍼' },
				{ value: '터레인', label: '터레인' },
				{ value: '레이니 스카이', label: '레이니 스카이' },
				{ value: '스카디', label: '스카디' },
				{ value: '오로라 블랑', label: '오로라 블랑' },
				{ value: '오로라 비스크', label: '오로라 비스크' },
				{ value: '오로라 그레이', label: '오로라 그레이' },
				{ value: '베네지아', label: '베네지아' },
			],
			next: 'door'
		},
		{
			step: 'door',
			label: '문 추가여부',
			question: '문을 추가하시겠습니까?',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함(바디만)' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'CHANGED_BY_SERIES' : 'maguri'
		},
		{
			step: 'formofdoor_slide',
			label: '문 형태',
			question: '문의 형태를 선택하세요.',
			options: [
				{ value: 'slide', label: '슬라이드' }
			],
			next: 'maguri'
		},
		{
			step: 'formofdoor_other',
			label: '문 형태',
			question: '문의 형태를 선택하세요.',
			options: [
				{ value: 'open', label: '여닫이' },
				{ value: 'drawer', label: '서랍식' },
				{ value: 'mixed', label: '혼합식' }
			],
			next: (selectedOption) => {
				if (selectedOption === 'open') {
					return 'numberofdoor';       // 여닫이 → numberofdoor
				} else if (selectedOption === 'drawer') {
					return 'numberofdrawer';    // 서랍식 → numberofdrawer
				} else if (selectedOption === 'mixed') {
					return 'maguri';      // 혼합식 → maguri 사이즈 입력
				}
				return null; // 기본값
			}
		},
		{
			step: 'numberofdoor',
			label: '문 수량',
			question: '문의 수량을 선택하세요.',
			next: 'doorDirection'
		},
		{
			step: 'doorDirection',
			label: '경첩 방향',
			question: '경첩 방향을 입력하세요.',
			next: 'maguri'
		},
		{
			step: 'numberofdrawer',
			label: '서랍 수량',
			question: '서랍의 수량을 선택하세요.',
			next: 'maguri'
		},
		{
			step: 'maguri',
			label: '마구리 추가여부',
			question: '마구리 설치여부를 선택하세요.',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'directionofmaguri' : 'hole'
		},
		{
			step: 'directionofmaguri',
			label: '마구리 설치방향',
			question: '마구리 설치방향을 선택하세요.',
			options: [
				{ value: 'front', label: '전면' },
				{ value: 'front_left', label: '전면/좌측면' },
				{ value: 'front_right', label: '전면/우측면' },
				{ value: 'front_left_right', label: '전면/좌측면/우측면' },
			],
			next: 'sizeofmaguri'
		},
		{
			step: 'sizeofmaguri',
			label: '마구리 사이즈',
			question: '마구리 사이즈를 입력해주세요.(1 ~ 250mm)',
			next: 'hole'
		},
		{
			step: 'hole',
			label: '상판 타공 유무',
			question: '상판 타공 유무를 선택하세요.',
			options: [
				{ value: 'add', label: '타공함' },
				{ value: 'not_add', label: '타공안함' }
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
			next: (selectedOption) => selectedOption === 'add' ? 'handletype' : 'CHANGED_BY_FORM'
		},
		{
			step: 'handletype',
			label: '손잡이 종류',
			question: '손잡이 종류를 선택 해 주세요.',
			options: [
				{ value: 'dolche', label: '히든 돌체 손잡이' },
				{ value: 'd195', label: '히든 D형(195) 손잡이' },
				{ value: 'half', label: '히든 하프 손잡이' },
				{ value: 'circle', label: '원형 손잡이' },
				{ value: 'd310', label: '히든 D형(310) 손잡이' }
			],
			next: (selectedOption) =>
				selectedOption === 'dolche' ? 'handle_dolche_color' :
				selectedOption === 'd195' ? 'handle_d195_color' :
				selectedOption === 'half' ? 'handle_half_color' :
				selectedOption === 'circle' ? 'handle_circle_color' : 
				'handle_d310_color'
		},
		{
			step: 'handle_dolche_color',
			label: '손잡의 색상',
			question: '손잡이 색상을 선택 해 주세요.',
			options: [
				{ value: 'one', label: '크롬' },
				{ value: 'two', label: '니켈' },
				{ value: 'three', label: '골드' }
			],
			next: 'CHANGED_BY_FORM'
		},
		{
			step: 'handle_d195_color',
			label: '손잡의 색상',
			question: '손잡이 색상을 선택 해 주세요.',
			options: [
				{ value: 'one', label: '크롬' },
				{ value: 'two', label: '니켈' },
				{ value: 'three', label: '골드' }
			],
			next: 'CHANGED_BY_FORM'
		},
		{
			step: 'handle_half_color',
			label: '손잡의 색상',
			question: '손잡이 색상을 선택 해 주세요.',
			options: [
				{ value: 'one', label: '화이트' },
				{ value: 'two', label: '크림' },
				{ value: 'three', label: '그레이' },
				{ value: 'four', label: '블랙' }
			],
			next: 'CHANGED_BY_FORM'
		},
		{
			step: 'handle_circle_color',
			label: '손잡의 색상',
			question: '손잡이 색상을 선택 해 주세요.',
			options: [
				{ value: 'one', label: '실버' },
				{ value: 'two', label: '골드' }
			],
			next: 'CHANGED_BY_FORM'
		},
		{
			step: 'handle_d310_color',
			label: '손잡의 색상',
			question: '손잡이 색상을 선택 해 주세요.',
			options: [
				{ value: 'one', label: '크롬' },
				{ value: 'two', label: '니켈' },
				{ value: 'three', label: '골드' }
			],
			next: 'CHANGED_BY_FORM'
		},
		{
			step: 'board',
			label: '걸레받이 추가여부',
			question: '걸레받이 추가여부를 선택하세요.',
			options: [
				{ value: 'add', label: '추가' },
				{ value: 'not_add', label: '추가 안함' }
			],
			next: (selectedOption) => selectedOption === 'add' ? 'directionofboard' : 'NEXT'
		},
		{
			step: 'directionofboard',
			label: '걸레받이 설치방향',
			question: '걸레받이 설치방향을 선택하세요.',
			options: [
				{ value: 'front', label: '전면' },
				{ value: 'front_left', label: '전면/좌측면' },
				{ value: 'front_right', label: '전면/우측면' },
				{ value: 'front_left_right', label: '전면/좌측면/우측면' },
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
				{ value: 'one', label: '3000K(전구색/주황색)' },
				{ value: 'two', label: '4000K(주백색)' },
				{ value: 'three', label: '5700K(주광색/백색)' }
			],
			next: 'NEXT'
		},
		{
			step: 'outletPosition',
			label: '콘센트 옵션',
			question: '콘센트의 추가여부 혹은 위치를 선택하세요.',
			next: 'NEXT'
		},
		{
			step: 'dryPosition',
			label: '드라이걸이 옵션',
			question: '드라이걸이 추가여부 혹은 위치를 선택하세요.',
			next: 'NEXT'
		},
		{
			step: 'tissuePosition',
			label: '티슈홀캡(타공) 옵션',
			question: '티슈홀캡(타공) 추가여부 혹은 위치를 선택하세요.',
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
				{ value: 'not_add', label: '추가 안함(바디만)' }
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
				{ value: 'one', label: '3000K(전구색/주황색)' },
				{ value: 'two', label: '4000K(주백색)' },
				{ value: 'three', label: '5700K(주광색/백색)' }
			],
			next: 'NEXT'
		},
		{
			step: 'outletPosition',
			label: '콘센트 옵션',
			question: '콘센트의 추가여부 혹은 위치를 선택하세요.',
			next: 'NEXT'
		},
		{
			step: 'dryPosition',
			label: '드라이걸이 옵션',
			question: '드라이걸이 추가여부 혹은 위치를 선택하세요.',
			next: 'NEXT'
		},
		{
			step: 'tissuePosition',
			label: '티슈홀캡(타공) 옵션',
			question: '티슈홀캡(타공) 추가여부 혹은 위치를 선택하세요.',
			next: 'final'
		}
	],
	// 비고에 바디만인지 혹은 도어변경을 원하는 경우 도어 기재 하도록 안내 메시지 작성
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
			question: '슬라이드장의 사이즈를 입력하세요.',
			next: 'door'
		},
		{
			step: 'door',
			label: '문 추가여부',
			question: '문을 추가 하시겠습니까?',
			options: [
				{ value: 'add', label: '추가함' },
				{ value: 'not_add', label: '추가하지않음(바디만)' }
			],
			next: 'NEXT'
		},
		{
			step: 'mirrorDirection',
			label: '거울방향',
			question: '좌측경 또는 우측경을 선택 해 주세요.',
			options: [
				{ value: 'left', label: '좌측경' },
				{ value: 'right', label: '우측경' }
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
				{ value: 'one', label: '3000K(전구색/주황색)' },
				{ value: 'two', label: '4000K(주백색)' },
				{ value: 'three', label: '5700K(주광색/백색)' }
			],
			next: 'NEXT'
		},
		{
			step: 'outletPosition',
			label: '콘센트 옵션',
			question: '콘센트의 추가여부 혹은 위치를 선택하세요.',
			next: 'NEXT'
		},
		{
			step: 'dryPosition',
			label: '드라이걸이 옵션',
			question: '드라이걸이 추가여부 혹은 위치를 선택하세요.',
			next: 'NEXT'
		},
		{
			step: 'tissuePosition',
			label: '티슈홀캡(타공) 옵션',
			question: '티슈홀캡(타공) 추가여부 혹은 위치를 선택하세요.',
			next: 'final'
		}
	]
};