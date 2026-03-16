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
}