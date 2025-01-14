let scene, camera, renderer, controls;
let cabinetParts = [];
const raycaster = new THREE.Raycaster();
const mouse = new THREE.Vector2();
let selectedCircle = null;
const highlightMaterial = new THREE.MeshBasicMaterial({ color: 0xff8800, side: THREE.DoubleSide });
let originalMaterials = [];


function createCabinetFromData(data) {
    // 데이터 파싱
    const category = data.category || '하부장';
    const width = parseFloat(data.width) || 500;
    const height = parseFloat(data.height) || 800;
    const depth = parseFloat(data.depth) || 400;
    const legHeight = 'leg' ? 150 : 0;
    const numberOfDoors = parseInt(data.numberOfDoors) || 2;
    const doorRatio1 = parseInt(data.doorRatio1) || 50;
    const doorRatio2 = parseInt(data.doorRatio2) || 50;
    const mirrorShape = data.mirrorShape || '사각형';
	const categoryName = category.label;
    // 기존 캐비닛 파트 제거
    cabinetParts.forEach((part) => scene.remove(part));
    cabinetParts = [];

    // 카테고리에 따라 적절한 함수 호출
    if (categoryName === "하부장" || categoryName === "상부장" || categoryName === "슬라이드장") {
        createStandardCabinet(width, height, depth, legHeight, numberOfDoors);
    } else if (categoryName === "플랩장") {
        drawFlapCabinet(width, height, depth, numberOfDoors, doorRatio1, doorRatio2);
    } else if (categoryName === "거울") {
        drawMirror(width, height, mirrorShape);
    }

    // 원래 재질 저장
    originalMaterials = cabinetParts.map((part) => part.material);
}

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

// 외곽선을 추가하는 함수
function addEdgesToCabinetParts() {
	const edgesMaterial = new THREE.LineBasicMaterial({ color: 0x000000 });
	cabinetParts.forEach((part) => {
		const edges = new THREE.EdgesGeometry(part.geometry);
		const line = new THREE.LineSegments(edges, edgesMaterial);
		part.add(line);
	});
}

// 표준 캐비닛 생성 함수 (하부장, 상부장, 슬라이드 공통)
function createStandardCabinet(width, height, depth, legHeight, numberOfDoors) {
	console.log('width : ', width);
	console.log('height : ', height);
	console.log('depth : ', depth);
	console.log('legHeight : ', legHeight);
	console.log('numberOfDoors : ', numberOfDoors);
	
	cabinetParts = [];  // 기존 캐비닛 부분 초기화

	const gapBetweenDoors = 10;
	const doorWidth = (width - gapBetweenDoors * (numberOfDoors - 1)) / numberOfDoors;
	const material = new THREE.MeshBasicMaterial({ color: 0x555555, side: THREE.DoubleSide });

	// 문 생성
	for (let i = 0; i < numberOfDoors; i++) {
        try {
            const doorGeometry = new THREE.PlaneGeometry(doorWidth / 100, height / 100);
            const door = new THREE.Mesh(doorGeometry, material);
            const doorXPosition = (i * (doorWidth + gapBetweenDoors)) - (width / 2) + (doorWidth / 2);
            door.position.set(doorXPosition / 100, 0, depth / 200 - 0.05);
            door.name = `Door ${i + 1}`;
            if (door) {
                cabinetParts.push(door);
                scene.add(door);
            }
        } catch (error) {
            console.error(`문 생성 중 오류 발생: ${error.message}`);
        }
    }

	// 나머지 캐비닛 구성요소 생성 (뒤, 양쪽, 상하)
	const backGeometry = new THREE.PlaneGeometry(width / 100, height / 100);
	const back = new THREE.Mesh(backGeometry, material);
	back.position.set(0, 0, -depth / 200);
	cabinetParts.push(back);
	scene.add(back);

	const sideGeometry = new THREE.PlaneGeometry(depth / 100, height / 100);
	const left = new THREE.Mesh(sideGeometry, material);
	left.position.set(-width / 200, 0, 0);
	left.rotation.y = Math.PI / 2;
	cabinetParts.push(left);
	scene.add(left);

	const right = left.clone();
	right.position.set(width / 200, 0, 0);
	right.rotation.y = -Math.PI / 2;
	cabinetParts.push(right);
	scene.add(right);

	const topPanelGeometry = new THREE.PlaneGeometry(width / 100, depth / 100);
	const topPanel = new THREE.Mesh(topPanelGeometry, material);
	topPanel.position.set(0, height / 200, 0);
	topPanel.rotation.x = -Math.PI / 2;
	cabinetParts.push(topPanel);
	scene.add(topPanel);

	const bottomPanelGeometry = new THREE.PlaneGeometry(width / 100, depth / 100);
	const bottomPanel = new THREE.Mesh(bottomPanelGeometry, material);
	bottomPanel.position.set(0, -height / 200, 0);
	bottomPanel.rotation.x = Math.PI / 2;
	cabinetParts.push(bottomPanel);
	scene.add(bottomPanel);

	// 다리 추가 (다리형에 한함)
	if (legHeight > 0) {
		const legGeometry = new THREE.CylinderGeometry(20 / 100, 20 / 100, legHeight / 100, 32);
		const legMaterial = new THREE.MeshBasicMaterial({ color: 0x333333 });

		const legPositions = [
			[-width / 200 + 30 / 100, -height / 200 - legHeight / 200, depth / 200 - 30 / 100],
			[width / 200 - 30 / 100, -height / 200 - legHeight / 200, depth / 200 - 30 / 100],
			[-width / 200 + 30 / 100, -height / 200 - legHeight / 200, -depth / 200 + 30 / 100],
			[width / 200 - 30 / 100, -height / 200 - legHeight / 200, -depth / 200 + 30 / 100],
		];

		legPositions.forEach((position, index) => {
			const leg = new THREE.Mesh(legGeometry, legMaterial);
			leg.position.set(position[0], position[1], position[2]);
			cabinetParts.push(leg);
			scene.add(leg);
		});
	}

	// 외곽선 추가
	addEdgesToCabinetParts();
}

function drawMirror(width, height, mirrorShape) {
	cabinetParts = [];  // 기존 거울 파트 초기화
	const material = new THREE.MeshBasicMaterial({ color: 0xaaaaaa, side: THREE.DoubleSide }); // 거울용 기본 색상
	const depth = 0.5; // 고정 깊이

	let geometry;

	switch (mirrorShape) {
		case "사각형":
			geometry = new THREE.PlaneGeometry(width / 100, height / 100);
			break;
		case "타원":
			geometry = createEllipseGeometry(width / 100, height / 100); // 타원을 그릴 때 비율 적용
			break;
		case "다각":
			geometry = createPentagonGeometry(width / 100, height / 100); // 별을 그릴 때 비율 적용
			break;
		case "트랙":  // 트랙 선택 시 윗아래 원형 직사각형으로 생성
			geometry = createRoundedRectangleGeometry(width / 100, height / 100, 0.25 * Math.min(width, height) / 100);
			break;
		default:
			console.warn("알 수 없는 거울 형태입니다.");
			return;
	}

	const mirror = new THREE.Mesh(geometry, material);
	mirror.position.set(0, 0, depth / 2); // 거울 위치 설정
	cabinetParts.push(mirror);
	scene.add(mirror);

	// 외곽선 추가
	addEdgesToCabinetParts();
}

// 타원 모양의 거울을 생성하는 함수
function createEllipseGeometry(width, height) {
	const shape = new THREE.Shape();
	shape.absellipse(0, 0, width / 2, height / 2, 0, Math.PI * 2, false, 0);
	return new THREE.ShapeGeometry(shape);
}

function createPentagonGeometry(width, height) {
    const shape = new THREE.Shape();
    const outerRadiusX = width / 2;
    const outerRadiusY = height / 2;
    const points = 5; // 오각형

    for (let i = 0; i < points; i++) {
        const angle = (i * 2 * Math.PI) / points + Math.PI / 2; // +90도 회전하여 하단을 향하도록 설정
        const x = outerRadiusX * Math.cos(angle);
        const y = outerRadiusY * Math.sin(angle);
        i === 0 ? shape.moveTo(x, y) : shape.lineTo(x, y);
    }
    shape.closePath();

    return new THREE.ShapeGeometry(shape);
}



// 비율을 적용한 별 모양의 거울을 생성하는 함수
function createStarGeometry(width, height) {
	const shape = new THREE.Shape();
	const outerRadiusX = width / 2;
	const outerRadiusY = height / 2;
	const innerRadiusX = outerRadiusX / 2.5;
	const innerRadiusY = outerRadiusY / 2.5;
	const points = 5;

	for (let i = 0; i < points * 2; i++) {
		const angle = (i * Math.PI) / points;
		const radiusX = i % 2 === 0 ? outerRadiusX : innerRadiusX;
		const radiusY = i % 2 === 0 ? outerRadiusY : innerRadiusY;
		const x = radiusX * Math.cos(angle);
		const y = radiusY * Math.sin(angle);
		i === 0 ? shape.moveTo(x, y) : shape.lineTo(x, y);
	}
	shape.closePath();

	return new THREE.ShapeGeometry(shape);
}
// 윗부분과 아랫부분이 완전히 반원인 직사각형 모양 생성 함수
function createRoundedRectangleGeometry(width, height) {
	const shape = new THREE.Shape();
	const halfWidth = width / 2;
	const halfHeight = height / 2;
	const radius = halfWidth; // 반원의 반지름을 너비 절반으로 설정

	// 모양 그리기 - 상하 반원과 좌우 직선
	shape.moveTo(-halfWidth, halfHeight - radius); // 시작점
	shape.quadraticCurveTo(-halfWidth, halfHeight, 0, halfHeight); // 상단 반원 좌측
	shape.quadraticCurveTo(halfWidth, halfHeight, halfWidth, halfHeight - radius); // 상단 반원 우측

	shape.lineTo(halfWidth, -halfHeight + radius); // 오른쪽 직선
	shape.quadraticCurveTo(halfWidth, -halfHeight, 0, -halfHeight); // 하단 반원 우측
	shape.quadraticCurveTo(-halfWidth, -halfHeight, -halfWidth, -halfHeight + radius); // 하단 반원 좌측

	shape.lineTo(-halfWidth, halfHeight - radius); // 왼쪽 직선

	return new THREE.ShapeGeometry(shape);
}



function drawFlapCabinet(width, height, depth, numberOfDoors, doorRatio1, doorRatio2) {
	cabinetParts = [];  // 기존 캐비닛 부분 초기화

	const gapBetweenDoors = 10;
	const material = new THREE.MeshBasicMaterial({ color: 0x555555, side: THREE.DoubleSide });

	// 문 생성
	if (numberOfDoors === 1) {
		// 문의 개수가 1인 경우
		const doorGeometry = new THREE.PlaneGeometry(width / 100, height / 100);
		const door = new THREE.Mesh(doorGeometry, material);
		door.position.set(0, 0, depth / 200 - 0.05);
		cabinetParts.push(door);
		scene.add(door);
	} else if (numberOfDoors === 2) {
		// 문의 개수가 2인 경우 - 비율 적용
		const doorWidth1 = (width * (doorRatio1 / 100) - gapBetweenDoors / 2) / 100;
		const doorWidth2 = (width * (doorRatio2 / 100) - gapBetweenDoors / 2) / 100;

		const doorGeometry1 = new THREE.PlaneGeometry(doorWidth1, height / 100);
		const door1 = new THREE.Mesh(doorGeometry1, material);
		door1.position.set(-doorWidth2 / 2 - gapBetweenDoors / 200, 0, depth / 200 - 0.05);
		cabinetParts.push(door1);
		scene.add(door1);

		const doorGeometry2 = new THREE.PlaneGeometry(doorWidth2, height / 100);
		const door2 = new THREE.Mesh(doorGeometry2, material);
		door2.position.set(doorWidth1 / 2 + gapBetweenDoors / 200, 0, depth / 200 - 0.05);
		cabinetParts.push(door2);
		scene.add(door2);
	}

	// 나머지 캐비닛 구성요소 생성 (뒤, 양쪽, 상하)
	const backGeometry = new THREE.PlaneGeometry(width / 100, height / 100);
	const back = new THREE.Mesh(backGeometry, material);
	back.position.set(0, 0, -depth / 200);
	cabinetParts.push(back);
	scene.add(back);

	const sideGeometry = new THREE.PlaneGeometry(depth / 100, height / 100);
	const left = new THREE.Mesh(sideGeometry, material);
	left.position.set(-width / 200, 0, 0);
	left.rotation.y = Math.PI / 2;
	cabinetParts.push(left);
	scene.add(left);

	const right = left.clone();
	right.position.set(width / 200, 0, 0);
	right.rotation.y = -Math.PI / 2;
	cabinetParts.push(right);
	scene.add(right);

	const topPanelGeometry = new THREE.PlaneGeometry(width / 100, depth / 100);
	const topPanel = new THREE.Mesh(topPanelGeometry, material);
	topPanel.position.set(0, height / 200, 0);
	topPanel.rotation.x = -Math.PI / 2;
	cabinetParts.push(topPanel);
	scene.add(topPanel);

	const bottomPanelGeometry = new THREE.PlaneGeometry(width / 100, depth / 100);
	const bottomPanel = new THREE.Mesh(bottomPanelGeometry, material);
	bottomPanel.position.set(0, -height / 200, 0);
	bottomPanel.rotation.x = Math.PI / 2;
	cabinetParts.push(bottomPanel);
	scene.add(bottomPanel);

	// 외곽선 추가
	addEdgesToCabinetParts();
}


// 컨트롤 함수에서 각 유형에 맞는 함수를 호출
function createCabinet() {
	const category = document.querySelector('input[name="category"]:checked').value;
	const width = parseFloat(document.getElementById("width").value);
	const height = parseFloat(document.getElementById("height").value);
	const depth = parseFloat(document.getElementById("depth").value);
	const legHeight = parseFloat(document.getElementById("legHeight").value);
	const numberOfDoors = parseInt(document.getElementById("numberOfDoors").value);
	const doorRatio1 = parseInt(document.getElementById("doorRatio1")?.value) || 0;
	const doorRatio2 = parseInt(document.getElementById("doorRatio2")?.value) || 0;
	const mirrorShape = document.querySelector('input[name="mirrorShape"]:checked')?.value;

	cabinetParts.forEach((part) => scene.remove(part)); // 기존 제거

	if (category === "하부장" || category === "상부장" || category === "슬라이드") {
		createStandardCabinet(width, height, depth, legHeight, numberOfDoors);
	} else if (category === "플랩") {
		drawFlapCabinet(width, height, depth, numberOfDoors, doorRatio1, doorRatio2);
	} else if (category === "거울") {
		drawMirror(width, height, mirrorShape);
	}
	originalMaterials = cabinetParts.map((part) => part.material);
}

function createTextCanvas(text, color = "#808080", fontSize = 24, fontWeight = "normal") { // 기본 회색(#808080), 크기 24px, normal weight
	const canvas = document.createElement("canvas");
	const context = canvas.getContext("2d");

	context.font = `${fontWeight} ${fontSize}px Arial`; // normal 또는 bold로 설정할 수 있음
	context.fillStyle = color;
	context.fillText(text, 0, fontSize);

	return canvas;
}

function createTextLabel(text, position, scale = 2, color = "#808080", fontSize = 24) {
	const canvas = createTextCanvas(text, color, fontSize, "normal"); // normal weight, 회색(#808080)
	const texture = new THREE.CanvasTexture(canvas);
	const spriteMaterial = new THREE.SpriteMaterial({ map: texture });
	const sprite = new THREE.Sprite(spriteMaterial);
	sprite.position.copy(position);

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

init();
createCabinetFromData(selectedData);
