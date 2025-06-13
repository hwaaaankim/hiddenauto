package com.dev.HiddenBATHAuto.repository.nonstandard;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.ProductMark;

@Repository
public interface ProductMarkRepository extends JpaRepository<ProductMark, Long> {
    List<ProductMark> findByMember(Member member);
}

