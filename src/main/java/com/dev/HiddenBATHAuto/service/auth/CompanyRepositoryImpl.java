package com.dev.HiddenBATHAuto.service.auth;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.dev.HiddenBATHAuto.dto.CompanyListDTO;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepositoryCustom;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CompanyRepositoryImpl implements CompanyRepositoryCustom {

	private final EntityManager em;

	@Override
	public Page<CompanyListDTO> findCompanyList(String keyword, Pageable pageable) {
		boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
		String baseQuery = """
				SELECT new com.dev.HiddenBATHAuto.dto.CompanyListDTO(
				    c.id,
				    c.companyName,
				    (SELECT m.name FROM Member m WHERE m.company = c AND m.role = com.dev.HiddenBATHAuto.model.auth.MemberRole.CUSTOMER_REPRESENTATIVE),
				    c.createdAt,
				    COALESCE(sm.name, '미지정'),
				    (SELECT COUNT(m) FROM Member m WHERE m.company = c)
				)
				FROM Company c
				LEFT JOIN c.salesManager sm
				"""
				+ (hasKeyword ? """
						LEFT JOIN Member m ON m.company = c
						WHERE c.companyName LIKE :kw
						   OR m.name LIKE :kw
						""" : "");

		TypedQuery<CompanyListDTO> query = em.createQuery(baseQuery, CompanyListDTO.class);
		if (hasKeyword)
			query.setParameter("kw", "%" + keyword + "%");

		String countQuery = """
				SELECT COUNT(DISTINCT c.id)
				FROM Company c
				""" + (hasKeyword ? """
				LEFT JOIN Member m ON m.company = c
				WHERE c.companyName LIKE :kw
				   OR m.name LIKE :kw
				""" : "");

		TypedQuery<Long> countTypedQuery = em.createQuery(countQuery, Long.class);
		if (hasKeyword)
			countTypedQuery.setParameter("kw", "%" + keyword + "%");

		List<CompanyListDTO> results = query.setFirstResult((int) pageable.getOffset())
				.setMaxResults(pageable.getPageSize()).getResultList();

		Long total = countTypedQuery.getSingleResult();

		return new PageImpl<>(results, pageable, total);
	}

}
