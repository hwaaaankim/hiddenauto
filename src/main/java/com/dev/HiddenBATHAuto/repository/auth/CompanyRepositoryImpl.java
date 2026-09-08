package com.dev.HiddenBATHAuto.repository.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    public Page<CompanyListRowDto> searchCompanyList(String keyword, String searchType,
                                                     List<String> provinceAliases, String cityName, String districtName,
                                                     String sortField, String sortDir, Pageable pageable) {
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
                company.get("businessNumber"),
                representativeExp,
                company.get("createdAt"),
                salesManager.get("name"),
                memberCountExp
        ));

        // where
        List<Predicate> predicates = buildSearchPredicates(
                cb, cq, company, keyword, searchType, provinceAliases, cityName, districtName
        );
        if (!predicates.isEmpty()) {
            cq.where(cb.and(predicates.toArray(new Predicate[0])));
        }

        // group by (집계 사용)
        cq.groupBy(
                company.get("id"),
                company.get("companyName"),
                company.get("businessNumber"),
                company.get("createdAt"),
                salesManager.get("name")
        );

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

        List<Predicate> countPredicates = buildSearchPredicates(
                cb, countCq, countRoot, keyword, searchType, provinceAliases, cityName, districtName
        );
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

        List<Predicate> predicates = buildSearchPredicates(
                cb, cq, company, keyword, searchType, List.of(), null, null
        );
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
                                                  String keyword, String searchType,
                                                  List<String> provinceAliases, String cityName,
                                                  String districtName) {
        List<Predicate> predicates = new ArrayList<>();

        if (StringUtils.hasText(keyword)) {
            String like = "%" + escapeLikePattern(keyword.toLowerCase(Locale.ROOT)) + "%";

            if ("member".equalsIgnoreCase(searchType)
                    || "username".equalsIgnoreCase(searchType)
                    || "phone".equalsIgnoreCase(searchType)) {
                Subquery<Long> sq = cq.subquery(Long.class);
                Root<Member> m = sq.from(Member.class);
                sq.select(cb.literal(1L));

                Predicate memberSearchPredicate;
                if ("username".equalsIgnoreCase(searchType)) {
                    memberSearchPredicate = cb.like(
                            cb.lower(cb.coalesce(m.<String>get("username"), "")),
                            like,
                            '\\'
                    );
                } else if ("phone".equalsIgnoreCase(searchType)) {
                    String phoneLike = "%"
                            + escapeLikePattern(normalizeLooseNumberSearchKeyword(keyword))
                            + "%";
                    memberSearchPredicate = cb.or(
                            cb.like(normalizeLooseNumberExpression(cb, m.<String>get("phone")), phoneLike, '\\'),
                            cb.like(normalizeLooseNumberExpression(cb, m.<String>get("telephone")), phoneLike, '\\')
                    );
                } else {
                    memberSearchPredicate = cb.like(
                            cb.lower(cb.coalesce(m.<String>get("name"), "")),
                            like,
                            '\\'
                    );
                }

                sq.where(
                        cb.equal(m.get("company"), company),
                        memberSearchPredicate
                );
                predicates.add(cb.exists(sq));
            } else if ("businessnumber".equalsIgnoreCase(searchType)) {
                String businessNumberLike = "%"
                        + escapeLikePattern(normalizeLooseNumberSearchKeyword(keyword))
                        + "%";
                predicates.add(cb.like(
                        normalizeLooseNumberExpression(cb, company.<String>get("businessNumber")),
                        businessNumberLike,
                        '\\'
                ));
            } else {
                predicates.add(cb.like(
                        cb.lower(cb.coalesce(company.<String>get("companyName"), "")),
                        like,
                        '\\'
                ));
            }
        }

        if (provinceAliases != null && !provinceAliases.isEmpty()) {
            predicates.add(normalizeRegionExpression(cb, company.<String>get("doName")).in(provinceAliases));
        }

        if (StringUtils.hasText(cityName)) {
            Predicate currentStructure = cb.equal(
                    normalizeRegionExpression(cb, company.<String>get("siName")),
                    normalizeRegionSearchValue(cityName)
            );

            if (!StringUtils.hasText(districtName)) {
                Predicate legacyStructure = cb.and(
                        cb.equal(normalizeRegionExpression(cb, company.<String>get("siName")), ""),
                        cb.equal(
                                normalizeRegionExpression(cb, company.<String>get("guName")),
                                normalizeRegionSearchValue(cityName)
                        )
                );
                predicates.add(cb.or(currentStructure, legacyStructure));
            } else {
                predicates.add(currentStructure);
            }
        }

        if (StringUtils.hasText(districtName)) {
            predicates.add(cb.equal(
                    normalizeRegionExpression(cb, company.<String>get("guName")),
                    normalizeRegionSearchValue(districtName)
            ));
        }

        return predicates;
    }

    private Expression<String> normalizeRegionExpression(CriteriaBuilder cb, Expression<String> expression) {
        return cb.lower(cb.function(
                "replace",
                String.class,
                cb.trim(cb.coalesce(expression, "")),
                cb.literal(" "),
                cb.literal("")
        ));
    }

    private String normalizeRegionSearchValue(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private Expression<String> normalizeLooseNumberExpression(CriteriaBuilder cb, Expression<String> expression) {
        Expression<String> normalized = cb.coalesce(expression, "");
        for (String token : List.of("-", " ", "(", ")", ".", "/", "+")) {
            normalized = cb.function(
                    "replace",
                    String.class,
                    normalized,
                    cb.literal(token),
                    cb.literal("")
            );
        }
        return normalized;
    }

    private String normalizeLooseNumberSearchKeyword(String value) {
        if (value == null) {
            return "";
        }
        String digits = value.replaceAll("\\D", "");
        return digits.isBlank() ? value.trim().toLowerCase(Locale.ROOT) : digits;
    }

    private String escapeLikePattern(String value) {
        return value == null
                ? ""
                : value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
