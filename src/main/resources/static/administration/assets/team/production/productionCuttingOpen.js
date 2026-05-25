(function () {
    "use strict";

    document.addEventListener("DOMContentLoaded", function () {
        var cuttingBtn = document.getElementById("team-production-material-cutting-btn");

        if (!cuttingBtn) {
            return;
        }

        cuttingBtn.addEventListener("click", function () {
            var config = window.teamProductionOverviewConfig || {};
            var cuttingUrl = config.cuttingUrl || "/team/productionList/cutting";

            var orderIds = getCurrentPageOrderIds();

            if (!orderIds.length) {
                alert("현재 조회된 오더가 없습니다.");
                return;
            }

            var url = new URL(cuttingUrl, window.location.origin);

            orderIds.forEach(function (orderId) {
                url.searchParams.append("orderIds", orderId);
            });

            window.open(url.toString(), "_blank", "noopener,noreferrer");
        });
    });

    function getCurrentPageOrderIds() {
        var rows = document.querySelectorAll("#team-production-tbody tr.team-production-overview-row");
        var result = [];
        var exists = {};

        rows.forEach(function (row) {
            var orderId = row.getAttribute("data-overview-order-id");

            if (!orderId) {
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