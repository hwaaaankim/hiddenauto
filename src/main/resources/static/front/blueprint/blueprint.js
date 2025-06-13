
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

function parseData(data) {
    const category = data.category || { label: '하부장', value: 'low' };

    let doorRatio1 = 50;
    let doorRatio2 = 50;

    if (category.label === '플랩장' && typeof data.doorRatio === 'string' && data.doorRatio.includes(':')) {
        const [val1, val2] = data.doorRatio.split(':').map(v => parseInt(v.trim()));
        if (!isNaN(val1) && !isNaN(val2)) {
            doorRatio1 = val1; // 💡 mm 단위 그대로
            doorRatio2 = val2;
        }
    }

    return {
        categoryName: category.label,
        width: parseFloat(data.width) || 500,
        height: parseFloat(data.height) || 800,
        depth: parseFloat(data.depth) || 400,
        legHeight: data.form === 'leg' ? 180 : 0,
        numberOfDoors: parseInt(data.numberOfDoors) || 2,
        doorRatio1: doorRatio1,
        doorRatio2: doorRatio2,
        mirrorShape: data.mirrorShape || '사각형'
    };
}

function controlDrawBlueprint(data) {
	const parsedData = parseData(data);

    if (parsedData.categoryName === "슬라이드장" || parsedData.categoryName === "상부장" || parsedData.categoryName === "하부장") {
        drawCabinetBlueprint(parsedData);
    } else if (parsedData.categoryName === "플랩장") {
        drawFlapBlueprint(parsedData);
    } else if (parsedData.categoryName === "거울") {
        drawMirrorBlueprint(parsedData);
    }
}

function drawCabinetBlueprint({ width, height, depth, legHeight, numberOfDoors, categoryName }) {
    const canvas = new fabric.Canvas('modal-canvas');

    const showLegHeight = !(categoryName === "상부장" || categoryName === "슬라이드");
    const gapBetweenDoors = 0;
    const additionalGap = 200;
    const sideMargin = 50;
    const topBottomMargin = 50;
    const scale = 0.4;

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

    if (showLegHeight && legHeight > 0 && categoryName === '하부장') {
	    const legWidth = 20 * scale * scaleFactor;
	    const legHeightScaled = legHeight * scale * scaleFactor;
	    const bodyLeft = startXFrontView;
	    const bodyRight = startXFrontView + bodyWidth * scaleFactor;
	    const legY = startY + bodyHeight * scaleFactor;
	
	    const legs = [
	        bodyLeft,
	        bodyRight - legWidth
	    ];
	
	    legs.forEach(x => {
	        // 왼쪽 세로선
	        canvas.add(new fabric.Line([x, legY, x, legY + legHeightScaled], {
	            stroke: '#000',
	            strokeWidth: 2 * scaleFactor,
	            selectable: false
	        }));
	        // 오른쪽 세로선
	        canvas.add(new fabric.Line([x + legWidth, legY, x + legWidth, legY + legHeightScaled], {
	            stroke: '#000',
	            strokeWidth: 2 * scaleFactor,
	            selectable: false
	        }));
	        // 윗변
	        canvas.add(new fabric.Line([x, legY, x + legWidth, legY], {
	            stroke: '#000',
	            strokeWidth: 2 * scaleFactor,
	            selectable: false
	        }));
	        // ✅ 추가: 아랫변
	        canvas.add(new fabric.Line([x, legY + legHeightScaled, x + legWidth, legY + legHeightScaled], {
	            stroke: '#000',
	            strokeWidth: 2 * scaleFactor,
	            selectable: false
	        }));
	    });
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


function drawMirrorBlueprint({ width, height, mirrorShape }) {
    const canvas = new fabric.Canvas('modal-canvas');

    const scale = 0.5;
    const canvasWidth = canvas.getWidth();
    const canvasHeight = canvas.getHeight();

    const bodyWidth = width * scale;
    const bodyHeight = height * scale;

    const startX = (canvasWidth - bodyWidth) / 2;
    const startY = (canvasHeight - bodyHeight) / 2;

    let mirror;

    // 선택한 모양에 따라 거울을 그리기
    if (mirrorShape === "트랙") {
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
    } else if (mirrorShape === "타원") {
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
    } else if (mirrorShape === "사각형") {
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
    } else if (mirrorShape === "다각") {
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
function drawFlapBlueprint({ width, height, depth, legHeight, numberOfDoors, doorRatio1 = 50, doorRatio2 = 50 }) {

    const canvas = new fabric.Canvas('modal-canvas');

    // 여백 및 스케일
    const gapBetweenDoors = 0;
    const additionalGap = 200;
    const sideMargin = 50;
    const topBottomMargin = 50;
    const scale = 0.4;

    // 본체 크기
    const bodyWidth = width * scale;
    const bodyHeight = (height - legHeight) * scale;
    const topViewHeight = depth * scale;

    // 문 갯수에 따른 너비 설정
    let doorWidths;
    if (numberOfDoors === 1) {
        doorWidths = [width];
    } else if (numberOfDoors === 2) {
        const total = doorRatio1 + doorRatio2;
        if (total > 0) {
            doorWidths = [
                width * (doorRatio1 / total),
                width * (doorRatio2 / total)
            ].map(w => w * 0.4); // scale 적용
        }
    } else {
        console.error("Invalid number of doors for 플랩장");
        return;
    }

    // 캔버스 크기
    const canvasWidth = canvas.getWidth();
    const canvasHeight = canvas.getHeight();

    // 도면의 전체 크기 계산
    const frontViewWidth = bodyWidth;
    const totalWidth = frontViewWidth + additionalGap + bodyWidth;
    const totalHeight = Math.max(bodyHeight + legHeight * scale, topViewHeight);

    // 스케일 조정
    let scaleFactor = 1;
    if (totalWidth + sideMargin * 2 > canvasWidth) {
        scaleFactor = canvasWidth / (totalWidth + sideMargin * 2);
    }
    if (totalHeight + topBottomMargin * 2 > canvasHeight) {
        scaleFactor = Math.min(scaleFactor, canvasHeight / (totalHeight + topBottomMargin * 2));
    }

    // 중앙 정렬 좌표
    const startXFrontView = (canvasWidth - (frontViewWidth * scaleFactor + additionalGap * scaleFactor + bodyWidth * scaleFactor)) / 2;
    const startY = (canvasHeight - (totalHeight * scaleFactor)) / 2;
    const startXTopView = startXFrontView + frontViewWidth * scaleFactor + additionalGap * scaleFactor;

    // 캐비닛 본체 그리기
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

    // 다리 그리기 (있을 경우)
    if (legHeight > 0) {
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

    // 문을 비율에 맞게 그리기
    let currentX = startXFrontView;
    doorWidths.forEach((doorWidth, index) => {
        const door = new fabric.Rect({
            left: currentX,
            top: startY,
            width: doorWidth * scaleFactor,
            height: bodyHeight * scaleFactor,
            fill: '#FFFFFF',
            stroke: '#000',
            strokeWidth: 1 * scaleFactor,
            selectable: false
        });
        canvas.add(door);

        // 문 너비 표시
        const doorText = new fabric.Text(`문 너비: ${(doorWidth / scale).toFixed(0)}mm`, {
            left: currentX + (doorWidth * scaleFactor) / 2,
            top: startY - 20 * scaleFactor,
            fontSize: 16 * scaleFactor,
            fill: '#000',
            selectable: false,
            originX: 'center',
            originY: 'center'
        });
        canvas.add(doorText);

        currentX += doorWidth * scaleFactor + gapBetweenDoors * scale * scaleFactor;
    });

    // 치수선 그리기 함수
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

        canvas.add(arrow1, arrow2, arrow3, arrow4);

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

    // 치수선 추가: 너비
    drawDimensionLine(
        startXFrontView,
        startY + bodyHeight * scaleFactor + 20 * scaleFactor,
        startXFrontView + bodyWidth * scaleFactor,
        startY + bodyHeight * scaleFactor + 20 * scaleFactor,
        `너비: ${width}mm`,
        0, 20 * scaleFactor
    );

    // 치수선 추가: 높이
    drawDimensionLine(
        startXFrontView - 20 * scaleFactor,
        startY,
        startXFrontView - 20 * scaleFactor,
        startY + bodyHeight * scaleFactor + legHeight * scale * scaleFactor,
        `높이: ${height}mm`,
        -60 * scaleFactor, 0
    );

    // 윗면 시점 그리기
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

    // 윗면 치수선 추가: 너비
    drawDimensionLine(
        startXTopView,
        startY + topViewHeight * scaleFactor + 20 * scaleFactor,
        startXTopView + bodyWidth * scaleFactor,
        startY + topViewHeight * scaleFactor + 20 * scaleFactor,
        `너비: ${width}mm`,
        0, 20 * scaleFactor
    );

    // 윗면 치수선 추가: 깊이
    drawDimensionLine(
        startXTopView - 20 * scaleFactor,
        startY,
        startXTopView - 20 * scaleFactor,
        startY + topViewHeight * scaleFactor,
        `깊이: ${depth}mm`,
        -60 * scaleFactor, 0
    );
}


controlDrawBlueprint(selectedData);
