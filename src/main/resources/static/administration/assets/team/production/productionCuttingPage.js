(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        fitPanelDrawings();

        var printBtn = document.getElementById("production-cutting-print-btn");

        if (printBtn) {
            printBtn.addEventListener("click", function () {
                fitPanelDrawings();
                window.print();
            });
        }
    });

    window.addEventListener("resize", function () {
        fitPanelDrawings();
    });

    function fitPanelDrawings() {
        var drawings = document.querySelectorAll(".cutting-panel-drawing");

        drawings.forEach(function (drawing) {
            var widthMm = parseInt(drawing.getAttribute("data-width") || "0", 10);
            var heightMm = parseInt(drawing.getAttribute("data-height") || "0", 10);

            if (!widthMm || !heightMm) {
                return;
            }

            var stage = drawing.closest(".cutting-panel-stage");

            if (!stage) {
                return;
            }

            var stageWidth = Math.max(120, stage.clientWidth - 18);
            var stageHeight = Math.max(70, stage.clientHeight - 18);

            var scale = Math.min(stageWidth / widthMm, stageHeight / heightMm);

            var pxW = Math.max(80, Math.round(widthMm * scale));
            var pxH = Math.max(42, Math.round(heightMm * scale));

            drawing.style.width = pxW + "px";
            drawing.style.height = pxH + "px";
        });
    }
})();