package com.dev.HiddenBATHAuto.service.order;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.DeliveryOrderIndexUpdateRequest;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class DeliveryOrderIndexService {

	private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;
    private final MemberRepository memberRepository;
	/**
     * 규칙:
     * - 레코드 존재 + (담당자/날짜 동일) => 아무 것도 안 함.
     * - 레코드 미존재 + (담당자/날짜 지정) => 새로 생성 (해당 큐 max+1).
     * - 레코드 존재 + (담당자/날짜 변경) => 해당 큐 끝(max+1)으로 이동(갱신).
     * - 담당자 또는 날짜가 null => 기존 레코드 있으면 삭제.
     */
    public void ensureIndex(Order order) {
        DeliveryOrderIndex existing = deliveryOrderIndexRepository.findByOrder(order).orElse(null);

        // 현재 오더의 담당자/희망배송일(날짜) 추출
        Member handler = order.getAssignedDeliveryHandler();
        LocalDate date = order.getPreferredDeliveryDate() == null ? null : order.getPreferredDeliveryDate().toLocalDate();

        // (D) 담당자 또는 날짜가 비어 있으면 인덱스 제거
        if (handler == null || date == null) {
            if (existing != null) {
                deliveryOrderIndexRepository.delete(existing);
            }
            return;
        }

        // (A) 이미 존재하고, (담당자/날짜) 모두 동일하면: 유지(아무 것도 안 함)
        if (existing != null
                && existing.getDeliveryHandler() != null
                && handler.getId().equals(existing.getDeliveryHandler().getId())
                && date.equals(existing.getDeliveryDate())) {
            return; // ★ 관리자 업데이트 때 뒤로 밀리는 현상 방지 핵심 라인
        }

        // (B) or (C): 새 큐의 끝으로 배치(신규 생성 또는 이동)
        Integer maxIndex = deliveryOrderIndexRepository.findMaxIndexByHandlerAndDate(handler.getId(), date);
        int next = (maxIndex == null ? 1 : (maxIndex + 1));

        if (existing == null) {
            // (B) 신규 생성
            DeliveryOrderIndex created = new DeliveryOrderIndex();
            created.setOrder(order);
            created.setDeliveryHandler(handler);
            created.setDeliveryDate(date);
            created.setOrderIndex(next);
            deliveryOrderIndexRepository.save(created);
        } else {
            // (C) 이동(담당자/날짜 변경)
            existing.setDeliveryHandler(handler);
            existing.setDeliveryDate(date);
            existing.setOrderIndex(next);
            // version 칼럼 덕에 동시성 충돌 시 OptimisticLockException 발생 → 상위에서 재시도 정책 고려 가능
            deliveryOrderIndexRepository.save(existing);
        }
    }

    // (추가) 일괄 재정렬 API는 기존 코드 유지
    public void updateIndexes(DeliveryOrderIndexUpdateRequest request) {
        Member handler = memberRepository.findById(request.getDeliveryHandlerId())
                .orElseThrow(() -> new IllegalArgumentException("배송 담당자 없음"));

        LocalDate date = LocalDate.parse(request.getDeliveryDate());

        for (DeliveryOrderIndexUpdateRequest.OrderIndexDto dto : request.getOrderList()) {
            DeliveryOrderIndex index = deliveryOrderIndexRepository
                    .findByDeliveryHandlerIdAndDeliveryDateAndOrderId(handler.getId(), date, dto.getOrderId())
                    .orElseThrow(() -> new IllegalStateException("해당 주문 인덱스 없음"));
            index.setOrderIndex(dto.getOrderIndex());
            // save 호출은 트랜잭션 커밋 시 플러시되므로 생략 가능(JPA dirty checking)
        }
    }
}
