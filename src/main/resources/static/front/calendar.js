document.addEventListener('DOMContentLoaded', function() {
    const calendarEl = document.getElementById('auto-calendar');

    // 임시 일정 데이터 (DB 대신 하드코딩된 데이터 사용)
    const events = [
        {
            title: '회의',
            start: '2024-10-02',
            description: '팀 회의'
        },
        {
            title: '프로젝트 마감',
            start: '2024-10-03',
            description: '프로젝트 1차 마감'
        },
        {
            title: '점심 미팅',
            start: '2024-10-03',
            description: '고객사 미팅'
        },
        {
            title: '세미나 참석',
            start: '2024-10-05',
            description: 'IT 세미나'
        },
        {
            title: '개인 연구',
            start: '2024-10-05',
            description: '논문 연구'
        }
    ];

    const calendar = new FullCalendar.Calendar(calendarEl, {
        initialView: 'dayGridMonth',
        locale: 'ko',
        events: events, // 하드코딩된 일정 데이터 사용
        eventClick: function(info) {
            const modal = document.getElementById('auto-modal');
            const modalTitle = document.getElementById('auto-modal-title');
            const modalBody = document.getElementById('auto-modal-body');

            // 모달에 일정 내용 표시
            modalTitle.innerText = info.event.title;
            modalBody.innerHTML = `
                <p>시작: ${info.event.start.toLocaleString()}</p>
                <p>${info.event.extendedProps.description || '추가 정보 없음'}</p>
            `;

            modal.style.display = 'block';
        },
        editable: false, // 일정 이동 금지
    });

    calendar.render();

    // 모달 닫기 기능
    const modalCloseBtn = document.getElementById('auto-close');
    modalCloseBtn.onclick = function() {
        document.getElementById('auto-modal').style.display = 'none';
    };

    window.onclick = function(event) {
        const modal = document.getElementById('auto-modal');
        if (event.target === modal) {
            modal.style.display = 'none';
        }
    };
});
