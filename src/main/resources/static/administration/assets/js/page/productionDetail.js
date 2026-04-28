(function() {
	"use strict";

	document.addEventListener("DOMContentLoaded", function() {
		initProductionDoneConfirm();
		initLayoutToggle();
		initImageViewer();
	});

	function initProductionDoneConfirm() {
		var form = document.getElementById("product-detail-added-production-done-form");

		if (!form) {
			return;
		}

		form.addEventListener("submit", function(event) {
			var submitButton = form.querySelector(".product-detail-added-done-button");

			if (submitButton && submitButton.disabled) {
				event.preventDefault();
				return;
			}

			var confirmed = window.confirm("생산완료 처리하시겠습니까?\n처리 후에는 생산팀 화면에서 다시 되돌릴 수 없습니다.");

			if (!confirmed) {
				event.preventDefault();
			}
		});
	}

	function initLayoutToggle() {
		var detailArea = document.querySelector(".product-detail-added-detail-area");
		var toggleButtons = Array.prototype.slice.call(
			document.querySelectorAll(".product-detail-added-toggle-button")
		);

		if (!detailArea || toggleButtons.length === 0) {
			return;
		}

		var animationTimer = null;
		var firstScrollTimer = null;
		var finalScrollTimer = null;
		var toggleSequence = 0;

		toggleButtons.forEach(function(button) {
			button.addEventListener("click", function() {
				var layout = button.getAttribute("data-product-detail-added-layout");

				if (!layout) {
					return;
				}

				toggleSequence += 1;
				var currentSequence = toggleSequence;

				toggleButtons.forEach(function(item) {
					item.classList.remove("product-detail-added-toggle-active");
					item.setAttribute("aria-pressed", "false");
				});

				button.classList.add("product-detail-added-toggle-active");
				button.setAttribute("aria-pressed", "true");

				detailArea.classList.add("product-detail-added-layout-changing");

				if (layout === "image") {
					detailArea.classList.add("product-detail-added-image-focus");
				} else {
					detailArea.classList.remove("product-detail-added-image-focus");
				}

				resetOptionInnerScroll(detailArea);
				scheduleDetailAreaCenterScroll(detailArea, currentSequence);

				if (animationTimer) {
					window.clearTimeout(animationTimer);
				}

				animationTimer = window.setTimeout(function() {
					detailArea.classList.remove("product-detail-added-layout-changing");
				}, 520);
			});
		});

		function resetOptionInnerScroll(target) {
			var optionGrid = target.querySelector(".product-detail-added-option-grid");

			if (!optionGrid) {
				return;
			}

			if (typeof optionGrid.scrollTo === "function") {
				optionGrid.scrollTo({
					top: 0,
					behavior: getSmoothBehavior()
				});
			} else {
				optionGrid.scrollTop = 0;
			}
		}

		function scheduleDetailAreaCenterScroll(target, sequence) {
			if (firstScrollTimer) {
				window.clearTimeout(firstScrollTimer);
			}

			if (finalScrollTimer) {
				window.clearTimeout(finalScrollTimer);
			}

			window.requestAnimationFrame(function() {
				if (sequence !== toggleSequence) {
					return;
				}

				scrollDetailAreaToImportantPosition(target);
			});

			firstScrollTimer = window.setTimeout(function() {
				if (sequence !== toggleSequence) {
					return;
				}

				scrollDetailAreaToImportantPosition(target);
			}, 120);

			finalScrollTimer = window.setTimeout(function() {
				if (sequence !== toggleSequence) {
					return;
				}

				scrollDetailAreaToImportantPosition(target);
			}, 560);
		}

		function scrollDetailAreaToImportantPosition(target) {
			if (!target) {
				return;
			}

			var rect = target.getBoundingClientRect();
			var viewportHeight = window.innerHeight || document.documentElement.clientHeight;
			var scrollTop = window.pageYOffset || document.documentElement.scrollTop || 0;
			var fixedTopOffset = getFixedTopOffset();

			var availableHeight = viewportHeight - fixedTopOffset;
			var absoluteTop = rect.top + scrollTop;
			var targetHeight = rect.height;

			var nextScrollTop;

			/*
			 * 영역이 현재 화면보다 지나치게 큰 경우:
			 * 중앙 정렬을 억지로 하면 옵션/이미지 시작부가 위로 숨어버릴 수 있으므로
			 * 제품 옵션/이미지 패널 시작 부분이 잘 보이도록 상단 기준으로 보정합니다.
			 */
			if (targetHeight > availableHeight * 0.9) {
				nextScrollTop = absoluteTop - fixedTopOffset - 18;
			} else {
				/*
				 * 일반적인 경우:
				 * 제품 옵션 + 이미지 영역 전체가 현재 화면의 중앙에 오도록 보정합니다.
				 */
				nextScrollTop = absoluteTop + (targetHeight / 2) - fixedTopOffset - (availableHeight / 2);
			}

			nextScrollTop = Math.max(0, nextScrollTop);

			window.scrollTo({
				top: nextScrollTop,
				behavior: getSmoothBehavior()
			});
		}

		function getFixedTopOffset() {
			var selectors = [
				"#page-topbar",
				".navbar-header",
				".topbar",
				".app-menu",
				"header"
			];

			for (var i = 0; i < selectors.length; i += 1) {
				var element = document.querySelector(selectors[i]);

				if (!element) {
					continue;
				}

				var style = window.getComputedStyle(element);
				var rect = element.getBoundingClientRect();

				if (
					rect.height > 0 &&
					rect.top <= 5 &&
					(style.position === "fixed" || style.position === "sticky")
				) {
					return rect.height;
				}
			}

			return 0;
		}

		function getSmoothBehavior() {
			if (
				window.matchMedia &&
				window.matchMedia("(prefers-reduced-motion: reduce)").matches
			) {
				return "auto";
			}

			return "smooth";
		}
	}

	function initImageViewer() {
		var thumbs = Array.prototype.slice.call(document.querySelectorAll(".product-detail-added-thumb"));

		if (thumbs.length === 0) {
			return;
		}

		var images = thumbs.map(function(thumb, index) {
			return {
				index: index,
				url: thumb.getAttribute("data-product-detail-added-url") || "",
				name: thumb.getAttribute("data-product-detail-added-name") || "image"
			};
		}).filter(function(item) {
			return item.url !== "";
		});

		if (images.length === 0) {
			return;
		}

		var previewOpen = document.getElementById("product-detail-added-preview-open");
		var previewImage = document.getElementById("product-detail-added-preview-image");
		var previewName = document.getElementById("product-detail-added-preview-name");
		var previewCounter = document.getElementById("product-detail-added-preview-counter");
		var previewPrev = document.querySelector(".product-detail-added-preview-prev");
		var previewNext = document.querySelector(".product-detail-added-preview-next");

		var modalElement = document.getElementById("product-detail-added-image-modal");
		var modalImage = document.getElementById("product-detail-added-modal-image");
		var modalName = document.getElementById("product-detail-added-modal-name");
		var modalCounter = document.getElementById("product-detail-added-modal-counter");
		var modalStage = document.getElementById("product-detail-added-modal-stage");
		var modalPrev = document.getElementById("product-detail-added-modal-prev");
		var modalNext = document.getElementById("product-detail-added-modal-next");
		var zoomInButton = document.getElementById("product-detail-added-zoom-in");
		var zoomOutButton = document.getElementById("product-detail-added-zoom-out");
		var zoomResetButton = document.getElementById("product-detail-added-zoom-reset");
		var downloadLink = document.getElementById("product-detail-added-download");

		var previewIndex = 0;
		var modalIndex = 0;

		var zoom = 1;
		var translateX = 0;
		var translateY = 0;
		var dragging = false;
		var dragStartX = 0;
		var dragStartY = 0;
		var dragBaseX = 0;
		var dragBaseY = 0;

		setPreview(0, false);
		updateSingleImageControls();

		thumbs.forEach(function(thumb) {
			thumb.addEventListener("click", function(event) {
				event.preventDefault();
				event.stopPropagation();

				var nextIndex = parseInt(thumb.getAttribute("data-product-detail-added-index"), 10);

				if (Number.isNaN(nextIndex)) {
					nextIndex = 0;
				}

				setPreview(nextIndex, true);
			});
		});

		if (previewOpen) {
			previewOpen.addEventListener("click", function(event) {
				event.preventDefault();
				event.stopPropagation();
				openModal(previewIndex);
			});
		}

		bindSafeClick(previewPrev, function() {
			setPreview(previewIndex - 1, true);
		});

		bindSafeClick(previewNext, function() {
			setPreview(previewIndex + 1, true);
		});

		bindSafeClick(modalPrev, function() {
			setModalImage(modalIndex - 1, true);
		});

		bindSafeClick(modalNext, function() {
			setModalImage(modalIndex + 1, true);
		});

		bindSafeClick(zoomInButton, function() {
			setZoom(zoom + 0.25);
		});

		bindSafeClick(zoomOutButton, function() {
			setZoom(zoom - 0.25);
		});

		bindSafeClick(zoomResetButton, function() {
			resetZoom();
		});

		if (downloadLink) {
			downloadLink.addEventListener("click", function(event) {
				event.stopPropagation();
			});

			downloadLink.addEventListener("pointerdown", function(event) {
				event.stopPropagation();
			});
		}

		if (modalStage) {
			modalStage.addEventListener("wheel", function(event) {
				if (isInteractiveTarget(event.target)) {
					return;
				}

				event.preventDefault();

				if (event.deltaY < 0) {
					setZoom(zoom + 0.15);
				} else {
					setZoom(zoom - 0.15);
				}
			}, { passive: false });

			modalStage.addEventListener("dblclick", function(event) {
				if (isInteractiveTarget(event.target)) {
					return;
				}

				event.preventDefault();

				if (zoom === 1) {
					setZoom(2);
				} else {
					resetZoom();
				}
			});

			modalStage.addEventListener("pointerdown", function(event) {
				if (isInteractiveTarget(event.target)) {
					return;
				}

				if (zoom <= 1) {
					return;
				}

				event.preventDefault();

				dragging = true;
				dragStartX = event.clientX;
				dragStartY = event.clientY;
				dragBaseX = translateX;
				dragBaseY = translateY;

				if (modalStage.setPointerCapture) {
					modalStage.setPointerCapture(event.pointerId);
				}
			});

			modalStage.addEventListener("pointermove", function(event) {
				if (!dragging) {
					return;
				}

				event.preventDefault();

				translateX = dragBaseX + (event.clientX - dragStartX);
				translateY = dragBaseY + (event.clientY - dragStartY);
				applyTransform();
			});

			modalStage.addEventListener("pointerup", stopDragging);
			modalStage.addEventListener("pointercancel", stopDragging);
			modalStage.addEventListener("pointerleave", stopDragging);
		}

		if (modalElement) {
			modalElement.addEventListener("hidden.bs.modal", function() {
				resetZoom();
			});

			var fallbackCloseButtons = Array.prototype.slice.call(
				modalElement.querySelectorAll("[data-bs-dismiss='modal']")
			);

			fallbackCloseButtons.forEach(function(button) {
				button.addEventListener("click", function() {
					if (!window.bootstrap || !window.bootstrap.Modal) {
						hideModalFallback();
					}
				});
			});
		}

		document.addEventListener("keydown", function(event) {
			if (!modalElement || !modalElement.classList.contains("show")) {
				return;
			}

			if (event.key === "ArrowLeft") {
				event.preventDefault();
				setModalImage(modalIndex - 1, true);
			}

			if (event.key === "ArrowRight") {
				event.preventDefault();
				setModalImage(modalIndex + 1, true);
			}

			if (event.key === "Escape" && (!window.bootstrap || !window.bootstrap.Modal)) {
				hideModalFallback();
			}
		});

		function setPreview(nextIndex, animated) {
			previewIndex = normalizeIndex(nextIndex);
			var item = images[previewIndex];

			if (!item) {
				return;
			}

			if (previewImage) {
				swapImage(previewImage, item.url, item.name, animated);
			}

			if (previewName) {
				previewName.textContent = item.name;
			}

			if (previewCounter) {
				previewCounter.textContent = (previewIndex + 1) + " / " + images.length;
			}

			if (previewOpen) {
				previewOpen.setAttribute("data-product-detail-added-index", String(previewIndex));
			}

			thumbs.forEach(function(thumb, index) {
				if (index === previewIndex) {
					thumb.classList.add("product-detail-added-thumb-active");
					thumb.scrollIntoView({ block: "nearest", inline: "nearest" });
				} else {
					thumb.classList.remove("product-detail-added-thumb-active");
				}
			});
		}

		function openModal(index) {
			setModalImage(index, false);

			if (!modalElement) {
				return;
			}

			if (window.bootstrap && window.bootstrap.Modal) {
				var instance = window.bootstrap.Modal.getOrCreateInstance(modalElement);
				instance.show();
			} else {
				showModalFallback();
			}
		}

		function setModalImage(nextIndex, animated) {
			modalIndex = normalizeIndex(nextIndex);
			var item = images[modalIndex];

			if (!item) {
				return;
			}

			resetZoom();

			if (modalImage) {
				swapImage(modalImage, item.url, item.name, animated);
			}

			if (modalName) {
				modalName.textContent = item.name;
			}

			if (modalCounter) {
				modalCounter.textContent = (modalIndex + 1) + " / " + images.length;
			}

			if (downloadLink) {
				downloadLink.href = item.url;
				downloadLink.setAttribute("download", item.name);
			}
		}

		function normalizeIndex(index) {
			if (images.length === 0) {
				return 0;
			}

			if (index < 0) {
				return images.length - 1;
			}

			if (index >= images.length) {
				return 0;
			}

			return index;
		}

		function updateSingleImageControls() {
			var disabled = images.length <= 1;
			var controls = [previewPrev, previewNext, modalPrev, modalNext];

			controls.forEach(function(control) {
				if (!control) {
					return;
				}

				control.disabled = disabled;
				control.setAttribute("aria-disabled", disabled ? "true" : "false");
				control.style.opacity = disabled ? "0.35" : "";
				control.style.pointerEvents = disabled ? "none" : "";
			});
		}

		function setZoom(nextZoom) {
			zoom = Math.max(1, Math.min(4, nextZoom));

			if (zoom === 1) {
				translateX = 0;
				translateY = 0;
			}

			applyTransform();
		}

		function resetZoom() {
			zoom = 1;
			translateX = 0;
			translateY = 0;
			applyTransform();
		}

		function applyTransform() {
			if (!modalImage) {
				return;
			}

			modalImage.style.transform = "translate(" + translateX + "px, " + translateY + "px) scale(" + zoom + ")";
		}

		function stopDragging() {
			dragging = false;
		}

		function swapImage(imageElement, url, alt, animated) {
			if (!imageElement) {
				return;
			}

			if (!animated) {
				imageElement.src = url;
				imageElement.alt = alt;
				imageElement.classList.remove("product-detail-added-image-swapping");
				return;
			}

			imageElement.classList.add("product-detail-added-image-swapping");

			window.setTimeout(function() {
				imageElement.src = url;
				imageElement.alt = alt;
			}, 80);

			var removeSwapClass = function() {
				imageElement.classList.remove("product-detail-added-image-swapping");
				imageElement.removeEventListener("load", removeSwapClass);
			};

			imageElement.addEventListener("load", removeSwapClass);

			window.setTimeout(function() {
				imageElement.classList.remove("product-detail-added-image-swapping");
				imageElement.removeEventListener("load", removeSwapClass);
			}, 260);
		}

		function bindSafeClick(element, callback) {
			if (!element) {
				return;
			}

			element.addEventListener("pointerdown", function(event) {
				event.preventDefault();
				event.stopPropagation();
			});

			element.addEventListener("click", function(event) {
				event.preventDefault();
				event.stopPropagation();

				if (typeof callback === "function") {
					callback();
				}
			});
		}

		function isInteractiveTarget(target) {
			if (!target || !target.closest) {
				return false;
			}

			return Boolean(target.closest("button, a, input, select, textarea, label"));
		}

		function showModalFallback() {
			if (!modalElement) {
				return;
			}

			modalElement.style.display = "block";
			modalElement.classList.add("show");
			modalElement.removeAttribute("aria-hidden");
			modalElement.setAttribute("aria-modal", "true");
			document.body.classList.add("modal-open");
		}

		function hideModalFallback() {
			if (!modalElement) {
				return;
			}

			modalElement.style.display = "none";
			modalElement.classList.remove("show");
			modalElement.setAttribute("aria-hidden", "true");
			modalElement.removeAttribute("aria-modal");
			document.body.classList.remove("modal-open");
			resetZoom();
		}
	}
})();