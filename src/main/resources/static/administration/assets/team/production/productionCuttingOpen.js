/* productionCuttingOpen.js */

(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        var cuttingBtn = document.getElementById("team-production-material-cutting-btn");

        if (!cuttingBtn) {
            return;
        }

        refreshCuttingButtonState(cuttingBtn);

        cuttingBtn.addEventListener("click", function () {
            var config = window.teamProductionOverviewConfig || {};
            var cuttingUrl = config.cuttingUrl || "/team/productionList/cutting";

            var orderIds = getCurrentPageCuttableOrderIds();

            if (!orderIds.length) {
                alert("현재 조회된 오더 중 재단 가능한 오더가 없습니다.\n지원 대상: 클린/심플/소프트/코지/라운드, W 6xx, 여닫이, 인도어");
                return;
            }

            var url = new URL(cuttingUrl, window.location.origin);

            orderIds.forEach(function (orderId) {
                url.searchParams.append("orderIds", orderId);
            });

            window.open(url.toString(), "_blank", "noopener,noreferrer");
        });
    });

    function refreshCuttingButtonState(cuttingBtn) {
        var orderIds = getCurrentPageCuttableOrderIds();
        cuttingBtn.disabled = !orderIds.length;
        cuttingBtn.title = orderIds.length
            ? "현재 화면에서 재단 가능한 " + orderIds.length + "건만 출력합니다."
            : "현재 화면에 재단 가능한 오더가 없습니다.";
    }

    function getCurrentPageCuttableOrderIds() {
        var rows = document.querySelectorAll("#team-production-tbody tr.team-production-overview-row");
        var result = [];
        var exists = {};

        rows.forEach(function (row) {
            var orderId = row.getAttribute("data-overview-order-id");
            var cuttingAvailable = row.getAttribute("data-cutting-available") === "true";

            if (!orderId || !cuttingAvailable) {
                return;
            }

            if (exists[orderId]) {
                return;
            }

            exists[orderId] = true;
            result.push(orderId);
        });

        return result;
    }
})();
