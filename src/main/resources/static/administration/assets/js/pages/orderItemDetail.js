document.addEventListener("DOMContentLoaded", () => {
	const input = document.getElementById("adminImages");
	const previewArea = document.getElementById("preview-area");
	let fileList = [];

	input.addEventListener("change", (e) => {
		const newFiles = Array.from(e.target.files);
		fileList.push(...newFiles);

		// input 파일 초기화 후 새로 설정
		updateFileInput();
		renderPreviews();
	});

	function updateFileInput() {
		const dataTransfer = new DataTransfer();
		fileList.forEach(file => dataTransfer.items.add(file));
		input.files = dataTransfer.files;
	}

	function renderPreviews() {
		previewArea.innerHTML = "";

		fileList.forEach((file, index) => {
			const reader = new FileReader();
			reader.onload = e => {
				const div = document.createElement("div");
				div.className = "col-lg-2 position-relative";

				div.innerHTML = `
					<div class="border rounded p-2 d-flex flex-column align-items-center text-center">
						<button type="button" class="btn-close position-absolute top-0 end-0" aria-label="삭제" data-index="${index}"></button>
						<img src="${e.target.result}" class="img-fluid mb-2" style="max-height: 120px;" />
						<span class="text-truncate small">${file.name}</span>
					</div>
				`;

				div.querySelector(".btn-close").addEventListener("click", () => {
					fileList.splice(index, 1);
					updateFileInput();
					renderPreviews();
				});

				previewArea.appendChild(div);
			};
			reader.readAsDataURL(file);
		});
	}

	// ✅ 관리자 업로드 이미지 삭제 (DB + 서버파일)
	document.querySelectorAll(".delete-uploaded-img").forEach(btn => {
		btn.addEventListener("click", () => {
			const imageId = btn.getAttribute("data-id");
			if (!confirm("이미지를 삭제하시겠습니까?")) return;

			fetch(`/management/order-image/delete/${imageId}`, {
				method: "DELETE"
			})
				.then(res => {
					if (res.ok) {
						location.reload(); // ✅ 삭제 성공 시 페이지 새로고침
					} else {
						alert("삭제 실패");
					}
				});
		});
	});
});

// ==============================
// 회사 변경 → 멤버 드롭다운 갱신
// ==============================
(function() {
	const selCompany = document.getElementById("companyId");
	const selMember = document.getElementById("requesterMemberId");

	if (!selCompany || !selMember) return;

	function rebuildMemberOptions(members) {
		// 기존 옵션 초기화
		selMember.innerHTML = "";
		const opt0 = document.createElement("option");
		opt0.value = "";
		opt0.textContent = "신청자 선택";
		selMember.appendChild(opt0);

		members.forEach(m => {
			const opt = document.createElement("option");
			opt.value = m.id;
			opt.textContent = `${m.name} (${m.username})`;
			selMember.appendChild(opt);
		});

		// 첫 번째 실제 멤버 자동 선택(옵션)
		if (members.length > 0) {
			selMember.value = String(members[0].id);
		}
	}

	selCompany.addEventListener("change", () => {
		const cid = selCompany.value;
		if (!cid) {
			rebuildMemberOptions([]);
			return;
		}
		fetch(`/api/companies/${cid}/members`)
			.then(r => {
				if (!r.ok) throw new Error("멤버 조회 실패");
				return r.json();
			})
			.then(list => rebuildMemberOptions(list))
			.catch(err => {
				console.error(err);
				alert("대리점 멤버를 불러오지 못했습니다.");
			});
	});
})();
