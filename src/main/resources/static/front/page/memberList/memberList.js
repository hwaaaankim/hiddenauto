/* /front/page/memberList/memberList.js */
/* global bootstrap */

(function() {
	'use strict';

	function byId(id) {
		return document.getElementById(id);
	}

	function all(selector, root) {
		return Array.prototype.slice.call((root || document).querySelectorAll(selector));
	}

	function normalize(value) {
		return (value == null ? '' : String(value))
			.trim()
			.toLowerCase();
	}

	var page = document.querySelector('.member-list-page');
	var groups = all('.member-list-row-group');

	var searchInput = byId('member-list-search-input');
	var statusFilter = byId('member-list-status-filter');
	var resetBtn = byId('member-list-filter-reset-btn');
	var visibleCount = byId('member-list-visible-count');
	var filterEmpty = byId('member-list-filter-empty');

	var totalStat = byId('member-list-stat-total');
	var activeStat = byId('member-list-stat-active');
	var inactiveStat = byId('member-list-stat-inactive');

	var noticeWrap = byId('member-list-notice-wrap');

	var statusModalEl = byId('member-list-status-modal');
	var confirmMemberEl = byId('member-list-confirm-member');
	var confirmMessageEl = byId('member-list-confirm-message');
	var confirmSubmitBtn = byId('member-list-confirm-submit');

	var statusModal = null;
	var pendingStatusChange = null;
	var noticeTimer = null;

	if (statusModalEl && window.bootstrap && bootstrap.Modal) {
		statusModal = bootstrap.Modal.getOrCreateInstance(statusModalEl, {
			backdrop: 'static',
			keyboard: true
		});
	}

	// ============================================================
	// Notice
	// ============================================================
	function showNotice(message, type) {
		if (!noticeWrap || !message) {
			return;
		}

		if (noticeTimer) {
			window.clearTimeout(noticeTimer);
			noticeTimer = null;
		}

		noticeWrap.innerHTML = '';

		var notice = document.createElement('div');
		notice.className = 'member-list-notice';

		if (type === 'danger') {
			notice.classList.add('member-list-notice-danger');
			notice.innerHTML = '<i class="fa fa-exclamation-circle"></i><span></span>';
		} else if (type === 'info') {
			notice.classList.add('member-list-notice-info');
			notice.innerHTML = '<i class="fa fa-info-circle"></i><span></span>';
		} else {
			notice.classList.add('member-list-notice-success');
			notice.innerHTML = '<i class="fa fa-check-circle"></i><span></span>';
		}

		notice.querySelector('span').textContent = message;
		noticeWrap.appendChild(notice);

		noticeTimer = window.setTimeout(function() {
			if (noticeWrap.contains(notice)) {
				notice.style.opacity = '0';

				window.setTimeout(function() {
					if (noticeWrap.contains(notice)) {
						notice.remove();
					}
				}, 180);
			}
		}, 4200);
	}

	// ============================================================
	// Statistics
	// ============================================================
	function refreshStatistics() {
		var total = groups.length;
		var active = 0;

		groups.forEach(function(group) {
			if (String(group.dataset.memberListEnabled) === 'true') {
				active += 1;
			}
		});

		var inactive = total - active;

		if (totalStat) totalStat.textContent = String(total);
		if (activeStat) activeStat.textContent = String(active);
		if (inactiveStat) inactiveStat.textContent = String(inactive);
	}

	// ============================================================
	// Filtering
	// ============================================================
	function groupSearchText(group) {
		return [
			group.dataset.memberListName,
			group.dataset.memberListUsername,
			group.dataset.memberListPhone,
			group.dataset.memberListEmail
		]
			.map(normalize)
			.join(' ');
	}

	function applyFilter() {
		if (!groups.length) {
			return;
		}

		var keyword = normalize(searchInput ? searchInput.value : '');
		var status = statusFilter ? statusFilter.value : 'all';
		var visible = 0;

		groups.forEach(function(group) {
			var keywordMatch = !keyword || groupSearchText(group).indexOf(keyword) !== -1;
			var enabled = String(group.dataset.memberListEnabled) === 'true';

			var statusMatch =
				status === 'all' ||
				(status === 'enabled' && enabled) ||
				(status === 'disabled' && !enabled);

			var matched = keywordMatch && statusMatch;

			group.classList.toggle('d-none', !matched);

			if (matched) {
				visible += 1;
			}
		});

		if (visibleCount) {
			visibleCount.textContent = String(visible);
		}

		if (filterEmpty) {
			filterEmpty.classList.toggle('d-none', visible !== 0);
		}
	}

	function bindFilter() {
		if (searchInput) {
			searchInput.addEventListener('input', applyFilter);
		}

		if (statusFilter) {
			statusFilter.addEventListener('change', applyFilter);
		}

		if (resetBtn) {
			resetBtn.addEventListener('click', function() {
				if (searchInput) {
					searchInput.value = '';
				}
				if (statusFilter) {
					statusFilter.value = 'all';
				}

				applyFilter();

				if (searchInput) {
					searchInput.focus();
				}
			});
		}
	}

	// ============================================================
	// Slide detail
	// ============================================================
	function setExpanded(group, expanded) {
		if (!group) {
			return;
		}

		var memberId = group.dataset.memberListId;
		var detail = group.querySelector('[data-member-list-detail="' + memberId + '"]');
		var button = group.querySelector('[data-member-list-detail-btn="' + memberId + '"]');

		if (!detail) {
			return;
		}

		group.classList.toggle('member-list-open', expanded);

		if (button) {
			button.setAttribute('aria-expanded', expanded ? 'true' : 'false');

			var text = button.querySelector('span');
			if (text) {
				text.textContent = expanded ? '닫기' : '확인';
			}
		}

		if (expanded) {
			detail.style.maxHeight = detail.scrollHeight + 'px';
		} else {
			detail.style.maxHeight = '0px';
		}
	}

	function toggleGroup(group) {
		if (!group) return;
		setExpanded(group, !group.classList.contains('member-list-open'));
	}

	function bindSlideRows() {
		groups.forEach(function(group) {
			var memberId = group.dataset.memberListId;
			var row = group.querySelector('[data-member-list-row-trigger="true"]');
			var detailButton = group.querySelector('[data-member-list-detail-btn="' + memberId + '"]');

			if (detailButton) {
				detailButton.addEventListener('click', function(event) {
					event.preventDefault();
					event.stopPropagation();
					toggleGroup(group);
				});
			}

			if (row) {
				row.addEventListener('click', function(event) {
					if (event.target.closest('a, button, input, label')) {
						return;
					}

					toggleGroup(group);
				});

				row.addEventListener('keydown', function(event) {
					if (event.key !== 'Enter' && event.key !== ' ') {
						return;
					}

					if (event.target.closest('a, button, input, label')) {
						return;
					}

					event.preventDefault();
					toggleGroup(group);
				});
			}
		});

		window.addEventListener('resize', function() {
			groups.forEach(function(group) {
				if (!group.classList.contains('member-list-open')) {
					return;
				}

				var detail = group.querySelector('.member-list-detail');
				if (detail) {
					detail.style.maxHeight = detail.scrollHeight + 'px';
				}
			});
		});
	}

	// ============================================================
	// Status UI
	// ============================================================
	function updateStatusUi(memberId, enabled) {
		var group = byId('member-list-member-' + memberId);

		if (!group) {
			return;
		}

		group.dataset.memberListEnabled = enabled ? 'true' : 'false';

		var toggle = group.querySelector('[data-member-list-toggle="' + memberId + '"]');
		var badge = group.querySelector('[data-member-list-status-badge="' + memberId + '"]');
		var accessText = group.querySelector('[data-member-list-access-text="' + memberId + '"]');

		if (toggle) {
			toggle.checked = enabled;
			toggle.disabled = false;
		}

		if (badge) {
			badge.textContent = enabled ? '접속 허용' : '접속 차단';
			badge.classList.toggle('member-list-status-enabled', enabled);
			badge.classList.toggle('member-list-status-disabled', !enabled);
		}

		if (accessText) {
			accessText.textContent = enabled ? '접속 허용중' : '접속 차단중';
		}

		refreshStatistics();
		applyFilter();
	}

	function resetPendingSwitch() {
		if (!pendingStatusChange || !pendingStatusChange.toggle) {
			return;
		}

		pendingStatusChange.toggle.checked = pendingStatusChange.currentEnabled;
		pendingStatusChange.toggle.disabled = false;
	}

	function openStatusModal(toggle) {
		var memberId = toggle.dataset.memberListToggle;
		var memberName = toggle.dataset.memberListName || '직원';
		var group = byId('member-list-member-' + memberId);

		if (!group) {
			return;
		}

		var currentEnabled = String(group.dataset.memberListEnabled) === 'true';
		var desiredEnabled = !currentEnabled;

		// 브라우저 change 시 checkbox가 먼저 바뀌므로, 확인 전에는 현재 상태로 되돌립니다.
		toggle.checked = currentEnabled;

		pendingStatusChange = {
			memberId: memberId,
			memberName: memberName,
			currentEnabled: currentEnabled,
			desiredEnabled: desiredEnabled,
			toggle: toggle,
			group: group
		};

		if (confirmMemberEl) {
			confirmMemberEl.textContent = memberName;
		}

		if (confirmMessageEl) {
			confirmMessageEl.textContent = desiredEnabled
				? '이 직원의 로그인을 다시 허용하시겠습니까?'
				: '이 직원의 로그인을 차단하시겠습니까?';
		}

		if (confirmSubmitBtn) {
			var buttonText = confirmSubmitBtn.querySelector('span');
			if (buttonText) {
				buttonText.textContent = desiredEnabled ? '접속 허용' : '접속 차단';
			}

			confirmSubmitBtn.classList.toggle('member-list-btn-primary', desiredEnabled);
			confirmSubmitBtn.classList.toggle('member-list-confirm-danger', !desiredEnabled);
			confirmSubmitBtn.disabled = false;
		}

		if (statusModal) {
			statusModal.show();
		} else {
			showNotice('상태 변경 확인창을 열 수 없습니다. Bootstrap 로드를 확인해 주세요.', 'danger');
			pendingStatusChange = null;
		}
	}

	function submitStatusChange() {
		if (!pendingStatusChange) {
			return;
		}

		var change = pendingStatusChange;

		if (confirmSubmitBtn) {
			confirmSubmitBtn.disabled = true;
		}

		if (change.toggle) {
			change.toggle.disabled = true;
		}

		fetch('/customer/toggleMemberEnabled', {
			method: 'POST',
			credentials: 'same-origin',
			headers: {
				'Accept': 'application/json',
				'Content-Type': 'application/json'
			},
			body: JSON.stringify({
				memberId: Number(change.memberId),
				enabled: change.desiredEnabled
			})
		})
			.then(function(response) {
				return response.json().then(function(body) {
					return {
						ok: response.ok,
						body: body
					};
				});
			})
			.then(function(result) {
				if (!result.ok) {
					throw new Error(
						result.body && result.body.message
							? result.body.message
							: '접속 상태 변경에 실패했습니다.'
					);
				}

				updateStatusUi(change.memberId, change.desiredEnabled);

				pendingStatusChange = null;

				if (statusModal) {
					statusModal.hide();
				}

				showNotice(
					change.memberName + ' 직원의 접속 상태를 '
						+ (change.desiredEnabled ? '허용' : '차단')
						+ '으로 변경했습니다.',
					'success'
				);
			})
			.catch(function(error) {
				resetPendingSwitch();

				if (statusModal) {
					statusModal.hide();
				}

				showNotice(
					error && error.message
						? error.message
						: '직원 접속 상태 변경 중 오류가 발생했습니다.',
					'danger'
				);

				pendingStatusChange = null;
			})
			.finally(function() {
				if (confirmSubmitBtn) {
					confirmSubmitBtn.disabled = false;
				}
			});
	}

	function bindStatusToggles() {
		all('[data-member-list-toggle]').forEach(function(toggle) {
			toggle.addEventListener('change', function() {
				openStatusModal(toggle);
			});
		});

		if (confirmSubmitBtn) {
			confirmSubmitBtn.addEventListener('click', submitStatusChange);
		}

		if (statusModalEl) {
			statusModalEl.addEventListener('hidden.bs.modal', function() {
				if (pendingStatusChange) {
					resetPendingSwitch();
					pendingStatusChange = null;
				}

				if (confirmSubmitBtn) {
					confirmSubmitBtn.disabled = false;
					confirmSubmitBtn.classList.add('member-list-btn-primary');
					confirmSubmitBtn.classList.remove('member-list-confirm-danger');
				}
			});
		}
	}

	// ============================================================
	// Old memberManager URL -> focused employee handling
	// ============================================================
	function focusRequestedMember() {
		if (!page) {
			return;
		}

		var focusId = String(page.dataset.memberListFocusId || '').trim();

		if (!focusId) {
			return;
		}

		var group = byId('member-list-member-' + focusId);

		if (!group) {
			return;
		}

		group.classList.add('member-list-focus');
		setExpanded(group, true);

		window.setTimeout(function() {
			group.scrollIntoView({
				behavior: 'smooth',
				block: 'center'
			});
		}, 180);

		window.setTimeout(function() {
			group.classList.remove('member-list-focus');
		}, 2600);
	}

	// ============================================================
	// Init
	// ============================================================
	document.addEventListener('DOMContentLoaded', function() {
		refreshStatistics();
		bindFilter();
		bindSlideRows();
		bindStatusToggles();
		applyFilter();
		focusRequestedMember();
	});

})();
