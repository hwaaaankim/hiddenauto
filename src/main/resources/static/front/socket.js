let stompClient = null;
let guestSubscription = null;

function connectWebSocket(isGuest) {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    stompClient.connect({}, function (frame) {
        console.log('Connected: ' + frame);

        stompClient.subscribe('/topic/all', function (message) {
            console.log("All Users: " + message.body);
        });

        if (isGuest) {
            guestSubscription = stompClient.subscribe('/topic/guests', function (message) {
                console.log("Guests Only: " + message.body);
            });
        } else {
            stompClient.subscribe('/user/topic/member', function (message) {
                console.log("Member Only: " + message.body);
            });
        }
    }, function (error) {
        console.error('STOMP error: ' + error);
    });
}

function sendToAllUsers(message) {
    if (stompClient && stompClient.connected) {
        stompClient.send("/app/sendToAll", {}, message);
    }
}

function sendToGuests(message) {
    fetch('/sendToGuests', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ message })
    });
}

function sendToMember(message) {
    fetch('/sendToMember', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ message })
    });
}

function disconnectWebSocket() {
    if (stompClient !== null) {
        stompClient.disconnect(function() {
            console.log("Disconnected");
        });
    }
}

window.addEventListener('load', function() {
    connectWebSocket(isGuest);
});

window.addEventListener('beforeunload', function() {
    disconnectWebSocket();
});
