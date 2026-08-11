(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        initOrderToggle();
        initMediaModal();
        openHashOrderIfNeeded();
    });

    function initOrderToggle() {
        document.querySelectorAll('.task-detail-order-toggle').forEach(function (button) {
            button.addEventListener('click', function () {
                const target = document.querySelector(button.dataset.target || '');
                if (!target) return;

                if (window.jQuery) {
                    window.jQuery(target).stop(true, true).slideToggle(160, function () {
                        button.textContent = window.jQuery(target).is(':visible') ? '접기' : '보기';
                    });
                } else {
                    const opened = target.style.display === 'block';
                    target.style.display = opened ? 'none' : 'block';
                    button.textContent = opened ? '보기' : '접기';
                }
            });
        });
    }

    function openHashOrderIfNeeded() {
        if (!window.location.hash || !window.location.hash.startsWith('#task-detail-order-')) return;
        const row = document.querySelector(window.location.hash);
        if (!row) return;
        const button = row.querySelector('.task-detail-order-toggle');
        if (button) button.click();
        setTimeout(function () { row.scrollIntoView({ behavior: 'smooth', block: 'center' }); }, 180);
    }

    function initMediaModal() {
        const modalElement = document.getElementById('task-detail-media-modal');
        const titleElement = document.getElementById('task-detail-media-modal-title');
        const inner = document.getElementById('task-detail-media-modal-inner');
        const carousel = document.getElementById('task-detail-media-modal-carousel');
        if (!modalElement || !titleElement || !inner || !carousel) return;

        bindModalBackdrop(modalElement, 'task-detail-modal-backdrop');

        document.querySelectorAll('.task-detail-media-open').forEach(function (button) {
            button.addEventListener('click', function () {
                const source = document.getElementById(button.dataset.mediaSourceId || '');
                if (!source) return;
                const items = Array.from(source.querySelectorAll('.task-detail-media-source-item'));
                if (items.length === 0) return;

                titleElement.textContent = button.dataset.mediaTitle || '이미지 보기';
                inner.innerHTML = '';
                items.forEach(function (item, index) {
                    const slide = document.createElement('div');
                    slide.className = 'carousel-item' + (index === 0 ? ' active' : '');
                    const image = document.createElement('img');
                    image.src = item.dataset.mediaUrl || '';
                    image.alt = item.dataset.mediaName || '';
                    slide.appendChild(image);
                    inner.appendChild(slide);
                });

                carousel.querySelectorAll('.carousel-control-prev, .carousel-control-next').forEach(function (control) {
                    control.style.display = items.length > 1 ? '' : 'none';
                });

                if (window.bootstrap && window.bootstrap.Modal) {
                    window.bootstrap.Modal.getOrCreateInstance(modalElement).show();
                }
            });
        });

        modalElement.addEventListener('hidden.bs.modal', function () {
            inner.innerHTML = '';
        });
    }

    function bindModalBackdrop(modalElement, className) {
        modalElement.addEventListener('shown.bs.modal', function () {
            const backdrops = document.querySelectorAll('.modal-backdrop');
            const backdrop = backdrops.length > 0 ? backdrops[backdrops.length - 1] : null;
            if (backdrop) backdrop.classList.add(className);
        });

        modalElement.addEventListener('hidden.bs.modal', function () {
            document.querySelectorAll('.' + className).forEach(function (backdrop) {
                backdrop.classList.remove(className);
            });
        });
    }
})();
