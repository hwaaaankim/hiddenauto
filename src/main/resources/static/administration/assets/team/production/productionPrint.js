/* global document, window */

(function() {
	'use strict';

	function qs(sel) { return document.querySelector(sel); }
	function qsa(sel) { return Array.prototype.slice.call(document.querySelectorAll(sel)); }

	var btnBulkDone = qs('#team-production-bulk-done-btn');
	var btnSticker = qs('#team-production-sticker-print-btn');
	var checkAll = qs('#team-production-check-all');

	function getItemChecks() {
		return qsa('.team-production-check-item');
	}

	function getCheckedIds() {
		return getItemChecks()
			.filter(function(cb) { return cb.checked && !cb.disabled; })
			.map(function(cb) { return cb.getAttribute('data-order-id'); })
			.filter(function(v) { return v !== null && v !== ''; });
	}

	function syncButtonsAndCheckAll() {
		var items = getItemChecks().filter(function(cb) { return !cb.disabled; });
		var checked = getCheckedIds();

		var hasAny = checked.length > 0;

		if (btnSticker) btnSticker.disabled = !hasAny;
		if (btnBulkDone) btnBulkDone.disabled = !hasAny;

		if (checkAll) {
			if (items.length === 0) {
				checkAll.checked = false;
				checkAll.indeterminate = false;
				return;
			}
			var checkedCount = items.filter(function(cb) { return cb.checked; }).length;
			checkAll.checked = (checkedCount === items.length);
			checkAll.indeterminate = (checkedCount > 0 && checkedCount < items.length);
		}
	}

	function bindCheckboxEvents() {
		var items = getItemChecks();

		items.forEach(function(cb) {
			cb.addEventListener('change', syncButtonsAndCheckAll);
		});

		if (checkAll) {
			checkAll.addEventListener('change', function() {
				var items2 = getItemChecks().filter(function(cb) { return !cb.disabled; });
				items2.forEach(function(cb) { cb.checked = checkAll.checked; });
				syncButtonsAndCheckAll();
			});
		}
	}

	// ✅ 스티커 출력 클릭 → 선택 IDs를 POST로 새 창에 제출
	function bindStickerPrint() {
		if (!btnSticker) return;

		btnSticker.addEventListener('click', function() {
			var ids = getCheckedIds();
			if (ids.length === 0) return;

			// 새 창(또는 새 탭)으로 POST 제출
			var form = document.createElement('form');
			form.method = 'POST';
			form.action = '/team/productionStickerPrint';
			form.target = '_blank';

			ids.forEach(function(id) {
				var input = document.createElement('input');
				input.type = 'hidden';
				input.name = 'orderIds';
				input.value = id;
				form.appendChild(input);
			});

			document.body.appendChild(form);
			form.submit();
			document.body.removeChild(form);
		});
	}

	// init
	bindCheckboxEvents();
	bindStickerPrint();
	syncButtonsAndCheckAll();

})();
