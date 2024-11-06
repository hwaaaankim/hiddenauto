document.getElementById("viewBlueprintButton").addEventListener("click", function() {

	const canvas = document.getElementById('modal-canvas');
	const canvasContainer = canvas.parentElement;

	const width = window.innerWidth;
	const height = window.innerHeight;

	canvas.width = width;
	canvas.height = height;

	canvas.style.width = width + "px";
	canvas.style.height = height + "px";

	canvasContainer.style.width = width + "px";
	canvasContainer.style.height = height + "px";

	controlDrawBlueprint();
});

function controlDrawBlueprint() {
	const selectedCategory = document.querySelector('input[name="category"]:checked').value;

	if (selectedCategory === "하부장" || selectedCategory === "상부장" || selectedCategory === "슬라이드") {
		drawCabinetBlueprint(); // 현재의 그리기 함수를 사용
	} else if (selectedCategory === "플랩") {
		drawFlapBlueprint(); // 플랩용 별도 함수 호출
	} else if (selectedCategory === "거울") {
		drawMirrorBlueprint(); // 거울용 별도 함수 호출
	}
}

function drawCabinetBlueprint() {
	const canvas = new fabric.Canvas('modal-canvas');

	const depth = parseFloat(document.getElementById('depth').value);
	const height = parseFloat(document.getElementById('height').value);
	const width = parseFloat(document.getElementById('width').value);
	const legHeight = parseFloat(document.getElementById('legHeight').value);
	const numberOfDoors = parseInt(document.getElementById('numberOfDoors').value);

	const category = document.querySelector('input[name="category"]:checked').value;
	const showLegHeight = !(category === "상부장" || category === "슬라이드");

	const gapBetweenDoors = 10;
	const additionalGap = 200;
	const sideMargin = 50;
	const topBottomMargin = 50;
	const scale = 0.5;

	const canvasWidth = canvas.getWidth();
	const canvasHeight = canvas.getHeight();

	const heightText = new fabric.Text(`높이: ${height}mm`, {
		fontSize: 16 * scale,
		fill: '#000',
		selectable: false
	});
	const textWidth = heightText.width;

	const bodyWidth = width * scale;
	const bodyHeight = (height - legHeight) * scale;
	const topViewHeight = depth * scale;

	const frontViewTotalWidth = bodyWidth + textWidth;
	const totalWidth = frontViewTotalWidth + additionalGap + bodyWidth + sideMargin;
	const totalHeight = Math.max(bodyHeight + legHeight * scale, topViewHeight) + topBottomMargin * 2;

	let scaleFactor = 1;
	if (totalWidth > canvasWidth) {
		scaleFactor = canvasWidth / totalWidth;
	}
	if (totalHeight > canvasHeight) {
		scaleFactor = Math.min(scaleFactor, canvasHeight / totalHeight);
	}

	const leftHalfCenterX = (canvasWidth - (frontViewTotalWidth * scaleFactor + additionalGap * scaleFactor + bodyWidth * scaleFactor)) / 2 + frontViewTotalWidth * scaleFactor / 2;
	const rightHalfCenterX = leftHalfCenterX + frontViewTotalWidth * scaleFactor + additionalGap * scaleFactor;

	const startY = (canvasHeight - Math.max(bodyHeight + legHeight * scale, topViewHeight) * scaleFactor) / 2;
	const startXFrontView = leftHalfCenterX - (frontViewTotalWidth * scaleFactor) / 2 + textWidth * scaleFactor / 2;
	const startXTopView = startXFrontView + bodyWidth * scaleFactor + additionalGap * scaleFactor;

	const body = new fabric.Rect({
		left: startXFrontView,
		top: startY,
		width: bodyWidth * scaleFactor,
		height: bodyHeight * scaleFactor,
		fill: '#D3D3D3',
		stroke: '#000',
		strokeWidth: 2 * scaleFactor,
		selectable: false
	});
	canvas.add(body);

	if (showLegHeight && legHeight > 0) {
		const leg = new fabric.Rect({
			left: startXFrontView,
			top: startY + bodyHeight * scaleFactor,
			width: bodyWidth * scaleFactor,
			height: legHeight * scale * scaleFactor,
			fill: '#A9A9A9',
			stroke: '#000',
			strokeWidth: 2 * scaleFactor,
			selectable: false
		});
		canvas.add(leg);
	}

	const doorWidth = (width - gapBetweenDoors * (numberOfDoors - 1)) / numberOfDoors * scale * scaleFactor;
	const doorTextOffsetY = 15 * scaleFactor;
	for (let i = 0; i < numberOfDoors; i++) {
		const door = new fabric.Rect({
			left: startXFrontView + i * (doorWidth + gapBetweenDoors * scale * scaleFactor),
			top: startY,
			width: doorWidth,
			height: bodyHeight * scaleFactor,
			fill: '#FFFFFF',
			stroke: '#000',
			strokeWidth: 1 * scaleFactor,
			selectable: false
		});
		canvas.add(door);

		const doorCenterX = door.left + doorWidth / 2;
		const doorCenterY = door.top + (bodyHeight * scaleFactor) / 2;

		drawDimensionLine(
			door.left, doorCenterY,
			door.left + doorWidth, doorCenterY,
			`너비: ${(doorWidth / (scale * scaleFactor)).toFixed(0)}mm`,
			0, -doorTextOffsetY
		);
	}

	function drawDimensionLine(x1, y1, x2, y2, text, textOffsetX = 0, textOffsetY = 0) {
		const line = new fabric.Line([x1, y1, x2, y2], {
			stroke: '#000',
			strokeWidth: 1 * scaleFactor,
			selectable: false
		});
		canvas.add(line);

		const arrowSize = 10 * scaleFactor;
		const angle = Math.atan2(y2 - y1, x2 - x1);

		const arrow1 = new fabric.Line([
			x1, y1,
			x1 + arrowSize * Math.cos(angle + Math.PI / 6),
			y1 + arrowSize * Math.sin(angle + Math.PI / 6)
		], {
			stroke: '#000',
			strokeWidth: 1 * scaleFactor,
			selectable: false
		});
		const arrow2 = new fabric.Line([
			x1, y1,
			x1 + arrowSize * Math.cos(angle - Math.PI / 6),
			y1 + arrowSize * Math.sin(angle - Math.PI / 6)
		], {
			stroke: '#000',
			strokeWidth: 1 * scaleFactor,
			selectable: false
		});
		const arrow3 = new fabric.Line([
			x2, y2,
			x2 - arrowSize * Math.cos(angle + Math.PI / 6),
			y2 - arrowSize * Math.sin(angle + Math.PI / 6)
		], {
			stroke: '#000',
			strokeWidth: 1 * scaleFactor,
			selectable: false
		});
		const arrow4 = new fabric.Line([
			x2, y2,
			x2 - arrowSize * Math.cos(angle - Math.PI / 6),
			y2 - arrowSize * Math.sin(angle - Math.PI / 6)
		], {
			stroke: '#000',
			strokeWidth: 1 * scaleFactor,
			selectable: false
		});

		canvas.add(arrow1);
		canvas.add(arrow2);
		canvas.add(arrow3);
		canvas.add(arrow4);

		const textObj = new fabric.Text(text, {
			left: (x1 + x2) / 2 + textOffsetX,
			top: (y1 + y2) / 2 + textOffsetY,
			fontSize: 16 * scaleFactor,
			fill: '#000',
			selectable: false,
			originX: 'center',
			originY: 'center'
		});
		canvas.add(textObj);
	}

	drawDimensionLine(
		startXFrontView,
		startY + bodyHeight * scaleFactor + 20 * scaleFactor,
		startXFrontView + bodyWidth * scaleFactor,
		startY + bodyHeight * scaleFactor + 20 * scaleFactor,
		`너비: ${width}mm`,
		0, 20 * scaleFactor
	);

	drawDimensionLine(
		startXFrontView - 20 * scaleFactor,
		startY,
		startXFrontView - 20 * scaleFactor,
		startY + (bodyHeight + legHeight * scale) * scaleFactor,
		`높이: ${height}mm`,
		-60 * scaleFactor, 0
	);

	if (showLegHeight && legHeight > 0) {
		drawDimensionLine(
			startXFrontView + bodyWidth * scaleFactor + 20 * scaleFactor,
			startY + bodyHeight * scaleFactor,
			startXFrontView + bodyWidth * scaleFactor + 20 * scaleFactor,
			startY + (bodyHeight + legHeight * scale) * scaleFactor,
			`다리 높이: ${legHeight}mm`,
			80 * scaleFactor, 0
		);
	}

	const topView = new fabric.Rect({
		left: startXTopView,
		top: startY,
		width: bodyWidth * scaleFactor,
		height: topViewHeight * scaleFactor,
		fill: '#D3D3D3',
		stroke: '#000',
		strokeWidth: 2 * scaleFactor,
		selectable: false
	});
	canvas.add(topView);

	drawDimensionLine(
		startXTopView,
		startY + topViewHeight * scaleFactor + 20 * scaleFactor,
		startXTopView + bodyWidth * scaleFactor,
		startY + topViewHeight * scaleFactor + 20 * scaleFactor,
		`너비: ${width}mm`,
		0, 20 * scaleFactor
	);

	drawDimensionLine(
		startXTopView - 20 * scaleFactor,
		startY,
		startXTopView - 20 * scaleFactor,
		startY + topViewHeight * scaleFactor,
		`깊이: ${depth}mm`,
		-60 * scaleFactor, 0
	);
}

function drawMirrorBlueprint() {
	const canvas = new fabric.Canvas('modal-canvas');

	const width = parseFloat(document.getElementById('width').value);
	const height = parseFloat(document.getElementById('height').value);
	const shape = document.querySelector('input[name="mirrorShape"]:checked').value;

	const scale = 0.5;
	const canvasWidth = canvas.getWidth();
	const canvasHeight = canvas.getHeight();

	const bodyWidth = width * scale;
	const bodyHeight = height * scale;

	const startX = (canvasWidth - bodyWidth) / 2;
	const startY = (canvasHeight - bodyHeight) / 2;

	let mirror;

	// 선택한 모양에 따라 거울을 그리기
	if (shape === "트랙") {
		mirror = new fabric.Rect({
			left: startX,
			top: startY,
			rx: bodyWidth * 0.25,
			ry: bodyHeight * 0.25,
			width: bodyWidth,
			height: bodyHeight,
			fill: '#D3D3D3',
			stroke: '#000',
			strokeWidth: 2,
			selectable: false
		});
	} else if (shape === "타원") {
		mirror = new fabric.Ellipse({
			left: startX + bodyWidth / 2,
			top: startY + bodyHeight / 2,
			rx: bodyWidth / 2,
			ry: bodyHeight / 2,
			fill: '#D3D3D3',
			stroke: '#000',
			strokeWidth: 2,
			selectable: false,
			originX: 'center',
			originY: 'center'
		});
	} else if (shape === "사각형") {
		mirror = new fabric.Rect({
			left: startX,
			top: startY,
			width: bodyWidth,
			height: bodyHeight,
			fill: '#D3D3D3',
			stroke: '#000',
			strokeWidth: 2,
			selectable: false
		});
	} else if (shape === "다각") {
		mirror = createStarShape(startX + bodyWidth / 2, startY + bodyHeight / 2, bodyWidth, bodyHeight, 5);
	}

	canvas.add(mirror);

	// 너비와 높이 치수선 추가
	drawDimensionLine(
		startX,
		startY + bodyHeight + 20,
		startX + bodyWidth,
		startY + bodyHeight + 20,
		`너비: ${width}mm`,
		0, 20
	);

	drawDimensionLine(
		startX - 20,
		startY,
		startX - 20,
		startY + bodyHeight,
		`높이: ${height}mm`,
		-60, 0
	);

	// 치수선을 그리는 함수
	function drawDimensionLine(x1, y1, x2, y2, text, textOffsetX = 0, textOffsetY = 0) {
		const line = new fabric.Line([x1, y1, x2, y2], {
			stroke: '#000',
			strokeWidth: 1,
			selectable: false
		});
		canvas.add(line);

		const arrowSize = 10;
		const angle = Math.atan2(y2 - y1, x2 - x1);

		const arrow1 = new fabric.Line([
			x1, y1,
			x1 + arrowSize * Math.cos(angle + Math.PI / 6),
			y1 + arrowSize * Math.sin(angle + Math.PI / 6)
		], { stroke: '#000', strokeWidth: 1, selectable: false });
		const arrow2 = new fabric.Line([
			x1, y1,
			x1 + arrowSize * Math.cos(angle - Math.PI / 6),
			y1 + arrowSize * Math.sin(angle - Math.PI / 6)
		], { stroke: '#000', strokeWidth: 1, selectable: false });
		const arrow3 = new fabric.Line([
			x2, y2,
			x2 - arrowSize * Math.cos(angle + Math.PI / 6),
			y2 - arrowSize * Math.sin(angle + Math.PI / 6)
		], { stroke: '#000', strokeWidth: 1, selectable: false });
		const arrow4 = new fabric.Line([
			x2, y2,
			x2 - arrowSize * Math.cos(angle - Math.PI / 6),
			y2 - arrowSize * Math.sin(angle - Math.PI / 6)
		], { stroke: '#000', strokeWidth: 1, selectable: false });

		canvas.add(arrow1, arrow2, arrow3, arrow4);

		const textObj = new fabric.Text(text, {
			left: (x1 + x2) / 2 + textOffsetX,
			top: (y1 + y2) / 2 + textOffsetY,
			fontSize: 16,
			fill: '#000',
			selectable: false,
			originX: 'center',
			originY: 'center'
		});
		canvas.add(textObj);
	}

	// 별 모양 생성 함수 (가로, 세로 크기를 반영)
	function createStarShape(centerX, centerY, width, height) {
		const path = [];

		// 외곽 점 (규칙에 따라 배치)
		const topPoint = { x: centerX, y: centerY - height / 2 };                    // 상단 중앙 점
		const bottomLeftPoint = { x: centerX - width / 4, y: centerY + height / 2 }; // 하단 왼쪽 점
		const bottomRightPoint = { x: centerX + width / 4, y: centerY + height / 2 }; // 하단 오른쪽 점
		const leftPoint = { x: centerX - width / 2, y: centerY - height / 5 };       // 좌측 점
		const rightPoint = { x: centerX + width / 2, y: centerY - height / 5 };      // 우측 점

		// 내부 점 (대략적인 균형을 잡아 배치)
		const innerTopLeft = { x: (topPoint.x + leftPoint.x) / 2, y: (topPoint.y + leftPoint.y) / 2 };
		const innerTopRight = { x: (topPoint.x + rightPoint.x) / 2, y: (topPoint.y + rightPoint.y) / 2 };
		const innerBottomLeft = { x: (bottomLeftPoint.x + leftPoint.x) / 2, y: (bottomLeftPoint.y + leftPoint.y) / 2 };
		const innerBottomRight = { x: (bottomRightPoint.x + rightPoint.x) / 2, y: (bottomRightPoint.y + rightPoint.y) / 2 };
		const innerCenterBottom = { x: centerX, y: (bottomLeftPoint.y + bottomRightPoint.y) / 2 };

		// path 배열에 점들을 추가하여 순서대로 별 모양을 만듭니다
		path.push(topPoint);
		path.push(innerTopLeft);
		path.push(leftPoint);
		path.push(innerBottomLeft);
		path.push(bottomLeftPoint);
		path.push(innerCenterBottom);
		path.push(bottomRightPoint);
		path.push(innerBottomRight);
		path.push(rightPoint);
		path.push(innerTopRight);

		// Fabric.js Polygon으로 반환
		return new fabric.Polygon(path, {
			fill: '#D3D3D3',
			stroke: '#000',
			strokeWidth: 2,
			selectable: false,
			originX: 'center',
			originY: 'center'
		});
	}
}

function drawFlapBlueprint() {
	const canvas = new fabric.Canvas('modal-canvas');

	// 기본 입력값 가져오기
	const depth = parseFloat(document.getElementById('depth').value);
	const height = parseFloat(document.getElementById('height').value);
	const width = parseFloat(document.getElementById('width').value);
	const legHeight = parseFloat(document.getElementById('legHeight').value);
	const numberOfDoors = parseInt(document.getElementById('numberOfDoors').value);

	// 문 비율 입력값 가져오기
	const doorRatio1 = parseFloat(document.getElementById("doorRatio1").value) || 50;
	const doorRatio2 = 100 - doorRatio1;

	// 레이아웃 및 크기 설정
	const gapBetweenDoors = 10;
	const additionalGap = 200;
	const sideMargin = 50;
	const topBottomMargin = 50;
	const scale = 0.5;

	// 캔버스 크기
	const canvasWidth = canvas.getWidth();
	const canvasHeight = canvas.getHeight();

	// 본체 크기
	const bodyWidth = width * scale;
	const bodyHeight = (height - legHeight) * scale;
	const topViewHeight = depth * scale;

	// 문 갯수에 따른 너비 설정
	let doorWidths;
	if (numberOfDoors === 1) {
		doorWidths = [bodyWidth];
	} else if (numberOfDoors === 2) {
		doorWidths = [
			(bodyWidth * (doorRatio1 / 100)),
			(bodyWidth * (doorRatio2 / 100))
		];
	} else {
		console.error("Invalid number of doors for 플랩장");
		return;
	}

	// 캔버스 시작 위치
	const startX = (canvasWidth - bodyWidth) / 2;
	const startY = (canvasHeight - bodyHeight - legHeight * scale) / 2;

	// 캐비닛 본체 그리기
	const body = new fabric.Rect({
		left: startX,
		top: startY,
		width: bodyWidth,
		height: bodyHeight,
		fill: '#D3D3D3',
		stroke: '#000',
		strokeWidth: 2 * scale,
		selectable: false
	});
	canvas.add(body);

	// 다리 그리기 (있을 경우)
	if (legHeight > 0) {
		const leg = new fabric.Rect({
			left: startX,
			top: startY + bodyHeight,
			width: bodyWidth,
			height: legHeight * scale,
			fill: '#A9A9A9',
			stroke: '#000',
			strokeWidth: 2 * scale,
			selectable: false
		});
		canvas.add(leg);
	}

	// 문을 비율에 맞게 그리기
	let currentX = startX;
	doorWidths.forEach((doorWidth, index) => {
		const door = new fabric.Rect({
			left: currentX,
			top: startY,
			width: doorWidth,
			height: bodyHeight,
			fill: '#FFFFFF',
			stroke: '#000',
			strokeWidth: 1 * scale,
			selectable: false
		});
		canvas.add(door);

		// 각 문의 길이 표시
		const doorText = new fabric.Text(`문 너비: ${(doorWidth / scale).toFixed(0)}mm`, {
			left: currentX + doorWidth / 2,
			top: startY - 20 * scale,
			fontSize: 16,
			fill: '#000',
			selectable: false,
			originX: 'center',
			originY: 'center'
		});
		canvas.add(doorText);

		currentX += doorWidth + gapBetweenDoors * scale;
	});

	// 치수선 그리기 함수
	function drawDimensionLine(x1, y1, x2, y2, text, textOffsetX = 0, textOffsetY = 0) {
		const line = new fabric.Line([x1, y1, x2, y2], {
			stroke: '#000',
			strokeWidth: 1 * scale,
			selectable: false
		});
		canvas.add(line);

		const arrowSize = 10 * scale;
		const angle = Math.atan2(y2 - y1, x2 - x1);

		const arrow1 = new fabric.Line([
			x1, y1,
			x1 + arrowSize * Math.cos(angle + Math.PI / 6),
			y1 + arrowSize * Math.sin(angle + Math.PI / 6)
		], {
			stroke: '#000',
			strokeWidth: 1 * scale,
			selectable: false
		});
		const arrow2 = new fabric.Line([
			x1, y1,
			x1 + arrowSize * Math.cos(angle - Math.PI / 6),
			y1 + arrowSize * Math.sin(angle - Math.PI / 6)
		], {
			stroke: '#000',
			strokeWidth: 1 * scale,
			selectable: false
		});
		const arrow3 = new fabric.Line([
			x2, y2,
			x2 - arrowSize * Math.cos(angle + Math.PI / 6),
			y2 - arrowSize * Math.sin(angle + Math.PI / 6)
		], {
			stroke: '#000',
			strokeWidth: 1 * scale,
			selectable: false
		});
		const arrow4 = new fabric.Line([
			x2, y2,
			x2 - arrowSize * Math.cos(angle - Math.PI / 6),
			y2 - arrowSize * Math.sin(angle - Math.PI / 6)
		], {
			stroke: '#000',
			strokeWidth: 1 * scale,
			selectable: false
		});

		canvas.add(arrow1);
		canvas.add(arrow2);
		canvas.add(arrow3);
		canvas.add(arrow4);

		const textObj = new fabric.Text(text, {
			left: (x1 + x2) / 2 + textOffsetX,
			top: (y1 + y2) / 2 + textOffsetY,
			fontSize: 16,
			fill: '#000',
			selectable: false,
			originX: 'center',
			originY: 'center'
		});
		canvas.add(textObj);
	}

	// 너비 치수선 아랫면으로 이동
	drawDimensionLine(
		startX,
		startY + bodyHeight + 20 * scale,
		startX + bodyWidth,
		startY + bodyHeight + 20 * scale,
		`너비: ${width}mm`,
		0, 20 * scale
	);

	// 높이 치수선
	drawDimensionLine(
		startX - 20 * scale,
		startY,
		startX - 20 * scale,
		startY + bodyHeight + legHeight * scale,
		`높이: ${height}mm`,
		-60 * scale, 0
	);

	// 윗면 시점 그리기
	const topView = new fabric.Rect({
		left: startX + bodyWidth + additionalGap * scale,
		top: startY,
		width: bodyWidth,
		height: topViewHeight,
		fill: '#D3D3D3',
		stroke: '#000',
		strokeWidth: 2 * scale,
		selectable: false
	});
	canvas.add(topView);

	// 윗면 너비와 깊이 치수선
	drawDimensionLine(
		startX + bodyWidth + additionalGap * scale,
		startY + topViewHeight + 20 * scale,
		startX + 2 * bodyWidth + additionalGap * scale,
		startY + topViewHeight + 20 * scale,
		`너비: ${width}mm`,
		0, 20 * scale
	);

	drawDimensionLine(
		startX + bodyWidth + additionalGap * scale - 20 * scale,
		startY,
		startX + bodyWidth + additionalGap * scale - 20 * scale,
		startY + topViewHeight,
		`깊이: ${depth}mm`,
		-60 * scale, 0
	);
}

// 문의 비율 유효성 검사 함수
function updateDoorRatio() {
	const doorRatio1 = document.getElementById("doorRatio1");
	const doorRatio2 = document.getElementById("doorRatio2");

	function validateRatio() {
		let ratio1Value = parseInt(doorRatio1.value) || 0;
		let ratio2Value = parseInt(doorRatio2.value) || 0;

		// 각각의 값을 1~99 범위로 강제 조정
		ratio1Value = Math.min(99, Math.max(1, ratio1Value));
		ratio2Value = Math.min(99, Math.max(1, ratio2Value));

		// 합이 100이 되도록 조정
		if (ratio1Value + ratio2Value !== 100) {
			ratio2Value = 100 - ratio1Value;
		}

		doorRatio1.value = ratio1Value;
		doorRatio2.value = ratio2Value;
	}

	doorRatio1.addEventListener("input", validateRatio);
	doorRatio2.addEventListener("input", validateRatio);
	doorRatio1.addEventListener("blur", validateRatio);
	doorRatio2.addEventListener("blur", validateRatio);
}

// 카테고리별 UI 설정 및 초기화
document.querySelectorAll('input[name="category"]').forEach((radio) => {
	radio.addEventListener("change", (event) => {
		const selectedCategory = event.target.value;
		const mountTypeSelection = document.getElementById("mountTypeSelection");
		const depthLabel = document.getElementById("depthLabel");
		const numberOfDoorsLabel = document.getElementById("numberOfDoorsLabel");
		const mirrorShapeSelection = document.getElementById("mirrorShapeSelection");
		const flapDoorRatio = document.getElementById("flapDoorRatio");
		const legHeightInput = document.getElementById("legHeight");
		const depthInput = document.getElementById("depth");
		const numberOfDoorsInput = document.getElementById("numberOfDoors");

		// 선택된 카테고리에 따라 설정
		if (selectedCategory === "하부장") {
			mountTypeSelection.style.display = "flex";
			mirrorShapeSelection.style.display = "none";
			flapDoorRatio.style.display = "none";
			depthLabel.style.display = "block";
			numberOfDoorsLabel.style.display = "block";
			legHeightInput.value = "150";
			depthInput.value = "460";
			numberOfDoorsInput.value = "2";
		} else if (selectedCategory === "상부장" || selectedCategory === "슬라이드") {
			mountTypeSelection.style.display = "none";
			mirrorShapeSelection.style.display = "none";
			flapDoorRatio.style.display = "none";
			depthLabel.style.display = "block";
			numberOfDoorsLabel.style.display = "block";
			legHeightInput.value = "0";
			numberOfDoorsInput.value = "2";
		} else if (selectedCategory === "플랩") {
			mountTypeSelection.style.display = "none";
			mirrorShapeSelection.style.display = "none";
			flapDoorRatio.style.display = "none"; // 비율 필드는 초기에는 숨김
			depthLabel.style.display = "block";
			numberOfDoorsLabel.style.display = "block";
			legHeightInput.value = "0";
			numberOfDoorsInput.value = "1"; // 초기 문의 갯수를 1로 설정

			// 문의 갯수 변화 감지
			numberOfDoorsInput.addEventListener("input", () => {
				const doorCount = parseInt(numberOfDoorsInput.value) || 1;
				if (doorCount === 2) {
					flapDoorRatio.style.display = "flex"; // 문의 비율 표시
					updateDoorRatio(); // 유효성 검사 적용
				} else {
					flapDoorRatio.style.display = "none"; // 문의 비율 숨김
				}
			});
		} else if (selectedCategory === "거울") {
			mountTypeSelection.style.display = "none";
			mirrorShapeSelection.style.display = "flex";
			flapDoorRatio.style.display = "none";
			depthLabel.style.display = "none";
			numberOfDoorsLabel.style.display = "none";
			legHeightInput.value = "0";
			depthInput.value = "50";
			numberOfDoorsInput.value = "0";
		}
	});
});

// 다리형/벽걸이형 선택에 따른 다리 높이 설정
document.querySelectorAll('input[name="mountType"]').forEach((radio) => {
	radio.addEventListener("change", () => {
		const legHeightInput = document.getElementById("legHeight");
		if (document.getElementById("legged").checked) {
			legHeightInput.value = "150";
		} else {
			legHeightInput.value = "0";
		}
	});
});
