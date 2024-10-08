document.addEventListener('DOMContentLoaded', function() {
    const calendarEl = document.getElementById('auto-calendar');

    // 임시 일정 데이터
    const events = [
        { title: '회의', start: '2024-10-02', description: '팀 회의' },
        { title: '프로젝트 마감', start: '2024-10-03', description: '프로젝트 1차 마감' },
        { title: '점심 미팅', start: '2024-10-03', description: '고객사 미팅' },
        { title: '세미나 참석', start: '2024-10-05', description: 'IT 세미나' },
        { title: '개인 연구', start: '2024-10-05', description: '논문 연구' },
        { title: '개인 연구', start: '2024-10-05', description: '논문 연구' },
        { title: '개인 연구', start: '2024-10-05', description: '논문 연구' }
    ];

    const calendar = new FullCalendar.Calendar(calendarEl, {
        initialView: 'dayGridMonth',
        locale: 'ko',
        events: events,
        editable: false,

        // 모바일에서 스크롤 없이 모든 날짜가 보이도록 높이를 자동 조정
        height: 'auto',

        // 날짜 클릭 시 해당 날짜의 모든 일정 모달로 표시
        dateClick: function(info) {
            showEventsForDate(info.dateStr);
        },

        // 일정 클릭 시 모달 표시
        eventClick: function(info) {
            showEventsForDate(info.event.startStr);
        }
    });

    calendar.render();

    function showEventsForDate(dateStr) {
        const modal = document.getElementById('auto-modal');
        const modalTitle = document.getElementById('auto-modal-title');
        const modalBody = document.getElementById('auto-modal-body');

        // 선택된 날짜의 일정 필터링
        const eventsForDay = events.filter(event => event.start === dateStr);

        if (eventsForDay.length > 0) {
            modalTitle.innerText = `${dateStr}의 일정`;
            modalBody.innerHTML = eventsForDay.map((event, index) => `
                <div class="event-item">
                    <p><strong>${index + 1}. ${event.title}</strong></p>
                    <p>시작: ${new Date(event.start).toLocaleString()}</p>
                    <p>${event.description || '추가 정보 없음'}</p>
                </div>
            `).join('');
        } else {
            modalTitle.innerText = '일정 없음';
            modalBody.innerHTML = '<p>선택한 날짜에 일정이 없습니다.</p>';
        }

        modal.style.display = 'block';
    }

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
