(function (document) {
    'use strict';

    document.querySelectorAll('[data-pms-slider]').forEach(function (slider) {
        const viewport = slider.querySelector('[data-pms-viewport]');
        if (!viewport) return;
        slider.querySelectorAll('[data-pms-slide]').forEach(function (button) {
            button.addEventListener('click', function () {
                const direction = Number(button.dataset.pmsSlide || 0);
                viewport.scrollBy({
                    left: direction * Math.max(220, viewport.clientWidth * 0.88),
                    behavior: 'smooth'
                });
            });
        });
    });
})(document);
