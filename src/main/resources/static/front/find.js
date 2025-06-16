// find.js
document.addEventListener('DOMContentLoaded', () => {
	const phoneInputs = document.querySelectorAll('input[name="phone"]');

	phoneInputs.forEach(input => {
		input.addEventListener('input', handlePhoneInput);
	});
});

/**
 * 입력된 문자열을 숫자만 추출하고 자동으로 하이픈 포맷을 적용
 * ex) 01012345678 → 010-1234-5678
 */
function handlePhoneInput(event) {
	let input = event.target;
	let digits = input.value.replace(/\D/g, ''); // 숫자 이외 제거

	if (digits.length > 11) digits = digits.substring(0, 11);

	let formatted = '';

	if (digits.length < 4) {
		formatted = digits;
	} else if (digits.length < 8) {
		formatted = `${digits.substring(0, 3)}-${digits.substring(3)}`;
	} else {
		formatted = `${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7)}`;
	}

	input.value = formatted;
}
