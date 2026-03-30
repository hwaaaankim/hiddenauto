package com.dev.HiddenBATHAuto.repository.as;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;

@Repository
public interface AsTaskRepository extends JpaRepository<AsTask, Long>, AsTaskRepositoryCustom {

	Optional<AsTask> findByIdAndRequestedBy_Id(Long id, Long requestedById);
	// ========= 신청일 기준 (화면 페이지) =========
	@EntityGraph(attributePaths = { "requestedBy", "requestedBy.company", "assignedHandler", "assignedTeam" })
	@Query(
	    value = """
	        SELECT a
	        FROM AsTask a
	        LEFT JOIN a.requestedBy rb
	        LEFT JOIN rb.company c
	        LEFT JOIN a.assignedHandler ah
	        WHERE (:status IS NULL OR a.status = :status)
	          AND (:handlerId IS NULL OR ah.id = :handlerId)
	          AND (:startDate IS NULL OR a.requestedAt >= :startDate)
	          AND (:endDate IS NULL OR a.requestedAt < :endDate)
	          AND (
	                :priceFilter IS NULL
	                OR (:priceFilter = 'ZERO' AND a.price = 0)
	                OR (:priceFilter = 'POSITIVE' AND a.price > 0)
	              )
	          AND (:paymentCollected IS NULL OR a.paymentCollected = :paymentCollected)
	          AND (
	                :keyword IS NULL
	                OR (
	                    :keywordType = 'all' AND (
	                        LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                    )
	                )
	                OR (:keywordType = 'companyName'
	                    AND LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'requesterName'
	                    AND LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'customerName'
	                    AND LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'subject'
	                    AND LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'productName'
	                    AND LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantName'
	                    AND LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantPhone'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	                OR (:keywordType = 'onsiteContact'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	              )
	        """,
	    countQuery = """
	        SELECT COUNT(a)
	        FROM AsTask a
	        LEFT JOIN a.requestedBy rb
	        LEFT JOIN rb.company c
	        LEFT JOIN a.assignedHandler ah
	        WHERE (:status IS NULL OR a.status = :status)
	          AND (:handlerId IS NULL OR ah.id = :handlerId)
	          AND (:startDate IS NULL OR a.requestedAt >= :startDate)
	          AND (:endDate IS NULL OR a.requestedAt < :endDate)
	          AND (
	                :priceFilter IS NULL
	                OR (:priceFilter = 'ZERO' AND a.price = 0)
	                OR (:priceFilter = 'POSITIVE' AND a.price > 0)
	              )
	          AND (:paymentCollected IS NULL OR a.paymentCollected = :paymentCollected)
	          AND (
	                :keyword IS NULL
	                OR (
	                    :keywordType = 'all' AND (
	                        LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                    )
	                )
	                OR (:keywordType = 'companyName'
	                    AND LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'requesterName'
	                    AND LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'customerName'
	                    AND LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'subject'
	                    AND LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'productName'
	                    AND LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantName'
	                    AND LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantPhone'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	                OR (:keywordType = 'onsiteContact'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	              )
	        """
	)
	Page<AsTask> findByRequestedDateRangePage(
	        @Param("handlerId") Long handlerId,
	        @Param("status") AsStatus status,
	        @Param("startDate") LocalDateTime startDate,
	        @Param("endDate") LocalDateTime endDate,
	        @Param("priceFilter") String priceFilter,
	        @Param("paymentCollected") Boolean paymentCollected,
	        @Param("keywordType") String keywordType,
	        @Param("keyword") String keyword,
	        Pageable pageable
	);

	// ========= 처리일 기준 (화면 페이지) =========
	@EntityGraph(attributePaths = { "requestedBy", "requestedBy.company", "assignedHandler", "assignedTeam" })
	@Query(
	    value = """
	        SELECT a
	        FROM AsTask a
	        LEFT JOIN a.requestedBy rb
	        LEFT JOIN rb.company c
	        LEFT JOIN a.assignedHandler ah
	        WHERE (:status IS NULL OR a.status = :status)
	          AND (:handlerId IS NULL OR ah.id = :handlerId)
	          AND (:startDate IS NULL OR a.asProcessDate >= :startDate)
	          AND (:endDate IS NULL OR a.asProcessDate < :endDate)
	          AND (
	                :priceFilter IS NULL
	                OR (:priceFilter = 'ZERO' AND a.price = 0)
	                OR (:priceFilter = 'POSITIVE' AND a.price > 0)
	              )
	          AND (:paymentCollected IS NULL OR a.paymentCollected = :paymentCollected)
	          AND (
	                :keyword IS NULL
	                OR (
	                    :keywordType = 'all' AND (
	                        LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                    )
	                )
	                OR (:keywordType = 'companyName'
	                    AND LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'requesterName'
	                    AND LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'customerName'
	                    AND LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'subject'
	                    AND LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'productName'
	                    AND LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantName'
	                    AND LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantPhone'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	                OR (:keywordType = 'onsiteContact'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	              )
	        """,
	    countQuery = """
	        SELECT COUNT(a)
	        FROM AsTask a
	        LEFT JOIN a.requestedBy rb
	        LEFT JOIN rb.company c
	        LEFT JOIN a.assignedHandler ah
	        WHERE (:status IS NULL OR a.status = :status)
	          AND (:handlerId IS NULL OR ah.id = :handlerId)
	          AND (:startDate IS NULL OR a.asProcessDate >= :startDate)
	          AND (:endDate IS NULL OR a.asProcessDate < :endDate)
	          AND (
	                :priceFilter IS NULL
	                OR (:priceFilter = 'ZERO' AND a.price = 0)
	                OR (:priceFilter = 'POSITIVE' AND a.price > 0)
	              )
	          AND (:paymentCollected IS NULL OR a.paymentCollected = :paymentCollected)
	          AND (
	                :keyword IS NULL
	                OR (
	                    :keywordType = 'all' AND (
	                        LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                    )
	                )
	                OR (:keywordType = 'companyName'
	                    AND LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'requesterName'
	                    AND LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'customerName'
	                    AND LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'subject'
	                    AND LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'productName'
	                    AND LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantName'
	                    AND LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantPhone'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	                OR (:keywordType = 'onsiteContact'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	              )
	        """
	)
	Page<AsTask> findByProcessedDateRangePage(
	        @Param("handlerId") Long handlerId,
	        @Param("status") AsStatus status,
	        @Param("startDate") LocalDateTime startDate,
	        @Param("endDate") LocalDateTime endDate,
	        @Param("priceFilter") String priceFilter,
	        @Param("paymentCollected") Boolean paymentCollected,
	        @Param("keywordType") String keywordType,
	        @Param("keyword") String keyword,
	        Pageable pageable
	);

	// ========= 신청일 기준 (엑셀 전체조회) =========
	@EntityGraph(attributePaths = { "requestedBy", "requestedBy.company", "assignedHandler", "assignedTeam" })
	@Query("""
	        SELECT a
	        FROM AsTask a
	        LEFT JOIN a.requestedBy rb
	        LEFT JOIN rb.company c
	        LEFT JOIN a.assignedHandler ah
	        WHERE (:status IS NULL OR a.status = :status)
	          AND (:handlerId IS NULL OR ah.id = :handlerId)
	          AND (:startDate IS NULL OR a.requestedAt >= :startDate)
	          AND (:endDate IS NULL OR a.requestedAt < :endDate)
	          AND (
	                :priceFilter IS NULL
	                OR (:priceFilter = 'ZERO' AND a.price = 0)
	                OR (:priceFilter = 'POSITIVE' AND a.price > 0)
	              )
	          AND (:paymentCollected IS NULL OR a.paymentCollected = :paymentCollected)
	          AND (
	                :keyword IS NULL
	                OR (
	                    :keywordType = 'all' AND (
	                        LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                    )
	                )
	                OR (:keywordType = 'companyName'
	                    AND LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'requesterName'
	                    AND LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'customerName'
	                    AND LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'subject'
	                    AND LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'productName'
	                    AND LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantName'
	                    AND LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantPhone'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	                OR (:keywordType = 'onsiteContact'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	              )
	        """)
	List<AsTask> findByRequestedDateRangeAll(
	        @Param("handlerId") Long handlerId,
	        @Param("status") AsStatus status,
	        @Param("startDate") LocalDateTime startDate,
	        @Param("endDate") LocalDateTime endDate,
	        @Param("priceFilter") String priceFilter,
	        @Param("paymentCollected") Boolean paymentCollected,
	        @Param("keywordType") String keywordType,
	        @Param("keyword") String keyword,
	        Sort sort
	);

	// ========= 처리일 기준 (엑셀 전체조회) =========
	@EntityGraph(attributePaths = { "requestedBy", "requestedBy.company", "assignedHandler", "assignedTeam" })
	@Query("""
	        SELECT a
	        FROM AsTask a
	        LEFT JOIN a.requestedBy rb
	        LEFT JOIN rb.company c
	        LEFT JOIN a.assignedHandler ah
	        WHERE (:status IS NULL OR a.status = :status)
	          AND (:handlerId IS NULL OR ah.id = :handlerId)
	          AND (:startDate IS NULL OR a.asProcessDate >= :startDate)
	          AND (:endDate IS NULL OR a.asProcessDate < :endDate)
	          AND (
	                :priceFilter IS NULL
	                OR (:priceFilter = 'ZERO' AND a.price = 0)
	                OR (:priceFilter = 'POSITIVE' AND a.price > 0)
	              )
	          AND (:paymentCollected IS NULL OR a.paymentCollected = :paymentCollected)
	          AND (
	                :keyword IS NULL
	                OR (
	                    :keywordType = 'all' AND (
	                        LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                    )
	                )
	                OR (:keywordType = 'companyName'
	                    AND LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'requesterName'
	                    AND LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'customerName'
	                    AND LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'subject'
	                    AND LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'productName'
	                    AND LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantName'
	                    AND LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantPhone'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	                OR (:keywordType = 'onsiteContact'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	              )
	        """)
	List<AsTask> findByProcessedDateRangeAll(
	        @Param("handlerId") Long handlerId,
	        @Param("status") AsStatus status,
	        @Param("startDate") LocalDateTime startDate,
	        @Param("endDate") LocalDateTime endDate,
	        @Param("priceFilter") String priceFilter,
	        @Param("paymentCollected") Boolean paymentCollected,
	        @Param("keywordType") String keywordType,
	        @Param("keyword") String keyword,
	        Sort sort
	);
	
	Optional<AsTask> findByIdAndRequestedBy_Company_Id(Long id, Long companyId);
	
	@Query(value = """
            select a
            from AsTask a
            left join a.requestedBy rb
            left join rb.company company
            where a.assignedHandler.id = :handlerId
              and (:status is null or a.status = :status)
              and (:start is null or a.requestedAt >= :start)
              and (:end is null or a.requestedAt < :end)
              and (:companyKeyword is null or :companyKeyword = '' or lower(company.companyName) like lower(concat('%', :companyKeyword, '%')))
              and (:provinceNames is null or a.doName in :provinceNames)
              and (:cityName is null or a.siName = :cityName)
              and (:districtName is null or a.guName = :districtName)
            order by
              case when :visitTimeSort = 'asc' and a.visitPlannedTime is null then 1 else 0 end asc,
              case when :visitTimeSort = 'asc' then a.visitPlannedTime else null end asc,
              case when :visitTimeSort = 'desc' and a.visitPlannedTime is null then 1 else 0 end asc,
              case when :visitTimeSort = 'desc' then a.visitPlannedTime else null end desc,
              a.id desc
            """,
            countQuery = """
            select count(a)
            from AsTask a
            left join a.requestedBy rb
            left join rb.company company
            where a.assignedHandler.id = :handlerId
              and (:status is null or a.status = :status)
              and (:start is null or a.requestedAt >= :start)
              and (:end is null or a.requestedAt < :end)
              and (:companyKeyword is null or :companyKeyword = '' or lower(company.companyName) like lower(concat('%', :companyKeyword, '%')))
              and (:provinceNames is null or a.doName in :provinceNames)
              and (:cityName is null or a.siName = :cityName)
              and (:districtName is null or a.guName = :districtName)
            """)
    Page<AsTask> findByRequestedDateFlexible(@Param("handlerId") Long handlerId,
                                             @Param("status") AsStatus status,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             @Param("companyKeyword") String companyKeyword,
                                             @Param("provinceNames") List<String> provinceNames,
                                             @Param("cityName") String cityName,
                                             @Param("districtName") String districtName,
                                             @Param("visitTimeSort") String visitTimeSort,
                                             Pageable pageable);

    @Query(value = """
            select a
            from AsTask a
            left join a.requestedBy rb
            left join rb.company company
            where a.assignedHandler.id = :handlerId
              and (:status is null or a.status = :status)
              and (:start is null or a.asProcessDate >= :start)
              and (:end is null or a.asProcessDate < :end)
              and (:companyKeyword is null or :companyKeyword = '' or lower(company.companyName) like lower(concat('%', :companyKeyword, '%')))
              and (:provinceNames is null or a.doName in :provinceNames)
              and (:cityName is null or a.siName = :cityName)
              and (:districtName is null or a.guName = :districtName)
            order by
              case when :visitTimeSort = 'asc' and a.visitPlannedTime is null then 1 else 0 end asc,
              case when :visitTimeSort = 'asc' then a.visitPlannedTime else null end asc,
              case when :visitTimeSort = 'desc' and a.visitPlannedTime is null then 1 else 0 end asc,
              case when :visitTimeSort = 'desc' then a.visitPlannedTime else null end desc,
              a.id desc
            """,
            countQuery = """
            select count(a)
            from AsTask a
            left join a.requestedBy rb
            left join rb.company company
            where a.assignedHandler.id = :handlerId
              and (:status is null or a.status = :status)
              and (:start is null or a.asProcessDate >= :start)
              and (:end is null or a.asProcessDate < :end)
              and (:companyKeyword is null or :companyKeyword = '' or lower(company.companyName) like lower(concat('%', :companyKeyword, '%')))
              and (:provinceNames is null or a.doName in :provinceNames)
              and (:cityName is null or a.siName = :cityName)
              and (:districtName is null or a.guName = :districtName)
            """)
    Page<AsTask> findByProcessedDateFlexible(@Param("handlerId") Long handlerId,
                                             @Param("status") AsStatus status,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             @Param("companyKeyword") String companyKeyword,
                                             @Param("provinceNames") List<String> provinceNames,
                                             @Param("cityName") String cityName,
                                             @Param("districtName") String districtName,
                                             @Param("visitTimeSort") String visitTimeSort,
                                             Pageable pageable);
	
	// ✅ (A) 신청일 기준 조회: requestedAt DESC 정렬 보장
	@Query("""
	      select t from AsTask t
	      left join t.requestedBy rb
	      left join rb.company c
	      where (:status is null or t.status = :status)

	        and (
	              :companyKeyword is null or :companyKeyword = '' or
	              (c is not null and c.companyName like concat('%', :companyKeyword, '%'))
	        )

	        and (:provinceNames is null or t.doName in :provinceNames)
	        and (:cityNames is null or t.siName in :cityNames)
	        and (:districtNames is null or t.guName in :districtNames)

	        and (:start is null or t.requestedAt >= :start)
	        and (:end is null or t.requestedAt < :end)

	      order by t.requestedAt desc
	      """)
	Page<AsTask> searchRequestedForCalendar(
	        @Param("status") AsStatus status,
	        @Param("companyKeyword") String companyKeyword,
	        @Param("provinceNames") List<String> provinceNames,
	        @Param("cityNames") List<String> cityNames,
	        @Param("districtNames") List<String> districtNames,
	        @Param("start") LocalDateTime start,
	        @Param("end") LocalDateTime end,
	        Pageable pageable
	);

	// ✅ (B) 처리일 기준 조회: asProcessDate DESC 정렬 보장 + asProcessDate null 제외
	@Query("""
	      select t from AsTask t
	      left join t.requestedBy rb
	      left join rb.company c
	      where t.asProcessDate is not null

	        and (:status is null or t.status = :status)

	        and (
	              :companyKeyword is null or :companyKeyword = '' or
	              (c is not null and c.companyName like concat('%', :companyKeyword, '%'))
	        )

	        and (:provinceNames is null or t.doName in :provinceNames)
	        and (:cityNames is null or t.siName in :cityNames)
	        and (:districtNames is null or t.guName in :districtNames)

	        and (:start is null or t.asProcessDate >= :start)
	        and (:end is null or t.asProcessDate < :end)

	      order by t.asProcessDate desc
	      """)
	Page<AsTask> searchProcessedForCalendar(
	        @Param("status") AsStatus status,
	        @Param("companyKeyword") String companyKeyword,
	        @Param("provinceNames") List<String> provinceNames,
	        @Param("cityNames") List<String> cityNames,
	        @Param("districtNames") List<String> districtNames,
	        @Param("start") LocalDateTime start,
	        @Param("end") LocalDateTime end,
	        Pageable pageable
	);

	// ✅ (C) 업무등록일(스케줄) 기준 조회: scheduledDate DESC, orderIndex ASC 정렬 보장
	@Query("""
	      select t
	      from AsTaskSchedule s
	      join s.asTask t
	      left join t.requestedBy rb
	      left join rb.company c
	      where (:status is null or t.status = :status)

	        and (:startDate is null or s.scheduledDate >= :startDate)
	        and (:endDate is null or s.scheduledDate < :endDate)

	        and (
	              :companyKeyword is null or :companyKeyword = '' or
	              (c is not null and c.companyName like concat('%', :companyKeyword, '%'))
	        )

	        and (:provinceNames is null or t.doName in :provinceNames)
	        and (:cityNames is null or t.siName in :cityNames)
	        and (:districtNames is null or t.guName in :districtNames)

	      order by s.scheduledDate desc, s.orderIndex asc
	      """)
	Page<AsTask> searchScheduledForCalendar(
	        @Param("status") AsStatus status,
	        @Param("companyKeyword") String companyKeyword,
	        @Param("provinceNames") List<String> provinceNames,
	        @Param("cityNames") List<String> cityNames,
	        @Param("districtNames") List<String> districtNames,
	        @Param("startDate") LocalDate startDate,
	        @Param("endDate") LocalDate endDate,
	        Pageable pageable
	);
	
	@Query("""
        select a
        from AsTask a
        where a.requestedBy.company.id = :companyId
          and (:start is null or a.requestedAt >= :start)
          and (:end is null or a.requestedAt <= :end)
        """)
    Page<AsTask> findByCompanyIdAndRequestedAtRange(
            @Param("companyId") Long companyId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable
    );
	
	@Query("select a from AsTask a where a.requestedBy = :member and a.asProcessDate is not null")
	List<AsTask> findByRequestedByAndAsProcessDateNotNull(@Param("member") Member member);

	Page<AsTask> findAllByOrderByRequestedAtDesc(Pageable pageable);

	List<AsTask> findByRequestedBy(Member member);

	List<AsTask> findByRequestedByAndAsProcessDateBetween(Member member, LocalDateTime start, LocalDateTime end);

	// AsTaskRepository
	List<AsTask> findByRequestedByAndRequestedAtBetween(Member member, LocalDateTime start, LocalDateTime end);

	@Query("SELECT a FROM AsTask a WHERE a.requestedBy.company.id = :companyId")
	Page<AsTask> findByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

	@Query("""
			    SELECT a FROM AsTask a
			    WHERE a.status IN :statuses
			      AND a.assignedHandler.id = :memberId
			      AND (:asDate IS NULL OR DATE(a.asProcessDate) = :asDate)
			    ORDER BY a.asProcessDate ASC
			""")
	Page<AsTask> findByAssignedHandlerAndStatusInAndDate(Long memberId, List<AsStatus> statuses, LocalDate asDate,
			Pageable pageable);

	@Query("""
			    SELECT a FROM AsTask a
			    WHERE a.requestedBy.company.id = :companyId
			      AND (:start IS NULL OR a.requestedAt >= :start)
			      AND (:end IS NULL OR a.requestedAt <= :end)
			""")
	Page<AsTask> findByCompanyIdAndRequestedAtBetween(@Param("companyId") Long companyId,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

	@Query("""
			    SELECT a FROM AsTask a
			    WHERE (:statuses IS NULL OR a.status = :statuses)
			      AND (:memberId IS NULL OR a.assignedHandler.id = :memberId)
			      AND (:asDate IS NULL OR a.requestedAt BETWEEN :asDate AND :asDatePlusOne)
			    ORDER BY a.requestedAt DESC
			""")
	Page<AsTask> findByFilter(@Param("memberId") Long memberId, @Param("statuses") AsStatus statuses,
			@Param("asDate") LocalDate asDate, @Param("asDatePlusOne") LocalDate asDatePlusOne, Pageable pageable);

	@Query("""
			    SELECT a FROM AsTask a
			    WHERE (:statuses IS NULL OR a.status = :statuses)
			      AND (:memberId IS NULL OR a.assignedHandler.id = :memberId)
			      AND (:asDateStart IS NULL OR a.requestedAt >= :asDateStart)
			      AND (:asDateEnd IS NULL OR a.requestedAt < :asDateEnd)
			    ORDER BY a.requestedAt DESC
			""")
	Page<AsTask> findByFilterWithDateRange(@Param("memberId") Long memberId, @Param("statuses") AsStatus statuses,
			@Param("asDateStart") LocalDateTime asDateStart, @Param("asDateEnd") LocalDateTime asDateEnd,
			Pageable pageable);

	@Query("""
			    SELECT a FROM AsTask a
			    WHERE (:statuses IS NULL OR a.status = :statuses)
			      AND (:memberId IS NULL OR a.assignedHandler.id = :memberId)
			      AND (:asDateStart IS NULL OR a.requestedAt >= :asDateStart)
			      AND (:asDateEnd IS NULL OR a.requestedAt < :asDateEnd)
			    ORDER BY a.requestedAt DESC
			""")
	List<AsTask> findByFilterWithDateRangeNonPageable(@Param("memberId") Long memberId,
			@Param("statuses") AsStatus statuses, @Param("asDateStart") LocalDateTime asDateStart,
			@Param("asDateEnd") LocalDateTime asDateEnd);

	@Query("""
			    SELECT a FROM AsTask a
			    WHERE a.assignedHandler.id = :handlerId
			      AND a.requestedAt >= :start
			      AND a.requestedAt < :end
			    ORDER BY a.requestedAt DESC
			""")
	Page<AsTask> findByAssignedHandlerAndDate(@Param("handlerId") Long handlerId, @Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end, Pageable pageable);

	@Query("""
			    SELECT a FROM AsTask a
			    WHERE a.assignedHandler.id = :handlerId
			      AND (:status IS NULL OR a.status = :status)
			      AND (
			           (:dateType = 'requested' AND a.requestedAt BETWEEN :start AND :end)
			        OR (:dateType = 'processed' AND a.asProcessDate BETWEEN :start AND :end)
			      )
			    ORDER BY a.requestedAt DESC
			""")
	Page<AsTask> findAsTasksByFilter(@Param("handlerId") Long handlerId, @Param("status") AsStatus status,
			@Param("dateType") String dateType, // "requested" or "processed"
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

	@Query("""
			    SELECT a FROM AsTask a
			    WHERE a.assignedHandler.id = :handlerId
			      AND (:status IS NULL OR a.status = :status)
			      AND a.requestedAt BETWEEN :start AND :end
			    ORDER BY a.requestedAt DESC
			""")
	Page<AsTask> findByRequestedDate(@Param("handlerId") Long handlerId, @Param("status") AsStatus status,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

	@Query("""
			    SELECT a FROM AsTask a
			    WHERE a.assignedHandler.id = :handlerId
			      AND (:status IS NULL OR a.status = :status)
			      AND (:start IS NULL OR a.requestedAt >= :start)
			      AND (:end IS NULL OR a.requestedAt < :end)

			      AND (
			            :companyKeyword IS NULL OR :companyKeyword = '' OR
			            (a.requestedBy.company IS NOT NULL AND a.requestedBy.company.companyName LIKE CONCAT('%', :companyKeyword, '%'))
			      )

			      AND (
			            :provinceNames IS NULL OR a.doName IN :provinceNames
			      )
			      AND (:cityName IS NULL OR :cityName = '' OR a.siName = :cityName)
			      AND (:districtName IS NULL OR :districtName = '' OR a.guName = :districtName)

			    ORDER BY a.requestedAt DESC
			""")
	Page<AsTask> findByRequestedDateFlexible(@Param("handlerId") Long handlerId, @Param("status") AsStatus status,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
			@Param("companyKeyword") String companyKeyword, @Param("provinceNames") List<String> provinceNames,
			@Param("cityName") String cityName, @Param("districtName") String districtName, Pageable pageable);

	@Query("""
			    SELECT a FROM AsTask a
			    WHERE a.assignedHandler.id = :handlerId
			      AND a.asProcessDate IS NOT NULL

			      AND (:status IS NULL OR a.status = :status)
			      AND (:start IS NULL OR a.asProcessDate >= :start)
			      AND (:end IS NULL OR a.asProcessDate < :end)

			      AND (
			            :companyKeyword IS NULL OR :companyKeyword = '' OR
			            (a.requestedBy.company IS NOT NULL AND a.requestedBy.company.companyName LIKE CONCAT('%', :companyKeyword, '%'))
			      )

			      AND (
			            :provinceNames IS NULL OR a.doName IN :provinceNames
			      )
			      AND (:cityName IS NULL OR :cityName = '' OR a.siName = :cityName)
			      AND (:districtName IS NULL OR :districtName = '' OR a.guName = :districtName)

			    ORDER BY a.asProcessDate DESC
			""")
	Page<AsTask> findByProcessedDateFlexible(@Param("handlerId") Long handlerId, @Param("status") AsStatus status,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
			@Param("companyKeyword") String companyKeyword, @Param("provinceNames") List<String> provinceNames,
			@Param("cityName") String cityName, @Param("districtName") String districtName, Pageable pageable);

	@Query("""
			    SELECT a
			    FROM AsTaskSchedule s
			    JOIN s.asTask a
			    WHERE a.assignedHandler.id = :handlerId

			      AND (:status IS NULL OR a.status = :status)

			      AND (:startDate IS NULL OR s.scheduledDate >= :startDate)
			      AND (:endDate IS NULL OR s.scheduledDate < :endDate)

			      AND (
			            :companyKeyword IS NULL OR :companyKeyword = '' OR
			            (a.requestedBy.company IS NOT NULL
			             AND a.requestedBy.company.companyName LIKE CONCAT('%', :companyKeyword, '%'))
			      )

			      AND (
			            :provinceNames IS NULL OR a.doName IN :provinceNames
			      )
			      AND (:cityName IS NULL OR :cityName = '' OR a.siName = :cityName)
			      AND (:districtName IS NULL OR :districtName = '' OR a.guName = :districtName)

			    ORDER BY s.scheduledDate DESC, s.orderIndex ASC
			""")
	Page<AsTask> findByScheduledDateFlexible(@Param("handlerId") Long handlerId, @Param("status") AsStatus status,

			@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,

			@Param("companyKeyword") String companyKeyword, @Param("provinceNames") List<String> provinceNames,
			@Param("cityName") String cityName, @Param("districtName") String districtName, Pageable pageable);

	@Query("""
			    SELECT a FROM AsTask a
			    WHERE a.assignedHandler.id = :handlerId
			      AND (:status IS NULL OR a.status = :status)
			      AND a.asProcessDate BETWEEN :start AND :end
			    ORDER BY a.requestedAt DESC
			""")
	Page<AsTask> findByProcessedDate(@Param("handlerId") Long handlerId, @Param("status") AsStatus status,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

	@Query("""
				SELECT a FROM AsTask a
				WHERE (:statuses IS NULL OR a.status = :statuses)
				  AND (:memberId IS NULL OR a.assignedHandler.id = :memberId)
				  AND (:startDate IS NULL OR a.requestedAt >= :startDate)
				  AND (:endDate IS NULL OR a.requestedAt < :endDate)
				ORDER BY a.requestedAt DESC
			""")
	Page<AsTask> findByRequestedDateRange(@Param("memberId") Long memberId, @Param("statuses") AsStatus statuses,
			@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate, Pageable pageable);

	@Query("""
				SELECT a FROM AsTask a
				WHERE (:statuses IS NULL OR a.status = :statuses)
				  AND (:memberId IS NULL OR a.assignedHandler.id = :memberId)
				  AND (:startDate IS NULL OR a.asProcessDate >= :startDate)
				  AND (:endDate IS NULL OR a.asProcessDate < :endDate)
				ORDER BY a.asProcessDate DESC
			""")
	Page<AsTask> findByProcessedDateRange(@Param("memberId") Long memberId, @Param("statuses") AsStatus statuses,
			@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate, Pageable pageable);

	// ========= 신청일 기준 (엑셀 전체) =========
	@EntityGraph(attributePaths = { "requestedBy", "requestedBy.company", "assignedHandler", "assignedTeam" })
	@Query("""
			    SELECT a FROM AsTask a
			    WHERE (:status IS NULL OR a.status = :status)
			      AND (:handlerId IS NULL OR a.assignedHandler.id = :handlerId)
			      AND (:startDate IS NULL OR a.requestedAt >= :startDate)
			      AND (:endDate IS NULL OR a.requestedAt < :endDate)
			    ORDER BY a.requestedAt DESC
			""")
	List<AsTask> findByRequestedDateRangeList(@Param("handlerId") Long handlerId, @Param("status") AsStatus status,
			@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

	// ========= 처리일 기준 (엑셀 전체) =========
	@EntityGraph(attributePaths = { "requestedBy", "requestedBy.company", "assignedHandler", "assignedTeam" })
	@Query("""
			    SELECT a FROM AsTask a
			    WHERE (:status IS NULL OR a.status = :status)
			      AND (:handlerId IS NULL OR a.assignedHandler.id = :handlerId)
			      AND (:startDate IS NULL OR a.asProcessDate >= :startDate)
			      AND (:endDate IS NULL OR a.asProcessDate < :endDate)
			    ORDER BY a.asProcessDate DESC
			""")
	List<AsTask> findByProcessedDateRangeList(@Param("handlerId") Long handlerId, @Param("status") AsStatus status,
			@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

	// ========= 신청일 기준 (화면 페이지) =========
    @EntityGraph(attributePaths = { "requestedBy", "requestedBy.company", "assignedHandler", "assignedTeam" })
    @Query("""
            SELECT a
            FROM AsTask a
            WHERE (:status IS NULL OR a.status = :status)
              AND (:handlerId IS NULL OR a.assignedHandler.id = :handlerId)
              AND (:startDate IS NULL OR a.requestedAt >= :startDate)
              AND (:endDate IS NULL OR a.requestedAt < :endDate)
              AND (
                    :priceFilter IS NULL
                    OR (:priceFilter = 'ZERO' AND a.price = 0)
                    OR (:priceFilter = 'POSITIVE' AND a.price > 0)
                  )
              AND (:paymentCollected IS NULL OR a.paymentCollected = :paymentCollected)
            """)
    Page<AsTask> findByRequestedDateRangePage(
            @Param("handlerId") Long handlerId,
            @Param("status") AsStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("priceFilter") String priceFilter,
            @Param("paymentCollected") Boolean paymentCollected,
            Pageable pageable
    );

    // ========= 처리일 기준 (화면 페이지) =========
    @EntityGraph(attributePaths = { "requestedBy", "requestedBy.company", "assignedHandler", "assignedTeam" })
    @Query("""
            SELECT a
            FROM AsTask a
            WHERE (:status IS NULL OR a.status = :status)
              AND (:handlerId IS NULL OR a.assignedHandler.id = :handlerId)
              AND (:startDate IS NULL OR a.asProcessDate >= :startDate)
              AND (:endDate IS NULL OR a.asProcessDate < :endDate)
              AND (
                    :priceFilter IS NULL
                    OR (:priceFilter = 'ZERO' AND a.price = 0)
                    OR (:priceFilter = 'POSITIVE' AND a.price > 0)
                  )
              AND (:paymentCollected IS NULL OR a.paymentCollected = :paymentCollected)
            """)
    Page<AsTask> findByProcessedDateRangePage(
            @Param("handlerId") Long handlerId,
            @Param("status") AsStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("priceFilter") String priceFilter,
            @Param("paymentCollected") Boolean paymentCollected,
            Pageable pageable
    );

	// requested/processed 조회용(기존이 있다면 그걸 사용)
	@Query("""
			  select t from AsTask t
			  left join t.requestedBy rb
			  left join rb.company c
			  where (:status is null or t.status = :status)
			    and (:companyKeyword is null or :companyKeyword = '' or c.companyName like concat('%', :companyKeyword, '%'))
			""")
	Page<AsTask> searchBase(@Param("status") AsStatus status, @Param("companyKeyword") String companyKeyword,
			Pageable pageable);

	// scheduled 조회: 스케줄에 등록된 것만
	@Query("""
			  select t from AsTaskSchedule s
			  join s.asTask t
			  left join t.requestedBy rb
			  left join rb.company c
			  where (:status is null or t.status = :status)
			    and (:companyKeyword is null or :companyKeyword = '' or c.companyName like concat('%', :companyKeyword, '%'))
			    and (:startDate is null or s.scheduledDate >= :startDate)
			    and (:endDate is null or s.scheduledDate < :endDate)
			""")
	Page<AsTask> searchByScheduledDate(@Param("status") AsStatus status, @Param("companyKeyword") String companyKeyword,
			@Param("startDate") java.time.LocalDate startDate, @Param("endDate") java.time.LocalDate endDate,
			Pageable pageable);

	// requested/processed 조회(지역 포함)
	@Query("""
			  select t from AsTask t
			  left join t.requestedBy rb
			  left join rb.company c
			  where (:status is null or t.status = :status)
			    and (:companyKeyword is null or :companyKeyword = '' or c.companyName like concat('%', :companyKeyword, '%'))
			    and (:provinceName is null or :provinceName = '' or t.doName = :provinceName)
			    and (:cityName is null or :cityName = '' or t.siName = :cityName)
			    and (:districtName is null or :districtName = '' or t.guName = :districtName)
			""")
	Page<AsTask> searchBaseWithRegion(@Param("status") AsStatus status, @Param("companyKeyword") String companyKeyword,
			@Param("provinceName") String provinceName, @Param("cityName") String cityName,
			@Param("districtName") String districtName, Pageable pageable);

	// scheduled 조회: 스케줄 등록된 것만(지역 포함)
	@Query("""
			  select t from AsTaskSchedule s
			  join s.asTask t
			  left join t.requestedBy rb
			  left join rb.company c
			  where (:status is null or t.status = :status)
			    and (:companyKeyword is null or :companyKeyword = '' or c.companyName like concat('%', :companyKeyword, '%'))
			    and (:provinceName is null or :provinceName = '' or t.doName = :provinceName)
			    and (:cityName is null or :cityName = '' or t.siName = :cityName)
			    and (:districtName is null or :districtName = '' or t.guName = :districtName)
			    and (:startDate is null or s.scheduledDate >= :startDate)
			    and (:endDate is null or s.scheduledDate < :endDate)
			""")
	Page<AsTask> searchByScheduledDateWithRegion(@Param("status") AsStatus status,
			@Param("companyKeyword") String companyKeyword, @Param("provinceName") String provinceName,
			@Param("cityName") String cityName, @Param("districtName") String districtName,
			@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, Pageable pageable);

	// requested/processed 조회(지역 포함) - IN으로 변경
	@Query("""
			  select t from AsTask t
			  left join t.requestedBy rb
			  left join rb.company c
			  where (:status is null or t.status = :status)
			    and (:companyKeyword is null or :companyKeyword = '' or c.companyName like concat('%', :companyKeyword, '%'))
			    and (:provinceNames is null or t.doName in :provinceNames)
			    and (:cityNames is null or t.siName in :cityNames)
			    and (:districtNames is null or t.guName in :districtNames)
			""")
	Page<AsTask> searchBaseWithRegion(@Param("status") AsStatus status, @Param("companyKeyword") String companyKeyword,
			@Param("provinceNames") List<String> provinceNames, @Param("cityNames") List<String> cityNames,
			@Param("districtNames") List<String> districtNames, Pageable pageable);

	// scheduled 조회: 스케줄 등록된 것만(지역 포함) - IN으로 변경
	@Query("""
			  select t from AsTaskSchedule s
			  join s.asTask t
			  left join t.requestedBy rb
			  left join rb.company c
			  where (:status is null or t.status = :status)
			    and (:companyKeyword is null or :companyKeyword = '' or c.companyName like concat('%', :companyKeyword, '%'))
			    and (:provinceNames is null or t.doName in :provinceNames)
			    and (:cityNames is null or t.siName in :cityNames)
			    and (:districtNames is null or t.guName in :districtNames)
			    and (:startDate is null or s.scheduledDate >= :startDate)
			    and (:endDate is null or s.scheduledDate < :endDate)
			""")
	Page<AsTask> searchByScheduledDateWithRegion(@Param("status") AsStatus status,
			@Param("companyKeyword") String companyKeyword, @Param("provinceNames") List<String> provinceNames,
			@Param("cityNames") List<String> cityNames, @Param("districtNames") List<String> districtNames,
			@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, Pageable pageable);
	
	
	// ========= 신청일 기준 (엑셀 전체조회) =========
	@EntityGraph(attributePaths = { "requestedBy", "requestedBy.company", "assignedHandler", "assignedTeam" })
	@Query("""
	        SELECT a
	        FROM AsTask a
	        WHERE (:status IS NULL OR a.status = :status)
	          AND (:handlerId IS NULL OR a.assignedHandler.id = :handlerId)
	          AND (:startDate IS NULL OR a.requestedAt >= :startDate)
	          AND (:endDate IS NULL OR a.requestedAt < :endDate)
	          AND (
	                :priceFilter IS NULL
	                OR (:priceFilter = 'ZERO' AND a.price = 0)
	                OR (:priceFilter = 'POSITIVE' AND a.price > 0)
	              )
	          AND (:paymentCollected IS NULL OR a.paymentCollected = :paymentCollected)
	        """)
	List<AsTask> findByRequestedDateRangeAll(
	        @Param("handlerId") Long handlerId,
	        @Param("status") AsStatus status,
	        @Param("startDate") LocalDateTime startDate,
	        @Param("endDate") LocalDateTime endDate,
	        @Param("priceFilter") String priceFilter,
	        @Param("paymentCollected") Boolean paymentCollected,
	        Sort sort
	);

	// ========= 처리일 기준 (엑셀 전체조회) =========
	@EntityGraph(attributePaths = { "requestedBy", "requestedBy.company", "assignedHandler", "assignedTeam" })
	@Query("""
	        SELECT a
	        FROM AsTask a
	        WHERE (:status IS NULL OR a.status = :status)
	          AND (:handlerId IS NULL OR a.assignedHandler.id = :handlerId)
	          AND (:startDate IS NULL OR a.asProcessDate >= :startDate)
	          AND (:endDate IS NULL OR a.asProcessDate < :endDate)
	          AND (
	                :priceFilter IS NULL
	                OR (:priceFilter = 'ZERO' AND a.price = 0)
	                OR (:priceFilter = 'POSITIVE' AND a.price > 0)
	              )
	          AND (:paymentCollected IS NULL OR a.paymentCollected = :paymentCollected)
	        """)
	List<AsTask> findByProcessedDateRangeAll(
	        @Param("handlerId") Long handlerId,
	        @Param("status") AsStatus status,
	        @Param("startDate") LocalDateTime startDate,
	        @Param("endDate") LocalDateTime endDate,
	        @Param("priceFilter") String priceFilter,
	        @Param("paymentCollected") Boolean paymentCollected,
	        Sort sort
	);
	
	@Query(value = """
	        select a
	        from AsTask a
	        left join a.requestedBy rb
	        left join rb.company company
	        where a.assignedHandler.id = :handlerId
	          and (:status is null or a.status = :status)
	          and (:start is null or a.requestedAt >= :start)
	          and (:end is null or a.requestedAt < :end)
	          and (:companyKeyword is null or :companyKeyword = '' or lower(company.companyName) like lower(concat('%', :companyKeyword, '%')))
	          and (:provinceNames is null or a.doName in :provinceNames)
	          and (:cityName is null or a.siName = :cityName)
	          and (:districtName is null or a.guName = :districtName)
	        order by
	          case when :addressSort = 'asc'
	                    and trim(concat(coalesce(a.roadAddress, ''), ' ', coalesce(a.detailAddress, ''))) = ''
	               then 1 else 0 end asc,
	          case when :addressSort = 'asc'
	               then lower(trim(concat(coalesce(a.roadAddress, ''), ' ', coalesce(a.detailAddress, ''))))
	               else null end asc,

	          case when :addressSort = 'desc'
	                    and trim(concat(coalesce(a.roadAddress, ''), ' ', coalesce(a.detailAddress, ''))) = ''
	               then 1 else 0 end asc,
	          case when :addressSort = 'desc'
	               then lower(trim(concat(coalesce(a.roadAddress, ''), ' ', coalesce(a.detailAddress, ''))))
	               else null end desc,

	          case when :visitTimeSort = 'asc' and a.visitPlannedTime is null then 1 else 0 end asc,
	          case when :visitTimeSort = 'asc' then a.visitPlannedTime else null end asc,
	          case when :visitTimeSort = 'desc' and a.visitPlannedTime is null then 1 else 0 end asc,
	          case when :visitTimeSort = 'desc' then a.visitPlannedTime else null end desc,

	          a.id desc
	        """,
	        countQuery = """
	        select count(a)
	        from AsTask a
	        left join a.requestedBy rb
	        left join rb.company company
	        where a.assignedHandler.id = :handlerId
	          and (:status is null or a.status = :status)
	          and (:start is null or a.requestedAt >= :start)
	          and (:end is null or a.requestedAt < :end)
	          and (:companyKeyword is null or :companyKeyword = '' or lower(company.companyName) like lower(concat('%', :companyKeyword, '%')))
	          and (:provinceNames is null or a.doName in :provinceNames)
	          and (:cityName is null or a.siName = :cityName)
	          and (:districtName is null or a.guName = :districtName)
	        """)
	Page<AsTask> findByRequestedDateFlexible(@Param("handlerId") Long handlerId,
	                                         @Param("status") AsStatus status,
	                                         @Param("start") LocalDateTime start,
	                                         @Param("end") LocalDateTime end,
	                                         @Param("companyKeyword") String companyKeyword,
	                                         @Param("provinceNames") List<String> provinceNames,
	                                         @Param("cityName") String cityName,
	                                         @Param("districtName") String districtName,
	                                         @Param("visitTimeSort") String visitTimeSort,
	                                         @Param("addressSort") String addressSort,
	                                         Pageable pageable);
	
	@Query(value = """
	        select a
	        from AsTask a
	        left join a.requestedBy rb
	        left join rb.company company
	        where a.assignedHandler.id = :handlerId
	          and (:status is null or a.status = :status)
	          and (:start is null or a.asProcessDate >= :start)
	          and (:end is null or a.asProcessDate < :end)
	          and (:companyKeyword is null or :companyKeyword = '' or lower(company.companyName) like lower(concat('%', :companyKeyword, '%')))
	          and (:provinceNames is null or a.doName in :provinceNames)
	          and (:cityName is null or a.siName = :cityName)
	          and (:districtName is null or a.guName = :districtName)
	        order by
	          case when :addressSort = 'asc'
	                    and trim(concat(coalesce(a.roadAddress, ''), ' ', coalesce(a.detailAddress, ''))) = ''
	               then 1 else 0 end asc,
	          case when :addressSort = 'asc'
	               then lower(trim(concat(coalesce(a.roadAddress, ''), ' ', coalesce(a.detailAddress, ''))))
	               else null end asc,

	          case when :addressSort = 'desc'
	                    and trim(concat(coalesce(a.roadAddress, ''), ' ', coalesce(a.detailAddress, ''))) = ''
	               then 1 else 0 end asc,
	          case when :addressSort = 'desc'
	               then lower(trim(concat(coalesce(a.roadAddress, ''), ' ', coalesce(a.detailAddress, ''))))
	               else null end desc,

	          case when :visitTimeSort = 'asc' and a.visitPlannedTime is null then 1 else 0 end asc,
	          case when :visitTimeSort = 'asc' then a.visitPlannedTime else null end asc,
	          case when :visitTimeSort = 'desc' and a.visitPlannedTime is null then 1 else 0 end asc,
	          case when :visitTimeSort = 'desc' then a.visitPlannedTime else null end desc,

	          a.id desc
	        """,
	        countQuery = """
	        select count(a)
	        from AsTask a
	        left join a.requestedBy rb
	        left join rb.company company
	        where a.assignedHandler.id = :handlerId
	          and (:status is null or a.status = :status)
	          and (:start is null or a.asProcessDate >= :start)
	          and (:end is null or a.asProcessDate < :end)
	          and (:companyKeyword is null or :companyKeyword = '' or lower(company.companyName) like lower(concat('%', :companyKeyword, '%')))
	          and (:provinceNames is null or a.doName in :provinceNames)
	          and (:cityName is null or a.siName = :cityName)
	          and (:districtName is null or a.guName = :districtName)
	        """)
	Page<AsTask> findByProcessedDateFlexible(@Param("handlerId") Long handlerId,
	                                         @Param("status") AsStatus status,
	                                         @Param("start") LocalDateTime start,
	                                         @Param("end") LocalDateTime end,
	                                         @Param("companyKeyword") String companyKeyword,
	                                         @Param("provinceNames") List<String> provinceNames,
	                                         @Param("cityName") String cityName,
	                                         @Param("districtName") String districtName,
	                                         @Param("visitTimeSort") String visitTimeSort,
	                                         @Param("addressSort") String addressSort,
	                                         Pageable pageable);
	
	@Query(value = """
	        SELECT a
	        FROM AsTaskSchedule s
	        JOIN s.asTask a
	        LEFT JOIN a.requestedBy rb
	        LEFT JOIN rb.company company
	        WHERE a.assignedHandler.id = :handlerId
	          AND (:status IS NULL OR a.status = :status)
	          AND (:startDate IS NULL OR s.scheduledDate >= :startDate)
	          AND (:endDate IS NULL OR s.scheduledDate <= :endDate)
	          AND (:companyKeyword IS NULL OR :companyKeyword = '' OR lower(company.companyName) like lower(concat('%', :companyKeyword, '%')))
	          AND (:provinceNames IS NULL OR a.doName in :provinceNames)
	          AND (:cityName IS NULL OR a.siName = :cityName)
	          AND (:districtName IS NULL OR a.guName = :districtName)
	        ORDER BY
	          case when :addressSort = 'asc'
	                    and trim(concat(coalesce(a.roadAddress, ''), ' ', coalesce(a.detailAddress, ''))) = ''
	               then 1 else 0 end asc,
	          case when :addressSort = 'asc'
	               then lower(trim(concat(coalesce(a.roadAddress, ''), ' ', coalesce(a.detailAddress, ''))))
	               else null end asc,

	          case when :addressSort = 'desc'
	                    and trim(concat(coalesce(a.roadAddress, ''), ' ', coalesce(a.detailAddress, ''))) = ''
	               then 1 else 0 end asc,
	          case when :addressSort = 'desc'
	               then lower(trim(concat(coalesce(a.roadAddress, ''), ' ', coalesce(a.detailAddress, ''))))
	               else null end desc,

	          case when :scheduledDateSort = 'asc' and s.scheduledDate is null then 1 else 0 end asc,
	          case when :scheduledDateSort = 'asc' then s.scheduledDate else null end asc,
	          case when :scheduledDateSort = 'desc' and s.scheduledDate is null then 1 else 0 end asc,
	          case when :scheduledDateSort = 'desc' then s.scheduledDate else null end desc,

	          case when :visitTimeSort = 'asc' and a.visitPlannedTime is null then 1 else 0 end asc,
	          case when :visitTimeSort = 'asc' then a.visitPlannedTime else null end asc,
	          case when :visitTimeSort = 'desc' and a.visitPlannedTime is null then 1 else 0 end asc,
	          case when :visitTimeSort = 'desc' then a.visitPlannedTime else null end desc,

	          case when :scheduledDateSort is null and :visitTimeSort is null then s.scheduledDate else null end desc,
	          case when :scheduledDateSort is null and :visitTimeSort is null then s.orderIndex else null end asc,

	          a.id desc
	        """,
	        countQuery = """
	        SELECT COUNT(a)
	        FROM AsTaskSchedule s
	        JOIN s.asTask a
	        LEFT JOIN a.requestedBy rb
	        LEFT JOIN rb.company company
	        WHERE a.assignedHandler.id = :handlerId
	          AND (:status IS NULL OR a.status = :status)
	          AND (:startDate IS NULL OR s.scheduledDate >= :startDate)
	          AND (:endDate IS NULL OR s.scheduledDate <= :endDate)
	          AND (:companyKeyword IS NULL OR :companyKeyword = '' OR lower(company.companyName) like lower(concat('%', :companyKeyword, '%')))
	          AND (:provinceNames IS NULL OR a.doName in :provinceNames)
	          AND (:cityName IS NULL OR a.siName = :cityName)
	          AND (:districtName IS NULL OR a.guName = :districtName)
	        """)
	Page<AsTask> findByScheduledDateFlexible(@Param("handlerId") Long handlerId,
	                                         @Param("status") AsStatus status,
	                                         @Param("startDate") LocalDate startDate,
	                                         @Param("endDate") LocalDate endDate,
	                                         @Param("companyKeyword") String companyKeyword,
	                                         @Param("provinceNames") List<String> provinceNames,
	                                         @Param("cityName") String cityName,
	                                         @Param("districtName") String districtName,
	                                         @Param("visitTimeSort") String visitTimeSort,
	                                         @Param("scheduledDateSort") String scheduledDateSort,
	                                         @Param("addressSort") String addressSort,
	                                         Pageable pageable);

	@EntityGraph(attributePaths = { "requestedBy", "requestedBy.company", "assignedHandler", "assignedTeam" })
	@Query(
	    value = """
	        SELECT a
	        FROM AsTask a
	        LEFT JOIN a.requestedBy rb
	        LEFT JOIN rb.company c
	        LEFT JOIN a.assignedHandler ah
	        LEFT JOIN AsTaskSchedule s ON s.asTask = a
	        WHERE (:status IS NULL OR a.status = :status)
	          AND (:handlerId IS NULL OR ah.id = :handlerId)
	          AND (:startDate IS NULL OR a.requestedAt >= :startDate)
	          AND (:endDate IS NULL OR a.requestedAt < :endDate)
	          AND (
	                :priceFilter IS NULL
	                OR (:priceFilter = 'ZERO' AND a.price = 0)
	                OR (:priceFilter = 'POSITIVE' AND a.price > 0)
	              )
	          AND (:paymentCollected IS NULL OR a.paymentCollected = :paymentCollected)
	          AND (
	                :keyword IS NULL
	                OR (
	                    :keywordType = 'all' AND (
	                        LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                    )
	                )
	                OR (:keywordType = 'companyName'
	                    AND LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'requesterName'
	                    AND LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'customerName'
	                    AND LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'subject'
	                    AND LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'productName'
	                    AND LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantName'
	                    AND LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantPhone'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	                OR (:keywordType = 'onsiteContact'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	              )
	        ORDER BY
	          CASE WHEN :sortField = 'companyName' AND :sortDir = 'asc' AND TRIM(COALESCE(c.companyName, '')) = '' THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'companyName' AND :sortDir = 'asc' THEN LOWER(COALESCE(c.companyName, '')) ELSE NULL END ASC,
	          CASE WHEN :sortField = 'companyName' AND :sortDir = 'desc' AND TRIM(COALESCE(c.companyName, '')) = '' THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'companyName' AND :sortDir = 'desc' THEN LOWER(COALESCE(c.companyName, '')) ELSE NULL END DESC,

	          CASE WHEN :sortField = 'requesterName' AND :sortDir = 'asc' AND TRIM(COALESCE(rb.name, '')) = '' THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'requesterName' AND :sortDir = 'asc' THEN LOWER(COALESCE(rb.name, '')) ELSE NULL END ASC,
	          CASE WHEN :sortField = 'requesterName' AND :sortDir = 'desc' AND TRIM(COALESCE(rb.name, '')) = '' THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'requesterName' AND :sortDir = 'desc' THEN LOWER(COALESCE(rb.name, '')) ELSE NULL END DESC,

	          CASE WHEN :sortField = 'handlerName' AND :sortDir = 'asc' AND TRIM(COALESCE(ah.name, '')) = '' THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'handlerName' AND :sortDir = 'asc' THEN LOWER(COALESCE(ah.name, '')) ELSE NULL END ASC,
	          CASE WHEN :sortField = 'handlerName' AND :sortDir = 'desc' AND TRIM(COALESCE(ah.name, '')) = '' THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'handlerName' AND :sortDir = 'desc' THEN LOWER(COALESCE(ah.name, '')) ELSE NULL END DESC,

	          CASE WHEN :sortField = 'requestedAt' AND :sortDir = 'asc' AND a.requestedAt IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'requestedAt' AND :sortDir = 'asc' THEN a.requestedAt ELSE NULL END ASC,
	          CASE WHEN :sortField = 'requestedAt' AND :sortDir = 'desc' AND a.requestedAt IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'requestedAt' AND :sortDir = 'desc' THEN a.requestedAt ELSE NULL END DESC,

	          CASE WHEN :sortField = 'scheduledDate' AND :sortDir = 'asc' AND s.scheduledDate IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'scheduledDate' AND :sortDir = 'asc' THEN s.scheduledDate ELSE NULL END ASC,
	          CASE WHEN :sortField = 'scheduledDate' AND :sortDir = 'asc' THEN s.orderIndex ELSE NULL END ASC,
	          CASE WHEN :sortField = 'scheduledDate' AND :sortDir = 'desc' AND s.scheduledDate IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'scheduledDate' AND :sortDir = 'desc' THEN s.scheduledDate ELSE NULL END DESC,
	          CASE WHEN :sortField = 'scheduledDate' AND :sortDir = 'desc' THEN s.orderIndex ELSE NULL END ASC,

	          CASE WHEN :sortField = 'asProcessDate' AND :sortDir = 'asc' AND a.asProcessDate IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'asProcessDate' AND :sortDir = 'asc' THEN a.asProcessDate ELSE NULL END ASC,
	          CASE WHEN :sortField = 'asProcessDate' AND :sortDir = 'desc' AND a.asProcessDate IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'asProcessDate' AND :sortDir = 'desc' THEN a.asProcessDate ELSE NULL END DESC,

	          CASE WHEN :sortField = 'status' AND :sortDir = 'asc' AND a.status IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'status' AND :sortDir = 'asc' THEN a.status ELSE NULL END ASC,
	          CASE WHEN :sortField = 'status' AND :sortDir = 'desc' AND a.status IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'status' AND :sortDir = 'desc' THEN a.status ELSE NULL END DESC,

	          CASE WHEN (:sortField IS NULL OR :sortField = '') AND a.requestedAt IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN (:sortField IS NULL OR :sortField = '') THEN a.requestedAt ELSE NULL END DESC,

	          a.id DESC
	        """,
	    countQuery = """
	        SELECT COUNT(a)
	        FROM AsTask a
	        LEFT JOIN a.requestedBy rb
	        LEFT JOIN rb.company c
	        LEFT JOIN a.assignedHandler ah
	        WHERE (:status IS NULL OR a.status = :status)
	          AND (:handlerId IS NULL OR ah.id = :handlerId)
	          AND (:startDate IS NULL OR a.requestedAt >= :startDate)
	          AND (:endDate IS NULL OR a.requestedAt < :endDate)
	          AND (
	                :priceFilter IS NULL
	                OR (:priceFilter = 'ZERO' AND a.price = 0)
	                OR (:priceFilter = 'POSITIVE' AND a.price > 0)
	              )
	          AND (:paymentCollected IS NULL OR a.paymentCollected = :paymentCollected)
	          AND (
	                :keyword IS NULL
	                OR (
	                    :keywordType = 'all' AND (
	                        LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                    )
	                )
	                OR (:keywordType = 'companyName'
	                    AND LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'requesterName'
	                    AND LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'customerName'
	                    AND LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'subject'
	                    AND LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'productName'
	                    AND LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantName'
	                    AND LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantPhone'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	                OR (:keywordType = 'onsiteContact'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	              )
	        """
	)
	Page<AsTask> findByRequestedDateRangePageWithScheduleSort(
	        @Param("handlerId") Long handlerId,
	        @Param("status") AsStatus status,
	        @Param("startDate") LocalDateTime startDate,
	        @Param("endDate") LocalDateTime endDate,
	        @Param("priceFilter") String priceFilter,
	        @Param("paymentCollected") Boolean paymentCollected,
	        @Param("keywordType") String keywordType,
	        @Param("keyword") String keyword,
	        @Param("sortField") String sortField,
	        @Param("sortDir") String sortDir,
	        Pageable pageable
	);

	@EntityGraph(attributePaths = { "requestedBy", "requestedBy.company", "assignedHandler", "assignedTeam" })
	@Query(
	    value = """
	        SELECT a
	        FROM AsTask a
	        LEFT JOIN a.requestedBy rb
	        LEFT JOIN rb.company c
	        LEFT JOIN a.assignedHandler ah
	        LEFT JOIN AsTaskSchedule s ON s.asTask = a
	        WHERE (:status IS NULL OR a.status = :status)
	          AND (:handlerId IS NULL OR ah.id = :handlerId)
	          AND (:startDate IS NULL OR a.asProcessDate >= :startDate)
	          AND (:endDate IS NULL OR a.asProcessDate < :endDate)
	          AND (
	                :priceFilter IS NULL
	                OR (:priceFilter = 'ZERO' AND a.price = 0)
	                OR (:priceFilter = 'POSITIVE' AND a.price > 0)
	              )
	          AND (:paymentCollected IS NULL OR a.paymentCollected = :paymentCollected)
	          AND (
	                :keyword IS NULL
	                OR (
	                    :keywordType = 'all' AND (
	                        LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                    )
	                )
	                OR (:keywordType = 'companyName'
	                    AND LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'requesterName'
	                    AND LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'customerName'
	                    AND LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'subject'
	                    AND LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'productName'
	                    AND LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantName'
	                    AND LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantPhone'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	                OR (:keywordType = 'onsiteContact'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	              )
	        ORDER BY
	          CASE WHEN :sortField = 'companyName' AND :sortDir = 'asc' AND TRIM(COALESCE(c.companyName, '')) = '' THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'companyName' AND :sortDir = 'asc' THEN LOWER(COALESCE(c.companyName, '')) ELSE NULL END ASC,
	          CASE WHEN :sortField = 'companyName' AND :sortDir = 'desc' AND TRIM(COALESCE(c.companyName, '')) = '' THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'companyName' AND :sortDir = 'desc' THEN LOWER(COALESCE(c.companyName, '')) ELSE NULL END DESC,

	          CASE WHEN :sortField = 'requesterName' AND :sortDir = 'asc' AND TRIM(COALESCE(rb.name, '')) = '' THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'requesterName' AND :sortDir = 'asc' THEN LOWER(COALESCE(rb.name, '')) ELSE NULL END ASC,
	          CASE WHEN :sortField = 'requesterName' AND :sortDir = 'desc' AND TRIM(COALESCE(rb.name, '')) = '' THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'requesterName' AND :sortDir = 'desc' THEN LOWER(COALESCE(rb.name, '')) ELSE NULL END DESC,

	          CASE WHEN :sortField = 'handlerName' AND :sortDir = 'asc' AND TRIM(COALESCE(ah.name, '')) = '' THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'handlerName' AND :sortDir = 'asc' THEN LOWER(COALESCE(ah.name, '')) ELSE NULL END ASC,
	          CASE WHEN :sortField = 'handlerName' AND :sortDir = 'desc' AND TRIM(COALESCE(ah.name, '')) = '' THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'handlerName' AND :sortDir = 'desc' THEN LOWER(COALESCE(ah.name, '')) ELSE NULL END DESC,

	          CASE WHEN :sortField = 'requestedAt' AND :sortDir = 'asc' AND a.requestedAt IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'requestedAt' AND :sortDir = 'asc' THEN a.requestedAt ELSE NULL END ASC,
	          CASE WHEN :sortField = 'requestedAt' AND :sortDir = 'desc' AND a.requestedAt IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'requestedAt' AND :sortDir = 'desc' THEN a.requestedAt ELSE NULL END DESC,

	          CASE WHEN :sortField = 'scheduledDate' AND :sortDir = 'asc' AND s.scheduledDate IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'scheduledDate' AND :sortDir = 'asc' THEN s.scheduledDate ELSE NULL END ASC,
	          CASE WHEN :sortField = 'scheduledDate' AND :sortDir = 'asc' THEN s.orderIndex ELSE NULL END ASC,
	          CASE WHEN :sortField = 'scheduledDate' AND :sortDir = 'desc' AND s.scheduledDate IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'scheduledDate' AND :sortDir = 'desc' THEN s.scheduledDate ELSE NULL END DESC,
	          CASE WHEN :sortField = 'scheduledDate' AND :sortDir = 'desc' THEN s.orderIndex ELSE NULL END ASC,

	          CASE WHEN :sortField = 'asProcessDate' AND :sortDir = 'asc' AND a.asProcessDate IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'asProcessDate' AND :sortDir = 'asc' THEN a.asProcessDate ELSE NULL END ASC,
	          CASE WHEN :sortField = 'asProcessDate' AND :sortDir = 'desc' AND a.asProcessDate IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'asProcessDate' AND :sortDir = 'desc' THEN a.asProcessDate ELSE NULL END DESC,

	          CASE WHEN :sortField = 'status' AND :sortDir = 'asc' AND a.status IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'status' AND :sortDir = 'asc' THEN a.status ELSE NULL END ASC,
	          CASE WHEN :sortField = 'status' AND :sortDir = 'desc' AND a.status IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN :sortField = 'status' AND :sortDir = 'desc' THEN a.status ELSE NULL END DESC,

	          CASE WHEN (:sortField IS NULL OR :sortField = '') AND a.asProcessDate IS NULL THEN 1 ELSE 0 END ASC,
	          CASE WHEN (:sortField IS NULL OR :sortField = '') THEN a.asProcessDate ELSE NULL END DESC,

	          a.id DESC
	        """,
	    countQuery = """
	        SELECT COUNT(a)
	        FROM AsTask a
	        LEFT JOIN a.requestedBy rb
	        LEFT JOIN rb.company c
	        LEFT JOIN a.assignedHandler ah
	        WHERE (:status IS NULL OR a.status = :status)
	          AND (:handlerId IS NULL OR ah.id = :handlerId)
	          AND (:startDate IS NULL OR a.asProcessDate >= :startDate)
	          AND (:endDate IS NULL OR a.asProcessDate < :endDate)
	          AND (
	                :priceFilter IS NULL
	                OR (:priceFilter = 'ZERO' AND a.price = 0)
	                OR (:priceFilter = 'POSITIVE' AND a.price > 0)
	              )
	          AND (:paymentCollected IS NULL OR a.paymentCollected = :paymentCollected)
	          AND (
	                :keyword IS NULL
	                OR (
	                    :keywordType = 'all' AND (
	                        LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                    )
	                )
	                OR (:keywordType = 'companyName'
	                    AND LOWER(COALESCE(c.companyName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'requesterName'
	                    AND LOWER(COALESCE(rb.name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'customerName'
	                    AND LOWER(COALESCE(a.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'subject'
	                    AND LOWER(COALESCE(a.subject, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'productName'
	                    AND LOWER(COALESCE(a.productName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantName'
	                    AND LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
	                OR (:keywordType = 'applicantPhone'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	                OR (:keywordType = 'onsiteContact'
	                    AND LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                        LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%')))
	              )
	        """
	)
	Page<AsTask> findByProcessedDateRangePageWithScheduleSort(
	        @Param("handlerId") Long handlerId,
	        @Param("status") AsStatus status,
	        @Param("startDate") LocalDateTime startDate,
	        @Param("endDate") LocalDateTime endDate,
	        @Param("priceFilter") String priceFilter,
	        @Param("paymentCollected") Boolean paymentCollected,
	        @Param("keywordType") String keywordType,
	        @Param("keyword") String keyword,
	        @Param("sortField") String sortField,
	        @Param("sortDir") String sortDir,
	        Pageable pageable
	);
	
	
}
