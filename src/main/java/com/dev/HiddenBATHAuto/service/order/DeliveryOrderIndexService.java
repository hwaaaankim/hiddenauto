package com.dev.HiddenBATHAuto.service.order;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.DeliveryOrderIndexUpdateRequest;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
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
    
    /**
     * 요구사항 반영:
     * - 화면에서는 미완료만 드래그 가능, 배송완료는 하단 고정
     * - 저장 시에는 "미완료(드래그된 순서) + 배송완료(기존 순서)"를 합친 DOM 순서를 그대로 1..N 인덱스로 저장
     * - 혹시 API를 직접 호출해 배송완료의 상대순서를 바꾸려는 경우 서버에서 차단
     */
    @Transactional
    public void updateIndexesWithDoneGuard(DeliveryOrderIndexUpdateRequest request) {
        if (request == null) throw new IllegalArgumentException("요청이 비어있습니다.");
        if (request.getDeliveryHandlerId() == null) throw new IllegalArgumentException("담당자 ID가 없습니다.");
        if (request.getDeliveryDate() == null || request.getDeliveryDate().isBlank())
            throw new IllegalArgumentException("날짜가 없습니다.");
        if (request.getOrderList() == null) throw new IllegalArgumentException("orderList가 없습니다.");

        Member handler = memberRepository.findById(request.getDeliveryHandlerId())
                .orElseThrow(() -> new IllegalArgumentException("배송 담당자 없음"));

        LocalDate date;
        try {
            date = LocalDate.parse(request.getDeliveryDate());
        } catch (Exception e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. yyyy-MM-dd");
        }

        // 1) 현재 DB 기준(정상 규칙) 순서 조회
        List<DeliveryOrderIndex> current = deliveryOrderIndexRepository
                .findAllByHandlerAndDateForGuard(handler.getId(), date);

        // 2) 현재 done 순서(상대순서) 추출
        List<Long> currentDoneOrderIds = current.stream()
                .filter(x -> x.getOrder() != null && x.getOrder().getStatus() == OrderStatus.DELIVERY_DONE)
                .map(x -> x.getOrder().getId())
                .collect(Collectors.toList());

        // 3) 요청된 done 순서 추출(요청 orderList 중에서 현재 done에 해당하는 것만 뽑아서 순서 유지)
        Set<Long> doneSet = new HashSet<>(currentDoneOrderIds);

        List<Long> requestedDoneOrderIds = request.getOrderList().stream()
                .map(DeliveryOrderIndexUpdateRequest.OrderIndexDto::getOrderId)
                .filter(Objects::nonNull)
                .filter(doneSet::contains)
                .collect(Collectors.toList());

        // 4) 서버 검증: 배송완료 상대순서가 바뀌면 차단
        // - UI에서는 doneList가 고정이라 정상이라면 항상 동일해야 함
        if (!currentDoneOrderIds.equals(requestedDoneOrderIds)) {
            throw new IllegalStateException("배송완료 항목의 순서는 변경할 수 없습니다.");
        }

        // 5) 요청 orderId 중복/누락 방지(기본 방어)
        List<Long> reqIds = request.getOrderList().stream()
                .map(DeliveryOrderIndexUpdateRequest.OrderIndexDto::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        long distinctCount = reqIds.stream().distinct().count();
        if (distinctCount != reqIds.size()) {
            throw new IllegalArgumentException("중복된 orderId가 존재합니다.");
        }

        // 6) 현재 존재하는 인덱스 맵
        Map<Long, DeliveryOrderIndex> indexByOrderId = current.stream()
                .filter(x -> x.getOrder() != null)
                .collect(Collectors.toMap(
                        x -> x.getOrder().getId(),
                        x -> x,
                        (a, b) -> a
                ));

        // 7) 요청에 들어온 orderId는 현재 목록에 모두 있어야 함(누락/조작 방어)
        for (Long oid : reqIds) {
            if (!indexByOrderId.containsKey(oid)) {
                throw new IllegalArgumentException("해당 주문 인덱스 없음: orderId=" + oid);
            }
        }

        // 8) 핵심 저장: 요청 순서대로 1..N 재부여(유니크 제약/충돌 방지)
        //    orderIndexDto.orderIndex 값은 신뢰하지 않고, request의 리스트 순서 자체를 기준으로 재계산합니다.
        int newIndex = 1;
        for (DeliveryOrderIndexUpdateRequest.OrderIndexDto dto : request.getOrderList()) {
            if (dto == null || dto.getOrderId() == null) continue;

            DeliveryOrderIndex idx = indexByOrderId.get(dto.getOrderId());
            idx.setOrderIndex(newIndex++);
            // dirty checking
        }
    }
}
