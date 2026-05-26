/* productionCuttingPage.js */

(function () {
    "use strict";

    var resizeTimer = null;

    document.addEventListener("DOMContentLoaded", function () {
        fitPanelDrawings();

        var printBtn = document.getElementById("production-cutting-print-btn");

        if (printBtn) {
            printBtn.addEventListener("click", function () {
                fitPanelDrawings();

                window.setTimeout(function () {
                    window.print();
                }, 80);
            });
        }
    });

    window.addEventListener("resize", function () {
        if (resizeTimer) {
            window.clearTimeout(resizeTimer);
        }

        resizeTimer = window.setTimeout(function () {
            fitPanelDrawings();
        }, 80);
    });

    function fitPanelDrawings() {
        var drawings = document.querySelectorAll(".cutting-panel-drawing");

        drawings.forEach(function (drawing) {
            var widthMm = parseInt(drawing.getAttribute("data-width") || "0", 10);
            var heightMm = parseInt(drawing.getAttribute("data-height") || "0", 10);

            if (!widthMm || !heightMm) {
                return;
            }

            var frame = drawing.closest(".cutting-panel-measure-frame");
            var stage = drawing.closest(".cutting-panel-stage");

            if (!frame || !stage) {
                return;
            }

            /*
             * 기존에는 점선/라벨 공간을 과하게 빼서 도면이 작아졌습니다.
             * 이제 점선은 실제 도면 바로 바깥에 붙이므로 최소 여백만 제외합니다.
             */
            var availableWidth = Math.max(100, frame.clientWidth - 34);
            var availableHeight = Math.max(64, frame.clientHeight - 30);

            var scale = Math.min(availableWidth / widthMm, availableHeight / heightMm);

            if (!isFinite(scale) || scale <= 0) {
                return;
            }

            var pxW = Math.round(widthMm * scale);
            var pxH = Math.round(heightMm * scale);

            pxW = clamp(pxW, 88, availableWidth);
            pxH = clamp(pxH, 44, availableHeight);

            drawing.style.width = pxW + "px";
            drawing.style.height = pxH + "px";

            syncDimensionLines(frame, drawing);
        });
    }

    function syncDimensionLines(frame, drawing) {
        /*
         * 핵심:
         * 점선 위치를 프레임 기준이 아니라 실제 drawing의 offset 기준으로 맞춥니다.
         * 그래서 점선의 시작/끝이 도면의 꼭지점과 정확히 일치합니다.
         */
        frame.style.setProperty("--drawing-left", drawing.offsetLeft + "px");
        frame.style.setProperty("--drawing-top", drawing.offsetTop + "px");
        frame.style.setProperty("--drawing-width", drawing.offsetWidth + "px");
        frame.style.setProperty("--drawing-height", drawing.offsetHeight + "px");
    }

    function clamp(value, min, max) {
        return Math.max(min, Math.min(value, max));
    }
})();