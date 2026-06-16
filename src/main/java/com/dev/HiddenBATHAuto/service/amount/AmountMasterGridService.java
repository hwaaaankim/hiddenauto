package com.dev.HiddenBATHAuto.service.amount;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.dto.amount.AmountCellUpdateRequest;
import com.dev.HiddenBATHAuto.dto.amount.AmountExcelColumnDto;
import com.dev.HiddenBATHAuto.dto.amount.AmountGridResponse;
import com.dev.HiddenBATHAuto.model.amount.AmountCustomerMaster;
import com.dev.HiddenBATHAuto.model.amount.AmountItemMaster;
import com.dev.HiddenBATHAuto.repository.amount.AmountCustomerMasterRepository;
import com.dev.HiddenBATHAuto.repository.amount.AmountItemMasterRepository;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AmountMasterGridService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final AmountItemMasterRepository itemRepository;
    private final AmountCustomerMasterRepository customerRepository;

    @Transactional(readOnly = true)
    public AmountGridResponse listItems(int offset, Integer limit, String sortField, String sortDir, Map<String, String> params) {
        int resolvedLimit = normalizeLimit(limit);
        Pageable pageable = buildPageable(offset, resolvedLimit, sortField, sortDir, AmountExcelColumnDefinition.ITEM_FIELD_SET);
        Specification<AmountItemMaster> spec = buildSpec(params, AmountExcelColumnDefinition.ITEM_FIELD_SET);
        Page<AmountItemMaster> page = itemRepository.findAll(spec, pageable);
        return new AmountGridResponse(
                AmountExcelColumnDefinition.ITEM_COLUMNS,
                page.getContent().stream().map(row -> toMap(row, AmountExcelColumnDefinition.ITEM_COLUMNS)).toList(),
                page.getTotalElements(),
                offset,
                resolvedLimit,
                offset + page.getNumberOfElements() < page.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public AmountGridResponse listCustomers(int offset, Integer limit, String sortField, String sortDir, Map<String, String> params) {
        int resolvedLimit = normalizeLimit(limit);
        Pageable pageable = buildPageable(offset, resolvedLimit, sortField, sortDir, AmountExcelColumnDefinition.CUSTOMER_FIELD_SET);
        Specification<AmountCustomerMaster> spec = buildSpec(params, AmountExcelColumnDefinition.CUSTOMER_FIELD_SET);
        Page<AmountCustomerMaster> page = customerRepository.findAll(spec, pageable);
        return new AmountGridResponse(
                AmountExcelColumnDefinition.CUSTOMER_COLUMNS,
                page.getContent().stream().map(row -> toMap(row, AmountExcelColumnDefinition.CUSTOMER_COLUMNS)).toList(),
                page.getTotalElements(),
                offset,
                resolvedLimit,
                offset + page.getNumberOfElements() < page.getTotalElements()
        );
    }

    @Transactional
    public Map<String, Object> updateItem(Long id, AmountCellUpdateRequest request) {
        if (request == null || !AmountExcelColumnDefinition.isAllowedItemField(request.field())) {
            throw new IllegalArgumentException("수정할 수 없는 품목 컬럼입니다.");
        }
        AmountItemMaster item = itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("품목 데이터를 찾을 수 없습니다. id=" + id));
        setFieldAndRefreshSearchText(item, request.field(), request.value(), AmountExcelColumnDefinition.ITEM_COLUMNS);
        return toMap(item, AmountExcelColumnDefinition.ITEM_COLUMNS);
    }

    @Transactional
    public Map<String, Object> updateCustomer(Long id, AmountCellUpdateRequest request) {
        if (request == null || !AmountExcelColumnDefinition.isAllowedCustomerField(request.field())) {
            throw new IllegalArgumentException("수정할 수 없는 거래처 컬럼입니다.");
        }
        AmountCustomerMaster customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("거래처 데이터를 찾을 수 없습니다. id=" + id));
        setFieldAndRefreshSearchText(customer, request.field(), request.value(), AmountExcelColumnDefinition.CUSTOMER_COLUMNS);
        return toMap(customer, AmountExcelColumnDefinition.CUSTOMER_COLUMNS);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Pageable buildPageable(int offset, int limit, String sortField, String sortDir, Set<String> allowedFields) {
        int safeOffset = Math.max(offset, 0);
        int page = safeOffset / limit;

        // 첫 화면 진입 시 sortField가 넘어오지 않으면 null입니다.
        // Java 불변 Set(Set.of/List.of 기반)은 contains(null) 호출 시 NPE가 발생할 수 있으므로
        // 반드시 null/blank 검사를 먼저 해야 합니다.
        String property = (StringUtils.hasText(sortField) && allowedFields.contains(sortField))
                ? sortField
                : "id";

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(page, limit, Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id")));
    }

    private <T> Specification<T> buildSpec(Map<String, String> params, Set<String> allowedFields) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (params != null) {
                for (String field : allowedFields) {
                    String value = params.get("f_" + field);
                    if (StringUtils.hasText(value)) {
                        predicates.add(cb.like(cb.lower(root.get(field).as(String.class)), "%" + value.trim().toLowerCase() + "%"));
                    }
                }
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void setFieldAndRefreshSearchText(Object entity, String field, String value, List<AmountExcelColumnDto> columns) {
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        wrapper.setPropertyValue(field, value == null ? "" : value.trim());
        List<String> parts = new ArrayList<>();
        for (AmountExcelColumnDto col : columns) {
            Object v = wrapper.getPropertyValue(col.field());
            if (v != null && StringUtils.hasText(String.valueOf(v))) {
                parts.add(String.valueOf(v));
            }
        }
        wrapper.setPropertyValue("searchText", String.join(" ", parts));
    }

    private Map<String, Object> toMap(Object entity, List<AmountExcelColumnDto> columns) {
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", wrapper.getPropertyValue("id"));
        for (AmountExcelColumnDto column : columns) {
            map.put(column.field(), wrapper.getPropertyValue(column.field()));
        }
        return map;
    }
}
