document.getElementById('file-upload').addEventListener('change', function() {
	const fileList = document.getElementById('file-list');
	fileList.innerHTML = ''; // 기존 목록 초기화

	const files = this.files;

	if (files.length > 0) {
		const fileItem = document.createElement('li');
		fileItem.innerHTML = `
	      ${files[0].name}
	      <span class="remove-file" onclick="removeSelectedFile()">✕</span>
	    `;
		fileList.appendChild(fileItem);
	}
});

function removeSelectedFile() {
	const input = document.getElementById('file-upload');
	input.value = ''; // 선택 초기화
	document.getElementById('file-list').innerHTML = ''; // 목록 제거
}
function execDaumPostcode() {
	new daum.Postcode({
		oncomplete: function(data) {
			const address = data.userSelectedType === 'R' ? data.roadAddress : data.jibunAddress;

			// 주소 input에 넣기
			document.getElementById('searchAddress').value = address;

			// 상세주소 input focus 이동
			document.getElementById('detailAddress').focus();

			// hidden input 값 세팅
			document.getElementById('doName').value = data.sido;          // 시 or 도
			document.getElementById('siName').value = data.sigungu;    // 구
			document.getElementById('guName').value = data.bname;        // 동/읍/면
			document.getElementById('zipCode').value = data.zonecode;  // 우편번호
		}
	}).open();
}

// ✅ 1. 아이디 중복 체크 (대표 + 직원)
['repUsername', 'empUsername'].forEach(id => {
	const input = document.getElementById(id);
	if (!input) return;

	input.addEventListener('change', async function () {
		const username = this.value.trim();
		if (!username) return;

		try {
			const res = await fetch(`/api/v1/validate/username?username=${encodeURIComponent(username)}`);
			const data = await res.json();
			if (data.duplicate) {
				alert('이미 사용 중인 아이디입니다.');
				this.value = '';
				this.focus();
			}
		} catch (e) {
			console.error('아이디 중복 확인 실패', e);
		}
	});
});

// ✅ 2. 비밀번호 일치 체크
// 대표 회원가입 비밀번호 확인 blur 시 체크
document.getElementById('repPasswordCheck')?.addEventListener('blur', function () {
	const pw = document.getElementById('repPassword')?.value;
	const check = this.value;

	if (pw && check && pw !== check) {
		alert('비밀번호가 일치하지 않습니다.');
		this.value = '';
		this.focus();
	}
});

// 직원 회원가입 비밀번호 확인 blur 시 체크
document.getElementById('empPasswordCheck')?.addEventListener('blur', function () {
	const pw = document.getElementById('empPassword')?.value;
	const check = this.value;

	if (pw && check && pw !== check) {
		alert('비밀번호가 일치하지 않습니다.');
		this.value = '';
		this.focus();
	}
});

// ✅ 3. 휴대폰 포맷 + 중복 체크
['repPhone', 'empPhone'].forEach(id => {
	const phoneInput = document.getElementById(id);
	if (!phoneInput) return;

	// 자동 포맷
	phoneInput.addEventListener('input', function () {
		let val = this.value.replace(/\D/g, '').slice(0, 11);
		let formatted = val;
		if (val.length > 3 && val.length <= 7)
			formatted = val.replace(/(\d{3})(\d+)/, '$1-$2');
		else if (val.length > 7)
			formatted = val.replace(/(\d{3})(\d{4})(\d+)/, '$1-$2-$3');
		this.value = formatted;
	});

	// 중복 체크
	phoneInput.addEventListener('blur', async function () {
		const rawNumber = this.value.replace(/\D/g, '');
		if (rawNumber.length !== 11) return;

		try {
			const res = await fetch(`/api/v1/validate/phone?phone=${encodeURIComponent(rawNumber)}`);
			const data = await res.json();
			if (data.duplicate) {
				alert('이미 등록된 연락처입니다.');
				this.value = '';
				this.focus();
			}
		} catch (e) {
			console.error('연락처 중복 확인 실패', e);
		}
	});
});
