package com.dev.HiddenBATHAuto.repository.as;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

public class AsTaskRepositoryImpl implements AsTaskRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<AsTask> findByRequestedDateFlexible(Long handlerId, AsStatus status,
            LocalDateTime start, LocalDateTime end,
            String companyKeyword, List<String> provinceNames, String cityName, String districtName,
            String visitTimeSort, String scheduledDateSort, Pageable pageable) {

        StringBuilder select = new StringBuilder();
        select.append("select t ")
              .append("from AsTask t ")
              .append("left join t.requestedBy rb ")
              .append("left join rb.company company ")
              .append("left join AsTaskSchedule s on s.asTask = t ")
              .append("where t.assignedHandler.id = :handlerId ");

        Map<String, Object> params = new HashMap<>();
        params.put("handlerId", handlerId);

        appendCommonFilters(select, params, status, companyKeyword, provinceNames, cityName, districtName);

        if (start != null) {
            select.append(" and t.requestedAt >= :start ");
            params.put("start", start);
        }

        if (end != null) {
            select.append(" and t.requestedAt < :end ");
            params.put("end", end);
        }

        select.append(buildRequestedOrProcessedOrderBy("t.requestedAt", visitTimeSort, scheduledDateSort));

        TypedQuery<AsTask> contentQuery = em.createQuery(select.toString(), AsTask.class);
        bindParams(contentQuery, params);
        contentQuery.setFirstResult((int) pageable.getOffset());
        contentQuery.setMaxResults(pageable.getPageSize());

        List<AsTask> content = contentQuery.getResultList();

        StringBuilder count = new StringBuilder();
        count.append("select count(t) ")
             .append("from AsTask t ")
             .append("left join t.requestedBy rb ")
             .append("left join rb.company company ")
             .append("where t.assignedHandler.id = :handlerId ");

        appendCommonFilters(count, params, status, companyKeyword, provinceNames, cityName, districtName);

        if (start != null) {
            count.append(" and t.requestedAt >= :start ");
        }

        if (end != null) {
            count.append(" and t.requestedAt < :end ");
        }

        TypedQuery<Long> countQuery = em.createQuery(count.toString(), Long.class);
        bindParams(countQuery, params);

        long total = countQuery.getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<AsTask> findByProcessedDateFlexible(Long handlerId, AsStatus status,
            LocalDateTime start, LocalDateTime end,
            String companyKeyword, List<String> provinceNames, String cityName, String districtName,
            String visitTimeSort, String scheduledDateSort, Pageable pageable) {

        StringBuilder select = new StringBuilder();
        select.append("select t ")
              .append("from AsTask t ")
              .append("left join t.requestedBy rb ")
              .append("left join rb.company company ")
              .append("left join AsTaskSchedule s on s.asTask = t ")
              .append("where t.assignedHandler.id = :handlerId ");

        Map<String, Object> params = new HashMap<>();
        params.put("handlerId", handlerId);

        appendCommonFilters(select, params, status, companyKeyword, provinceNames, cityName, districtName);

        if (start != null) {
            select.append(" and t.asProcessDate >= :start ");
            params.put("start", start);
        }

        if (end != null) {
            select.append(" and t.asProcessDate < :end ");
            params.put("end", end);
        }

        select.append(buildRequestedOrProcessedOrderBy("t.asProcessDate", visitTimeSort, scheduledDateSort));

        TypedQuery<AsTask> contentQuery = em.createQuery(select.toString(), AsTask.class);
        bindParams(contentQuery, params);
        contentQuery.setFirstResult((int) pageable.getOffset());
        contentQuery.setMaxResults(pageable.getPageSize());

        List<AsTask> content = contentQuery.getResultList();

        StringBuilder count = new StringBuilder();
        count.append("select count(t) ")
             .append("from AsTask t ")
             .append("left join t.requestedBy rb ")
             .append("left join rb.company company ")
             .append("where t.assignedHandler.id = :handlerId ");

        appendCommonFilters(count, params, status, companyKeyword, provinceNames, cityName, districtName);

        if (start != null) {
            count.append(" and t.asProcessDate >= :start ");
        }

        if (end != null) {
            count.append(" and t.asProcessDate < :end ");
        }

        TypedQuery<Long> countQuery = em.createQuery(count.toString(), Long.class);
        bindParams(countQuery, params);

        long total = countQuery.getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<AsTask> findByScheduledDateFlexible(Long handlerId, AsStatus status,
            LocalDate startDate, LocalDate endDate,
            String companyKeyword, List<String> provinceNames, String cityName, String districtName,
            String visitTimeSort, String scheduledDateSort, Pageable pageable) {

        StringBuilder select = new StringBuilder();
        select.append("select t ")
              .append("from AsTask t ")
              .append("left join t.requestedBy rb ")
              .append("left join rb.company company ")
              .append("join AsTaskSchedule s on s.asTask = t ")
              .append("where t.assignedHandler.id = :handlerId ");

        Map<String, Object> params = new HashMap<>();
        params.put("handlerId", handlerId);

        appendCommonFilters(select, params, status, companyKeyword, provinceNames, cityName, districtName);

        if (startDate != null) {
            select.append(" and s.scheduledDate >= :startDate ");
            params.put("startDate", startDate);
        }

        if (endDate != null) {
            select.append(" and s.scheduledDate <= :endDate ");
            params.put("endDate", endDate);
        }

        select.append(buildScheduledOrderBy(visitTimeSort, scheduledDateSort));

        TypedQuery<AsTask> contentQuery = em.createQuery(select.toString(), AsTask.class);
        bindParams(contentQuery, params);
        contentQuery.setFirstResult((int) pageable.getOffset());
        contentQuery.setMaxResults(pageable.getPageSize());

        List<AsTask> content = contentQuery.getResultList();

        StringBuilder count = new StringBuilder();
        count.append("select count(t) ")
             .append("from AsTask t ")
             .append("left join t.requestedBy rb ")
             .append("left join rb.company company ")
             .append("join AsTaskSchedule s on s.asTask = t ")
             .append("where t.assignedHandler.id = :handlerId ");

        appendCommonFilters(count, params, status, companyKeyword, provinceNames, cityName, districtName);

        if (startDate != null) {
            count.append(" and s.scheduledDate >= :startDate ");
        }

        if (endDate != null) {
            count.append(" and s.scheduledDate <= :endDate ");
        }

        TypedQuery<Long> countQuery = em.createQuery(count.toString(), Long.class);
        bindParams(countQuery, params);

        long total = countQuery.getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    private void appendCommonFilters(StringBuilder jpql, Map<String, Object> params,
            AsStatus status, String companyKeyword, List<String> provinceNames, String cityName, String districtName) {

        if (status != null) {
            jpql.append(" and t.status = :status ");
            params.put("status", status);
        }

        if (StringUtils.hasText(companyKeyword)) {
            jpql.append(" and lower(company.companyName) like :companyKeyword ");
            params.put("companyKeyword", "%" + companyKeyword.trim().toLowerCase() + "%");
        }

        if (provinceNames != null && !provinceNames.isEmpty()) {
            jpql.append(" and t.doName in :provinceNames ");
            params.put("provinceNames", provinceNames);
        }

        if (StringUtils.hasText(cityName)) {
            jpql.append(" and t.siName = :cityName ");
            params.put("cityName", cityName);
        }

        if (StringUtils.hasText(districtName)) {
            jpql.append(" and t.guName = :districtName ");
            params.put("districtName", districtName);
        }
    }

    private String buildRequestedOrProcessedOrderBy(String defaultDateField,
            String visitTimeSort, String scheduledDateSort) {

        List<String> orders = new ArrayList<>();

        boolean hasScheduledSort = "asc".equalsIgnoreCase(scheduledDateSort) || "desc".equalsIgnoreCase(scheduledDateSort);
        boolean hasVisitTimeSort = "asc".equalsIgnoreCase(visitTimeSort) || "desc".equalsIgnoreCase(visitTimeSort);

        if (hasScheduledSort) {
            orders.add("case when s.scheduledDate is null then 1 else 0 end asc");

            if ("asc".equalsIgnoreCase(scheduledDateSort)) {
                orders.add("s.scheduledDate asc");
                orders.add("s.orderIndex asc");
            } else {
                orders.add("s.scheduledDate desc");
                orders.add("s.orderIndex desc");
            }
        }

        if (hasVisitTimeSort) {
            orders.add("case when t.visitPlannedTime is null then 1 else 0 end asc");

            if ("asc".equalsIgnoreCase(visitTimeSort)) {
                orders.add("t.visitPlannedTime asc");
            } else {
                orders.add("t.visitPlannedTime desc");
            }
        }

        if (!hasScheduledSort && !hasVisitTimeSort) {
            orders.add(defaultDateField + " desc");
        } else {
            orders.add(defaultDateField + " desc");
        }

        orders.add("t.id desc");

        return " order by " + String.join(", ", orders);
    }

    private String buildScheduledOrderBy(String visitTimeSort, String scheduledDateSort) {

        List<String> orders = new ArrayList<>();

        boolean hasScheduledSort = "asc".equalsIgnoreCase(scheduledDateSort) || "desc".equalsIgnoreCase(scheduledDateSort);
        boolean hasVisitTimeSort = "asc".equalsIgnoreCase(visitTimeSort) || "desc".equalsIgnoreCase(visitTimeSort);

        if (hasScheduledSort) {
            if ("asc".equalsIgnoreCase(scheduledDateSort)) {
                orders.add("s.scheduledDate asc");
                orders.add("s.orderIndex asc");
            } else {
                orders.add("s.scheduledDate desc");
                orders.add("s.orderIndex desc");
            }

            if (hasVisitTimeSort) {
                orders.add("case when t.visitPlannedTime is null then 1 else 0 end asc");

                if ("asc".equalsIgnoreCase(visitTimeSort)) {
                    orders.add("t.visitPlannedTime asc");
                } else {
                    orders.add("t.visitPlannedTime desc");
                }
            }
        } else if (hasVisitTimeSort) {
            orders.add("case when t.visitPlannedTime is null then 1 else 0 end asc");

            if ("asc".equalsIgnoreCase(visitTimeSort)) {
                orders.add("t.visitPlannedTime asc");
            } else {
                orders.add("t.visitPlannedTime desc");
            }

            // 방문예정일 필터 조회에서는 동일 방문예정일 내 순서를 안정적으로 유지
            orders.add("s.scheduledDate asc");
            orders.add("s.orderIndex asc");
        } else {
            // 기본값: 방문예정일 순
            orders.add("s.scheduledDate asc");
            orders.add("s.orderIndex asc");
        }

        orders.add("t.id desc");

        return " order by " + String.join(", ", orders);
    }

    private void bindParams(Query query, Map<String, Object> params) {
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
    }


    @Override
    public Page<AsTask> findAdminAsPage(Long handlerId, AsStatus status, String dateType,
            LocalDateTime start, LocalDateTime end, Long asIdFrom, Long asIdTo,
            String priceFilter, Boolean paymentCollected, String keywordType, String keyword,
            String sortState, Pageable pageable) {
        String from = """
FROM AsTask a
	        LEFT JOIN a.requestedBy rb
	        LEFT JOIN rb.company c
	        LEFT JOIN a.assignedHandler ah
	        LEFT JOIN AsTaskSchedule s ON s.asTask = a
	        WHERE (:status IS NULL OR a.status = :status)
	          AND (:handlerId IS NULL OR ah.id = :handlerId)
	          AND (:asIdFrom IS NULL OR a.id >= :asIdFrom)
	          AND (:asIdTo IS NULL OR a.id <= :asIdTo)
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
	                        OR LOWER(CONCAT(COALESCE(a.roadAddress, ''), ' ', COALESCE(a.detailAddress, ''))) LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(COALESCE(a.applicantName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.applicantPhone, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                        OR LOWER(FUNCTION('replace', FUNCTION('replace', COALESCE(a.onsiteContact, ''), '-', ''), ' ', ''))
	                           LIKE LOWER(CONCAT('%', FUNCTION('replace', FUNCTION('replace', :keyword, '-', ''), ' ', ''), '%'))
	                    )
	                )
	                OR (:keywordType = 'address' AND LOWER(CONCAT(COALESCE(a.roadAddress, ''), ' ', COALESCE(a.detailAddress, ''))) LIKE LOWER(CONCAT('%', :keyword, '%')))
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
                """;
        String dateField = "processed".equals(dateType) ? "a.asProcessDate" : "a.requestedAt";
        from = from.replace("a.requestedAt", dateField);
        Map<String, Object> params = new HashMap<>();
        params.put("handlerId", handlerId);
        params.put("status", status);
        params.put("startDate", start);
        params.put("endDate", end);
        params.put("asIdFrom", asIdFrom);
        params.put("asIdTo", asIdTo);
        params.put("priceFilter", priceFilter);
        params.put("paymentCollected", paymentCollected);
        params.put("keywordType", keywordType);
        params.put("keyword", keyword);
        List<String> orders = new ArrayList<>();
        Map<String, String> sorts = AdminAsSort.parse(sortState, null, null);
        sorts.forEach((field, direction) -> {
            String expression = AdminAsSort.expression(field);
            boolean text = !List.of("id", "requestedAt", "scheduledDate", "asProcessDate", "status").contains(field);
            orders.add("CASE WHEN " + expression + " IS NULL"
                    + (text ? " OR TRIM(" + expression + ") = ''" : "") + " THEN 1 ELSE 0 END ASC");
            orders.add((text ? "LOWER(" + expression + ")" : expression) + " " + direction);
        });
        if (orders.isEmpty()) {
            orders.add("CASE WHEN " + dateField + " IS NULL THEN 1 ELSE 0 END ASC");
            orders.add(dateField + " DESC");
        }
        if (sorts.containsKey("scheduledDate")) orders.add("s.orderIndex ASC");
        if (!sorts.containsKey("id")) orders.add("a.id DESC");
        TypedQuery<AsTask> content = em.createQuery("SELECT a " + from
                + " ORDER BY " + String.join(", ", orders), AsTask.class);
        var graph = em.createEntityGraph(AsTask.class);
        graph.addAttributeNodes("assignedHandler", "assignedTeam");
        graph.addSubgraph("requestedBy").addAttributeNodes("company");
        content.setHint("jakarta.persistence.fetchgraph", graph);
        bindParams(content, params);
        if (pageable.isPaged()) {
            content.setFirstResult((int) pageable.getOffset());
            content.setMaxResults(pageable.getPageSize());
        }
        List<AsTask> rows = content.getResultList();
        if (pageable.isUnpaged()) return new PageImpl<>(rows);
        TypedQuery<Long> count = em.createQuery("SELECT COUNT(a) " + from, Long.class);
        bindParams(count, params);
        return new PageImpl<>(rows, pageable, count.getSingleResult());
    }

}
