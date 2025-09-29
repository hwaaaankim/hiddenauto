/* ==========================================================================
 * popupManager.js
 * - 등록/수정/삭제 UX + 드래그 정렬
 * ========================================================================== */
(function() {
	'use strict';

	const USE_AJAX = false;      // 서버 리다이렉트 사용 시 false
	const IMAGE_MAX_MB = 10;

	const qs = (sel, root = document) => root.querySelector(sel);
	const qsa = (sel, root = document) => Array.from(root.querySelectorAll(sel));

	const isHttpUrl = (v) => /^https?:\/\//i.test(v || '');

	const validateDateRange = (startInput, endInput) => {
		if (!startInput || !endInput) return true;
		const s = startInput.value;
		const e = endInput.value;
		if (!s || !e) return true;
		const sd = new Date(s);
		const ed = new Date(e);
		return ed >= sd;
	};

	const validateImageFile = (file) => {
		if (!file) return { ok: false, msg: '이미지를 선택하세요.' };
		const okType = /^image\//i.test(file.type);
		if (!okType) return { ok: false, msg: '이미지 파일만 업로드할 수 있습니다.' };
		const maxBytes = IMAGE_MAX_MB * 1024 * 1024;
		if (file.size > maxBytes) {
			return { ok: false, msg: `이미지는 최대 ${IMAGE_MAX_MB}MB까지 업로드 가능합니다.` };
		}
		return { ok: true };
	};

	const previewFileToImg = (file, imgEl, after = () => { }) => {
		if (!file || !imgEl) return;
		const reader = new FileReader();
		reader.onload = (e) => {
			imgEl.src = e.target.result;
			imgEl.style.display = 'block';
			after();
		};
		reader.readAsDataURL(file);
	};

	const enableBtn = (btn) => { if (btn) btn.disabled = false; };
	const disableBtn = (btn) => { if (btn) btn.disabled = true; };

	/* =========================
	 * Insert Form
	 * ========================= */
	function initInsertForm() {
		// (기존 그대로)
		const form = qs('.popupManager-form');
		if (!form) return;

		const fileInput = qs('.popupManager-image-input', form);
		const thumbImg = qs('.popupManager-thumb-img', form);
		const thumbRemove = qs('.popupManager-thumb-remove', form);

		const linkToggle = qs('.popupManager-link-toggle', form);
		const linkWrap = qs('.popupManager-link-wrap', form);
		const linkInput = qs('.popupManager-link-input', form);

		const startAt = form.querySelector('input[name="startAt"]');
		const endAt = form.querySelector('input[name="endAt"]');

		const syncLinkWrap = () => {
			const on = !!linkToggle?.checked;
			if (linkWrap) linkWrap.classList.toggle('show', on);
			if (!on && linkInput) linkInput.value = '';
		};
		syncLinkWrap();
		linkToggle?.addEventListener('change', syncLinkWrap);

		fileInput?.addEventListener('change', () => {
			const f = fileInput.files?.[0];
			if (f) {
				const r = validateImageFile(f);
				if (!r.ok) {
					alert(r.msg);
					fileInput.value = '';
					thumbImg.src = '';
					thumbImg.style.display = 'none';
					thumbRemove.style.display = 'none';
					return;
				}
				previewFileToImg(f, thumbImg, () => {
					thumbRemove.style.display = 'inline-block';
				});
			} else {
				thumbImg.src = '';
				thumbImg.style.display = 'none';
				thumbRemove.style.display = 'none';
			}
		});

		thumbRemove?.addEventListener('click', () => {
			fileInput.value = '';
			thumbImg.src = '';
			thumbImg.style.display = 'none';
			thumbRemove.style.display = 'none';
			fileInput.focus();
		});

		form.addEventListener('submit', (ev) => {
			if (!fileInput || fileInput.files.length === 0) {
				ev.preventDefault();
				alert('이미지는 필수입니다.');
				fileInput?.focus();
				return;
			}
			const vf = validateImageFile(fileInput.files[0]);
			if (!vf.ok) {
				ev.preventDefault();
				alert(vf.msg);
				fileInput.focus();
				return;
			}
			if (!validateDateRange(startAt, endAt)) {
				ev.preventDefault();
				alert('종료일이 시작일보다 빠를 수 없습니다.');
				(endAt || startAt).focus();
				return;
			}
			if (linkToggle?.checked) {
				if (!isHttpUrl(linkInput?.value)) {
					ev.preventDefault();
					alert('연결 URL을 올바르게 입력하세요. (http/https)');
					linkInput?.focus();
					return;
				}
			}
			if (!USE_AJAX) return;
			ev.preventDefault();
			const fd = new FormData(form);
			fetch(form.action, { method: 'POST', body: fd })
				.then(() => window.location.reload())
				.catch(() => alert('등록 중 오류가 발생했습니다.'));
		});
	}

	/* =========================
	 * Card Forms (Update/Delete)
	 * ========================= */
	function initCardForms() {
		// (기존 그대로)
		const cards = qsa('.popupManager-card');
		cards.forEach((card) => {
			const form = qs('.popupManager-card-form', card);
			if (!form) return;

			const updateBtn = qs('.popupManager-card-update', form);

			const linkToggle = qs('.popupManager-card-link-toggle', form);
			const linkWrap = qs('.popupManager-card-link-wrap', form);
			const linkInput = qs('.popupManager-card-link-input', form);

			const startAt = qs('.popupManager-card-start', form);
			const endAt = qs('.popupManager-card-end', form);

			const imageInput = qs('.popupManager-card-image', form);
			const previewImg = card.querySelector('img.img-fluid.border');

			if (linkWrap) {
				const initialOn = !!linkToggle?.checked;
				linkWrap.classList.toggle('show', initialOn);
			}

			qsa('input,select,textarea', form).forEach((el) => {
				el.addEventListener('change', () => enableBtn(updateBtn));
				el.addEventListener('input', () => enableBtn(updateBtn));
			});

			const syncCardLink = () => {
				const on = !!linkToggle?.checked;
				if (linkWrap) linkWrap.classList.toggle('show', on);
				if (!on && linkInput) linkInput.value = '';
				enableBtn(updateBtn);
			};
			linkToggle?.addEventListener('change', syncCardLink);

			imageInput?.addEventListener('change', () => {
				const f = imageInput.files?.[0];
				if (!f) return;
				const r = validateImageFile(f);
				if (!r.ok) {
					alert(r.msg);
					imageInput.value = '';
					return;
				}
				if (previewImg) previewFileToImg(f, previewImg);
				enableBtn(updateBtn);
			});

			form.addEventListener('submit', (ev) => {
				if (!validateDateRange(startAt, endAt)) {
					ev.preventDefault();
					alert('종료일이 시작일보다 빠를 수 없습니다.');
					(endAt || startAt).focus();
					return;
				}
				if (linkToggle?.checked) {
					if (!isHttpUrl(linkInput?.value)) {
						ev.preventDefault();
						alert('연결 URL을 올바르게 입력하세요. (http/https)');
						linkInput?.focus();
						return;
					}
				}
				if (!USE_AJAX) return;
				ev.preventDefault();
				const fd = new FormData(form);
				fetch(form.action, { method: 'POST', body: fd })
					.then(() => window.location.reload())
					.catch(() => alert('수정 중 오류가 발생했습니다.'));
			});

			const deleteForm = card.querySelector('form[action*="/management/popupDelete"]');
			if (deleteForm) {
				deleteForm.addEventListener('submit', (ev) => {
					if (!confirm('삭제하시겠습니까?')) {
						ev.preventDefault();
						return;
					}
					if (!USE_AJAX) return;
					ev.preventDefault();
					const fd = new FormData(deleteForm);
					fetch(deleteForm.action, { method: 'POST', body: fd })
						.then(() => window.location.reload())
						.catch(() => alert('삭제 중 오류가 발생했습니다.'));
				});
			}
		});
	}

	/* =========================
	 * ✅ 드래그 정렬 & 저장
	 * ========================= */
	let sortDirty = false;

	function currentOrderedIds() {
		// 카드 DOM 순서대로 id 수집
		return qsa('.popupManager-list .popupManager-card')
			.map(el => Number(el.getAttribute('data-id')))
			.filter(n => !Number.isNaN(n));
	}

	function initSortable() {
		const listEl = qs('.popupManager-list[data-sortable="true"]');
		if (!listEl || typeof Sortable === 'undefined') return;

		new Sortable(listEl, {
			animation: 150,
			handle: '.popupManager-drag-handle',
			forceFallback: true,
			// row/grid 레이아웃에서도 동작하게 하기 위해 draggable를 칼럼으로 지정
			draggable: '.col-lg-3, .col-md-4, .col-sm-6, .col-xs-12',
			onSort() {
				sortDirty = true;
				const btn = qs('#popupManager-order-save');
				enableBtn(btn);
			}
		});
	}

	function initOrderSave() {
		const btn = qs('#popupManager-order-save');
		if (!btn) return;

		btn.addEventListener('click', async () => {
			if (!sortDirty) return;

			const ids = currentOrderedIds();
			if (!ids.length) {
				alert('변경할 항목이 없습니다.');
				return;
			}
			try {
				const res = await fetch('/management/updatePopupIndex', {
					method: 'POST',
					headers: { 'Content-Type': 'application/json' },
					body: JSON.stringify({ ids })
				});
				if (!res.ok) throw new Error('bad response');
				sortDirty = false;
				disableBtn(btn);
				// 저장 후 번호(#dispOrder) 갱신용으로 단순 새로고침 권장
				window.location.reload();
			} catch (e) {
				console.error(e);
				alert('순서 저장 중 오류가 발생했습니다.');
			}
		});
	}

	/* =========================
	 * DOM Ready
	 * ========================= */
	document.addEventListener('DOMContentLoaded', function() {
		initInsertForm();
		initCardForms();
		initSortable();   // ✅ 추가
		initOrderSave();  // ✅ 추가
	});
})();
