package com.dev.HiddenBATHAuto.repository.auth;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.model.auth.Company;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

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

}
