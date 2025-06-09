package com.dev.HiddenBATHAuto.repository.order;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Cart;

@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {
	
	// 기본 장바구니 (directOrder = false)
    List<Cart> findByMemberAndDirectOrder(Member member, boolean directOrder);

    // 생성일 기준 전체 조회 (직접발주 포함)
    List<Cart> findAllByMemberOrderByCreatedAtDesc(Member member);

    // 단일 항목 조회 (삭제 등)
    Optional<Cart> findByIdAndMember(Long id, Member member);
}
