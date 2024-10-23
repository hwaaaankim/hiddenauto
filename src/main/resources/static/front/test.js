var socket = new WebSocket("ws://localhost:8080/ws/users");

// WebSocket이 열렸을 때
socket.onopen = function() {
    console.log("WebSocket 연결됨");

    // 현재 페이지 경로를 가져옴
    var page = window.location.pathname;

    // 사용자의 로그인 상태를 확인하고, 로그인되지 않은 사용자라면 "NOT_LOGGED_IN" 상태로 전송
    if (isUserLoggedIn()) {
        // 로그인된 사용자의 역할(ROLE_MEMBER 또는 ROLE_ADMIN)을 가져와서 서버에 전송
        var role = getUserRole();  // 예: "ROLE_MEMBER", "ROLE_ADMIN"
        console.log("로그인된 사용자:", role, "페이지:", page);
        socket.send(JSON.stringify({ action: "update", page: page, role: role }));
    } else {
        // 로그인되지 않은 사용자
        console.log("로그인되지 않은 사용자 페이지:", page);
        socket.send(JSON.stringify({ action: "update", page: page, role: "NOT_LOGGED_IN" }));
    }
};

// 로그아웃 메시지를 수신했을 때 처리
socket.onmessage = function(message) {
    try {
        var parsedMessage = JSON.parse(message.data);

        if (parsedMessage.action === "LOGOUT") {
            alert("로그아웃 되었습니다.");
            window.location.href = "/index";  // 로그아웃 후 리다이렉트할 페이지
        }
    } catch (e) {
        console.error("메시지 처리 중 오류 발생: ", e);
    }
};

// WebSocket 연결이 종료되었을 때
socket.onclose = function() {
    console.log("WebSocket 연결 종료");
};

// 사용자가 로그인되었는지 확인하는 함수 (서버에서 로그인 상태를 가져오는 방법에 따라 구현 필요)
function isUserLoggedIn() {
    // 예시: 로그인 여부를 확인하는 코드
    return document.body.classList.contains('logged-in');  // 또는 다른 방식으로 로그인 상태 확인
}

// 사용자의 역할을 가져오는 함수 (서버에서 사용자의 역할 정보를 제공해야 함)
function getUserRole() {
    // 예시: 사용자의 역할을 반환하는 코드
    return document.body.dataset.role;  // 페이지에서 역할을 설정하는 방식에 따라 다름
}
