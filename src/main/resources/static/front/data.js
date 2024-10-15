// data.js
export const productData = {
	'거울': [
		{
			id: 'MIRROR01',
			name: '거울01',
			sizes: ['SIZE01', 'SIZE02'],
			colors: ['Red', 'Blue', 'Green']
		},
		{
			id: 'MIRROR02',
			name: '거울02',
			sizes: ['SIZE01', 'SIZE03', 'SIZE04'],
			colors: ['Yellow', 'Black']
		},
		{
			id: 'MIRROR03',
			name: '거울03',
			sizes: ['SIZE01', 'SIZE02', 'SIZE05'],
			colors: ['White', 'Red', 'Blue', 'Yellow']
		}
	],
	'상부장': [
		{
			id: 'UPPER01',
			name: '상부장01',
			sizes: ['SIZE04', 'SIZE05'],
			colors: ['Red', 'Blue']
		},
		{
			id: 'UPPER02',
			name: '상부장02',
			sizes: ['SIZE04', 'SIZE06'],
			colors: ['Yellow', 'Green', 'Black']
		},
		{
			id: 'UPPER03',
			name: '상부장03',
			sizes: ['SIZE05'],
			colors: ['Blue', 'White']
		}
	],
	'하부장': [
		{
			id: 'LOWER01',
			name: '하부장01',
			sizes: ['SIZE06', 'SIZE07'],
			colors: ['Red', 'Blue']
		},
		{
			id: 'LOWER02',
			name: '하부장02',
			sizes: ['SIZE06', 'SIZE08'],
			colors: ['Black', 'Yellow', 'White']
		},
		{
			id: 'LOWER03',
			name: '하부장03',
			sizes: ['SIZE07', 'SIZE08'],
			colors: ['Red', 'Green']
		}
	],
	'슬라이드장': [
		{
			id: 'SLIDE01',
			name: '슬라이드장01',
			sizes: ['SIZE08', 'SIZE09'],
			colors: ['Black', 'White']
		},
		{
			id: 'SLIDE02',
			name: '슬라이드장02',
			sizes: ['SIZE09'],
			colors: ['Yellow', 'Green', 'Blue']
		},
		{
			id: 'SLIDE03',
			name: '슬라이드장03',
			sizes: ['SIZE08', 'SIZE10'],
			colors: ['Red', 'Blue']
		}
	],
	'플랩장': [
		{
			id: 'FLAP01',
			name: '플랩장01',
			sizes: ['SIZE10'],
			colors: ['Yellow', 'Green']
		},
		{
			id: 'FLAP02',
			name: '플랩장02',
			sizes: ['SIZE09', 'SIZE10'],
			colors: ['Red', 'Blue', 'White']
		},
		{
			id: 'FLAP03',
			name: '플랩장03',
			sizes: ['SIZE10'],
			colors: ['Yellow', 'Green', 'Black']
		}
	],
	'기타': [
		{
			id: 'OTHER01',
			name: '기타01',
			sizes: ['SIZE01'],
			colors: ['Red', 'Blue']
		},
		{
			id: 'OTHER02',
			name: '기타02',
			sizes: ['SIZE02', 'SIZE03'],
			colors: ['Yellow', 'Black']
		},
		{
			id: 'OTHER03',
			name: '기타03',
			sizes: ['SIZE04'],
			colors: ['White', 'Green']
		}
	]
};

// 색상 및 사이즈 정보 (DB에서 색상 및 사이즈 정보를 별도로 가져왔다고 가정)
export const availableColors = ['Red', 'Blue', 'Green', 'Yellow', 'Black', 'White'];
export const availableSizes = ['SIZE01', 'SIZE02', 'SIZE03', 'SIZE04', 'SIZE05', 'SIZE06', 'SIZE07', 'SIZE08', 'SIZE09', 'SIZE10'];
