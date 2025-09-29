package com.dev.HiddenBATHAuto.repository.manager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dev.HiddenBATHAuto.model.manager.Popup;

public interface PopupManagerRepository extends JpaRepository<Popup, Long> {

	List<Popup> findAllByOrderByCreatedAtDesc();
	
    Page<Popup> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Popup> findByStartAtLessThanEqualAndEndAtGreaterThanEqual(LocalDateTime now1, LocalDateTime now2);
    
    /* ✅ 순서 기준 조회 */
    List<Popup> findAllByOrderByDispOrderAscCreatedAtDesc();
    Page<Popup> findAllByOrderByDispOrderAscCreatedAtDesc(Pageable pageable);

    /* ✅ 활성 팝업도 순서 기준 */
    List<Popup> findByStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByDispOrderAscCreatedAtDesc(
            LocalDateTime now1, LocalDateTime now2
    );

    /* ✅ 최대 dispOrder 찾기 (신규 등록 시 +1) */
    Optional<Popup> findTopByOrderByDispOrderDesc();

    /* ✅ 개별 업데이트(배치용) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Popup p set p.dispOrder = :dispOrder where p.id = :id")
    int updateDispOrder(@Param("id") Long id, @Param("dispOrder") int dispOrder);
}