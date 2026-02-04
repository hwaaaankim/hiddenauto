package com.dev.HiddenBATHAuto.repository.auth;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.dto.client.CompanyListRowDto;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

@Repository
public class CompanyRepositoryImpl implements CompanyRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<CompanyListRowDto> searchCompanyList(String keyword, String searchType, String sortField, String sortDir, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // =========================
        // 1) Content Query
        // =========================
        CriteriaQuery<CompanyListRowDto> cq = cb.createQuery(CompanyListRowDto.class);
        Root<Company> company = cq.from(Company.class);

        // salesManager (to-one)
        Join<Object, Object> salesManager = company.join("salesManager", JoinType.LEFT);

        // members join (for count / representative 계산)
        Join<Company, Member> memberJoin = company.join("members", JoinType.LEFT);

        // memberCount
        Expression<Long> memberCountExp = cb.count(memberJoin.get("id"));

        // representativeName = max(case when role=CUSTOMER_REPRESENTATIVE then name else '' end)
        Expression<String> repCase =
                cb.<String>selectCase()
                        .when(
                                cb.equal(memberJoin.get("role"), MemberRole.CUSTOMER_REPRESENTATIVE),
                                memberJoin.get("name")
                        )
                        .otherwise("");

        // ✅ greatest 대신 max 함수 사용 (문자열 집계 안전)
        Expression<String> representativeExp = cb.function("max", String.class, repCase);

        // DTO select
        cq.select(cb.construct(
                CompanyListRowDto.class,
                company.get("id"),
                company.get("companyName"),
                representativeExp,
                company.get("createdAt"),
                salesManager.get("name"),
                memberCountExp
        ));

        // where
        List<Predicate> predicates = buildSearchPredicates(cb, cq, company, keyword, searchType);
        if (!predicates.isEmpty()) {
            cq.where(cb.and(predicates.toArray(new Predicate[0])));
        }

        // group by (집계 사용)
        cq.groupBy(company.get("id"), company.get("companyName"), company.get("createdAt"), salesManager.get("name"));

        // order by
        cq.orderBy(buildOrderBy(cb, company, memberCountExp, representativeExp, sortField, sortDir));

        TypedQuery<CompanyListRowDto> query = em.createQuery(cq);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        List<CompanyListRowDto> content = query.getResultList();

        // =========================
        // 2) Count Query
        // =========================
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<Company> countRoot = countCq.from(Company.class);

        List<Predicate> countPredicates = buildSearchPredicates(cb, countCq, countRoot, keyword, searchType);
        if (!countPredicates.isEmpty()) {
            countCq.where(cb.and(countPredicates.toArray(new Predicate[0])));
        }

        // distinct count
        countCq.select(cb.countDistinct(countRoot.get("id")));
        Long total = em.createQuery(countCq).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public List<Company> findAllForExcel(String keyword, String searchType, String sortField, String sortDir) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Company> cq = cb.createQuery(Company.class);
        Root<Company> company = cq.from(Company.class);

        // ✅ to-one 은 fetch 해도 문제 없음
        company.fetch("salesManager", JoinType.LEFT);

        cq.select(company).distinct(true);

        List<Predicate> predicates = buildSearchPredicates(cb, cq, company, keyword, searchType);
        if (!predicates.isEmpty()) {
            cq.where(cb.and(predicates.toArray(new Predicate[0])));
        }

        // ✅ 엑셀 정렬은 회사 단일 테이블 기준으로 적용
        // (대표자명/직원수까지 완전 동일 정렬을 원하시면 2단계 정렬 id 조회로 강화 가능)
        boolean asc = "asc".equalsIgnoreCase(sortDir);
        if ("companyName".equalsIgnoreCase(sortField)) {
            cq.orderBy(asc ? cb.asc(company.get("companyName")) : cb.desc(company.get("companyName")),
                    cb.desc(company.get("id")));
        } else if ("createdAt".equalsIgnoreCase(sortField)) {
            cq.orderBy(asc ? cb.asc(company.get("createdAt")) : cb.desc(company.get("createdAt")),
                    cb.desc(company.get("id")));
        } else {
            cq.orderBy(cb.desc(company.get("createdAt")), cb.desc(company.get("id")));
        }

        return em.createQuery(cq).getResultList();
    }


    // -----------------------------
    // 공통: 검색 조건
    // -----------------------------
    private List<Predicate> buildSearchPredicates(CriteriaBuilder cb, CriteriaQuery<?> cq, Root<Company> company,
                                                 String keyword, String searchType) {
        List<Predicate> predicates = new ArrayList<>();

        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword + "%";

            if ("member".equalsIgnoreCase(searchType)) {
                // exists (select 1 from Member m where m.company=company and m.name like :like)
                Subquery<Long> sq = cq.subquery(Long.class);
                Root<Member> m = sq.from(Member.class);
                sq.select(cb.literal(1L));
                sq.where(
                        cb.equal(m.get("company"), company),
                        cb.like(m.get("name"), like)
                );
                predicates.add(cb.exists(sq));
            } else {
                predicates.add(cb.like(company.get("companyName"), like));
            }
        }
        return predicates;
    }

    // -----------------------------
    // 리스트 정렬 (정확 지원)
    // -----------------------------
    private List<Order> buildOrderBy(CriteriaBuilder cb,
                                    Root<Company> company,
                                    Expression<Long> memberCountExp,
                                    Expression<String> representativeExp,
                                    String sortField,
                                    String sortDir) {

        boolean asc = "asc".equalsIgnoreCase(sortDir);

        Expression<?> sortExp;
        if ("companyName".equalsIgnoreCase(sortField)) {
            sortExp = company.get("companyName");
        } else if ("representativeName".equalsIgnoreCase(sortField)) {
            sortExp = representativeExp;
        } else if ("memberCount".equalsIgnoreCase(sortField)) {
            sortExp = memberCountExp;
        } else {
            // default createdAt
            sortExp = company.get("createdAt");
        }

        List<Order> orders = new ArrayList<>();
        if (asc) {
            orders.add(cb.asc(sortExp));
        } else {
            orders.add(cb.desc(sortExp));
        }

        // tie-breaker
        orders.add(cb.desc(company.get("id")));
        return orders;
    }

    // -----------------------------
    // 엑셀 정렬 (fetch join 환경에서 최대한 맞춤)
    // -----------------------------
    private List<Order> buildExcelOrderBy(CriteriaBuilder cb, Root<Company> company, String sortField, String sortDir) {
        boolean asc = "asc".equalsIgnoreCase(sortDir);

        Expression<?> sortExp;
        if ("companyName".equalsIgnoreCase(sortField)) {
            sortExp = company.get("companyName");
        } else if ("createdAt".equalsIgnoreCase(sortField)) {
            sortExp = company.get("createdAt");
        } else {
            // representative/memberCount는 fetch join 때문에 안정적 집계 정렬이 어렵습니다.
            // 운영에서 “excel도 완전 동일 정렬”이 반드시 필요하면, 별도 강화안 드리겠습니다.
            sortExp = company.get("createdAt");
        }

        List<Order> orders = new ArrayList<>();
        orders.add(asc ? cb.asc(sortExp) : cb.desc(sortExp));
        orders.add(cb.desc(company.get("id")));
        return orders;
    }
}