document.addEventListener("DOMContentLoaded", function () {
	const rows = document.querySelectorAll(".delivery-manager-added-row");

	rows.forEach(function (row) {
		row.addEventListener("click", function (event) {
			const ignored = event.target.closest("a, button, input, select, textarea, label");

			if (ignored) {
				return;
			}

			const href = row.getAttribute("data-href");

			if (!href) {
				return;
			}

			window.location.href = href;
		});

		row.addEventListener("keydown", function (event) {
			if (event.key !== "Enter") {
				return;
			}

			const href = row.getAttribute("data-href");

			if (!href) {
				return;
			}

			window.location.href = href;
		});

		row.setAttribute("tabindex", "0");
	});
});