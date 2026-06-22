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

            if (!frame) {
                return;
            }

            var availableWidth = Math.max(70, frame.clientWidth - 26);
            var availableHeight = Math.max(40, frame.clientHeight - 24);

            var scale = Math.min(availableWidth / widthMm, availableHeight / heightMm);

            if (!isFinite(scale) || scale <= 0) {
                return;
            }

            var pxW = Math.round(widthMm * scale);
            var pxH = Math.round(heightMm * scale);

            pxW = clamp(pxW, 54, availableWidth);
            pxH = clamp(pxH, 28, availableHeight);

            drawing.style.width = pxW + "px";
            drawing.style.height = pxH + "px";

            syncDimensionLines(frame, drawing);
        });
    }

    function syncDimensionLines(frame, drawing) {
        frame.style.setProperty("--drawing-left", drawing.offsetLeft + "px");
        frame.style.setProperty("--drawing-top", drawing.offsetTop + "px");
        frame.style.setProperty("--drawing-width", drawing.offsetWidth + "px");
        frame.style.setProperty("--drawing-height", drawing.offsetHeight + "px");
    }

    function clamp(value, min, max) {
        return Math.max(min, Math.min(value, max));
    }
})();
