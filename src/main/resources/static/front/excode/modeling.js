let scene, camera, renderer, controls;
let cabinetParts = [];
const raycaster = new THREE.Raycaster();
const mouse = new THREE.Vector2();
let selectedCircle = null;
const highlightMaterial = new THREE.MeshBasicMaterial({ color: 0xff8800, side: THREE.DoubleSide });
let originalMaterials = [];

function init() {
	scene = new THREE.Scene();
	camera = new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, 1000);
	renderer = new THREE.WebGLRenderer({ antialias: true });
	renderer.setSize(window.innerWidth, window.innerHeight);
	document.body.appendChild(renderer.domElement);

	controls = new THREE.OrbitControls(camera, renderer.domElement);
	renderer.setClearColor(0xffffff);

	camera.position.set(12, 12, 18);
	camera.lookAt(0, 0, 0);

	const light = new THREE.PointLight(0xffffff, 1);
	light.position.set(10, 15, 10);
	scene.add(light);

	const ambientLight = new THREE.AmbientLight(0x808080);
	scene.add(ambientLight);

	createAxesWithTicks();
	window.addEventListener("resize", onWindowResize);

	document.querySelectorAll(".circle").forEach((circle) => {
		circle.addEventListener("mousedown", function(event) {
			selectedCircle = event.target;
			document.addEventListener("mousemove", onMouseMove);
			document.addEventListener("mouseup", onMouseUp);
		});
	});

	animate();
}

function createCabinet() {
	cabinetParts.forEach((part) => scene.remove(part));
	cabinetParts = [];

	const userDepth = parseFloat(document.getElementById("depth").value);
	const userHeight = parseFloat(document.getElementById("height").value);
	const userWidth = parseFloat(document.getElementById("width").value);
	const userLegHeight = parseFloat(document.getElementById("legHeight").value);
	const userNumberOfDoors = parseInt(document.getElementById("numberOfDoors").value);

	const gapBetweenDoors = 10;
	const doorWidth = (userWidth - gapBetweenDoors * (userNumberOfDoors - 1)) / userNumberOfDoors;

	const material = new THREE.MeshBasicMaterial({
		color: 0x555555,
		side: THREE.DoubleSide,
	});

	for (let i = 0; i < userNumberOfDoors; i++) {
		const doorGeometry = new THREE.PlaneGeometry(doorWidth / 100, userHeight / 100);
		const door = new THREE.Mesh(doorGeometry, material);
		const doorXPosition = (i * (doorWidth + gapBetweenDoors)) - (userWidth / 2) + (doorWidth / 2);
		door.position.set(doorXPosition / 100, 0, userDepth / 200 - 0.05);
		door.name = `Door ${i + 1}`;
		cabinetParts.push(door);
		scene.add(door);
	}

	const backGeometry = new THREE.PlaneGeometry(userWidth / 100, userHeight / 100);
	const back = new THREE.Mesh(backGeometry, material);
	back.position.set(0, 0, -userDepth / 200);
	back.name = "Back";
	cabinetParts.push(back);
	scene.add(back);

	const sideGeometry = new THREE.PlaneGeometry(userDepth / 100, userHeight / 100);
	const left = new THREE.Mesh(sideGeometry, material);
	left.position.set(-userWidth / 200, 0, 0);
	left.rotation.y = Math.PI / 2;
	left.name = "Left Side";
	cabinetParts.push(left);
	scene.add(left);

	const right = left.clone();
	right.position.set(userWidth / 200, 0, 0);
	right.rotation.y = -Math.PI / 2;
	right.name = "Right Side";
	cabinetParts.push(right);
	scene.add(right);

	const topPanelGeometry = new THREE.PlaneGeometry(userWidth / 100, userDepth / 100);
	const topPanel = new THREE.Mesh(topPanelGeometry, material);
	topPanel.position.set(0, userHeight / 200, 0);
	topPanel.rotation.x = -Math.PI / 2;
	topPanel.name = "Top";
	cabinetParts.push(topPanel);
	scene.add(topPanel);

	const bottomPanelGeometry = new THREE.PlaneGeometry(userWidth / 100, userDepth / 100);
	const bottomPanel = new THREE.Mesh(bottomPanelGeometry, material);
	bottomPanel.position.set(0, -userHeight / 200, 0);
	bottomPanel.rotation.x = Math.PI / 2;
	bottomPanel.name = "Bottom";
	cabinetParts.push(bottomPanel);
	scene.add(bottomPanel);

	const legGeometry = new THREE.CylinderGeometry(20 / 100, 20 / 100, userLegHeight / 100, 32);
	const legMaterial = new THREE.MeshBasicMaterial({ color: 0x333333 });

	const legPositions = [
		[-userWidth / 200 + 30 / 100, -userHeight / 200 - userLegHeight / 200, userDepth / 200 - 30 / 100],
		[userWidth / 200 - 30 / 100, -userHeight / 200 - userLegHeight / 200, userDepth / 200 - 30 / 100],
		[-userWidth / 200 + 30 / 100, -userHeight / 200 - userLegHeight / 200, -userDepth / 200 + 30 / 100],
		[userWidth / 200 - 30 / 100, -userHeight / 200 - userLegHeight / 200, -userDepth / 200 + 30 / 100],
	];

	legPositions.forEach((position, index) => {
		const leg = new THREE.Mesh(legGeometry, legMaterial);
		leg.position.set(position[0], position[1], position[2]);
		leg.name = `Leg ${index + 1}`;
		cabinetParts.push(leg);
		scene.add(leg);
	});

	const edgesMaterial = new THREE.LineBasicMaterial({ color: 0x000000 });
	cabinetParts.forEach((part) => {
		const edges = new THREE.EdgesGeometry(part.geometry);
		const line = new THREE.LineSegments(edges, edgesMaterial);
		part.add(line);
	});

	originalMaterials = cabinetParts.map((part) => part.material);
}

function createAxesWithTicks() {
	const axisLength = 100;

	const xAxisGeometry = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(-axisLength, 0, 0), new THREE.Vector3(axisLength, 0, 0)]);
	const xAxisMaterial = new THREE.LineBasicMaterial({ color: 0x777b7e });
	const xAxis = new THREE.Line(xAxisGeometry, xAxisMaterial);
	scene.add(xAxis);

	const yAxisGeometry = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0, -axisLength, 0), new THREE.Vector3(0, axisLength, 0)]);
	const yAxisMaterial = new THREE.LineBasicMaterial({ color: 0x777b7e });
	const yAxis = new THREE.Line(yAxisGeometry, yAxisMaterial);
	scene.add(yAxis);

	const zAxisGeometry = new THREE.BufferGeometry().setFromPoints([new THREE.Vector3(0, 0, -axisLength), new THREE.Vector3(0, 0, axisLength)]);
	const zAxisMaterial = new THREE.LineBasicMaterial({ color: 0x777b7e });
	const zAxis = new THREE.Line(zAxisGeometry, zAxisMaterial);
	scene.add(zAxis);

	function addTicks(axis, tickSpacing, tickLength, color) {
		const tickPositions = [];
		const labels = [];

		for (let i = -axisLength; i <= axisLength; i += tickSpacing) {
			if (axis === "x") {
				tickPositions.push(new THREE.Vector3(i, 0, tickLength / 2), new THREE.Vector3(i, 0, -tickLength / 2));
				labels.push({ position: new THREE.Vector3(i, 0, 0), text: (i * 100).toString() });
			} else if (axis === "y") {
				tickPositions.push(new THREE.Vector3(0, i, tickLength / 2), new THREE.Vector3(0, i, -tickLength / 2));
				labels.push({ position: new THREE.Vector3(0, i, 0), text: (i * 100).toString() });
			} else if (axis === "z") {
				tickPositions.push(new THREE.Vector3(tickLength / 2, 0, i), new THREE.Vector3(-tickLength / 2, 0, i));
				labels.push({ position: new THREE.Vector3(0, 0, i), text: (i * 100).toString() });
			}
		}

		const tickGeometry = new THREE.BufferGeometry().setFromPoints(tickPositions);
		const tickMaterial = new THREE.LineBasicMaterial({ color: color });
		const ticks = new THREE.LineSegments(tickGeometry, tickMaterial);
		scene.add(ticks);

		labels.forEach((label) => {
			const sprite = createTextLabel(label.text, label.position, 1.5, color, 72);
			scene.add(sprite);
		});
	}

	addTicks("x", 1, 0.5, 0x000000);
	addTicks("y", 1, 0.5, 0x000000);
	addTicks("z", 1, 0.5, 0x000000);

	function createAxisLabel(text, position, color) {
		const sprite = createTextLabel(text, position, 2, color, 72);
		scene.add(sprite);
	}

	createAxisLabel("X+", new THREE.Vector3(axisLength + 5, 0, 0), "#000000");
	createAxisLabel("X-", new THREE.Vector3(-axisLength - 5, 0, 0), "#000000");
	createAxisLabel("Y+", new THREE.Vector3(0, axisLength + 5, 0), "#000000");
	createAxisLabel("Y-", new THREE.Vector3(0, -axisLength - 5, 0), "#000000");
	createAxisLabel("Z+", new THREE.Vector3(0, 0, axisLength + 5), "#000000");
	createAxisLabel("Z-", new THREE.Vector3(0, 0, -axisLength - 5), "#000000");
}

function createTextCanvas(text, color = "#808080", fontSize = 24, fontWeight = "normal") { // 기본 회색(#808080), 크기 24px, normal weight
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d");
    
    // 글꼴 크기 및 굵기 설정
    context.font = `${fontWeight} ${fontSize}px Arial`; // normal 또는 bold로 설정할 수 있음

    // 텍스트 색상 설정 (회색)
    context.fillStyle = color; 

    // 텍스트 그리기
    context.fillText(text, 0, fontSize);

    return canvas;
}

function createTextLabel(text, position, scale = 2, color = "#808080", fontSize = 24) {
    const canvas = createTextCanvas(text, color, fontSize, "normal"); // normal weight, 회색(#808080)
    const texture = new THREE.CanvasTexture(canvas);
    const spriteMaterial = new THREE.SpriteMaterial({ map: texture });
    const sprite = new THREE.Sprite(spriteMaterial);
    sprite.position.copy(position);

    // 텍스트 크기 조정 (scale)
    sprite.scale.set(scale, scale * 0.5, 1); 
    return sprite;
}


function onWindowResize() {
	const width = window.innerWidth;
	const height = window.innerHeight;
	renderer.setSize(width, height);
	camera.aspect = width / height;
	camera.updateProjectionMatrix();
	controls.update();
}

function onMouseMove(event) {
	if (selectedCircle) {
		const rect = renderer.domElement.getBoundingClientRect();
		selectedCircle.style.left = `${event.clientX - rect.left - 25}px`;
		selectedCircle.style.top = `${event.clientY - rect.top - 25}px`;
		updateIntersections();
	}
}

function onMouseUp() {
	document.removeEventListener("mousemove", onMouseMove);
	document.removeEventListener("mouseup", onMouseUp);
	selectedCircle = null;
	updateIntersections();
}

function updateIntersections() {
	if (selectedCircle) {
		const rect = renderer.domElement.getBoundingClientRect();
		const x = parseFloat(selectedCircle.style.left) + 25;
		const y = parseFloat(selectedCircle.style.top) + 25;

		mouse.x = ((x - rect.left) / renderer.domElement.clientWidth) * 2 - 1;
		mouse.y = -((y - rect.top) / renderer.domElement.clientHeight) * 2 + 1;

		raycaster.setFromCamera(mouse, camera);
		const intersects = raycaster.intersectObjects(cabinetParts);

		resetColors();

		if (intersects.length > 0) {
			const intersectedObject = intersects[0].object;
			intersectedObject.material = highlightMaterial;
			intersectedObject.renderOrder = 999;
		}
	} else {
		resetColors();
	}
}

function resetColors() {
	cabinetParts.forEach((part, index) => {
		part.material = originalMaterials[index];
		part.renderOrder = 0;
	});
}

function animate() {
	requestAnimationFrame(animate);
	controls.update();
	renderer.render(scene, camera);
}

document.getElementById("drawButton").addEventListener("click", createCabinet);

init();

document.getElementById("viewBlueprintButton").addEventListener("click", function() {
	const modal = document.getElementById("blueprintModal");
	modal.style.display = "block";

	const canvas = document.getElementById('modal-canvas');
	const canvasContainer = canvas.parentElement;

	const modalWidth = 1200;
	const modalHeight = 800;

	canvas.width = modalWidth;
	canvas.height = modalHeight;

	canvas.style.width = modalWidth + "px";
	canvas.style.height = modalHeight + "px";

	canvasContainer.style.width = modalWidth + "px";
	canvasContainer.style.height = modalHeight + "px";

	drawCabinetBlueprint();
});

document.querySelector(".close").addEventListener("click", function() {
	const modal = document.getElementById("blueprintModal");
	modal.style.display = "none";
});

function drawCabinetBlueprint() {
	const canvas = new fabric.Canvas('modal-canvas');

	const depth = parseFloat(document.getElementById('depth').value);
	const height = parseFloat(document.getElementById('height').value);
	const width = parseFloat(document.getElementById('width').value);
	const legHeight = parseFloat(document.getElementById('legHeight').value);
	const numberOfDoors = parseInt(document.getElementById('numberOfDoors').value);

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
		startY - 20 * scaleFactor,
		startXFrontView + bodyWidth * scaleFactor,
		startY - 20 * scaleFactor,
		`너비: ${width}mm`,
		0, -20 * scaleFactor
	);

	drawDimensionLine(
		startXFrontView - 20 * scaleFactor,
		startY,
		startXFrontView - 20 * scaleFactor,
		startY + (bodyHeight + legHeight * scale) * scaleFactor,
		`높이: ${height}mm`,
		-60 * scaleFactor, 0
	);

	drawDimensionLine(
		startXFrontView + bodyWidth * scaleFactor + 20 * scaleFactor,
		startY + bodyHeight * scaleFactor,
		startXFrontView + bodyWidth * scaleFactor + 20 * scaleFactor,
		startY + (bodyHeight + legHeight * scale) * scaleFactor,
		`다리 높이: ${legHeight}mm`,
		80 * scaleFactor, 0
	);

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
