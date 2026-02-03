package com.dev.HiddenBATHAuto.repository.auth;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Company;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    // ✅ 사업자등록번호 중복체크(본인 회사 제외)
    boolean existsByBusinessNumberAndIdNot(String businessNumber, Long id);
	
	Company findByCompanyName(String name);

	Optional<Company> findByRegistrationKey(String registrationKey);

	@Query("""
        SELECT DISTINCT c FROM Company c
        LEFT JOIN FETCH c.salesManager
        LEFT JOIN c.members m
        WHERE (:keyword IS NULL OR
              (:searchType = 'company' AND c.companyName LIKE CONCAT('%', :keyword, '%')) OR
              (:searchType = 'member' AND m.name LIKE CONCAT('%', :keyword, '%')))
        """)
    Page<Company> findWithSearch(@Param("keyword") String keyword,
                                 @Param("searchType") String searchType,
                                 Pageable pageable);

    boolean existsByBusinessNumber(String businessNumber);

    Optional<Company> findByBusinessNumber(String businessNumber);
    
    // ✅ 배송지까지 함께 로딩 (OSIV 없어도 안전)
    @EntityGraph(attributePaths = {"deliveryAddresses"})
    Optional<Company> findWithDeliveryAddressesById(Long id);
}
