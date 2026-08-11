(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        const modalElement = document.getElementById('order-detail-media-modal');
        const titleElement = document.getElementById('order-detail-media-modal-title');
        const inner = document.getElementById('order-detail-media-modal-inner');
        const carousel = document.getElementById('order-detail-media-modal-carousel');
        if (!modalElement || !titleElement || !inner || !carousel) return;

        bindModalBackdrop(modalElement, 'order-detail-modal-backdrop');

        document.querySelectorAll('.order-detail-media-open').forEach(function (button) {
            button.addEventListener('click', function () {
                const source = document.getElementById(button.dataset.mediaSourceId || '');
                if (!source) return;
                const items = Array.from(source.querySelectorAll('.order-detail-media-source-item'));
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
    });

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
