package com.dev.HiddenBATHAuto.service.amount;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
                    if (!StringUtils.hasText(value)) {
                        continue;
                    }
                    if ("standard".equals(field)) {
                        Boolean parsed = parseStandardFilter(value);
                        if (parsed != null) {
                            predicates.add(cb.equal(root.get(field), parsed));
                        }
                        continue;
                    }
                    if ("mirrorCuttingProduct".equals(field)) {
                        Boolean parsed = parseMirrorFilter(value);
                        if (parsed != null) {
                            predicates.add(cb.equal(root.get(field), parsed));
                        }
                        continue;
                    }
                    predicates.add(cb.like(cb.lower(root.get(field).as(String.class)), "%" + value.trim().toLowerCase(Locale.ROOT) + "%"));
                }
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void setFieldAndRefreshSearchText(Object entity, String field, String value, List<AmountExcelColumnDto> columns) {
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        Object normalizedValue = normalizeValueForField(wrapper, field, value);
        wrapper.setPropertyValue(field, normalizedValue);
        refreshSearchText(wrapper, columns);
    }

    private Object normalizeValueForField(BeanWrapper wrapper, String field, String value) {
        String safeValue = value == null ? "" : value.trim();
        Class<?> propertyType = wrapper.getPropertyType(field);
        if (propertyType == Boolean.class || propertyType == boolean.class) {
            if ("standard".equals(field)) {
                Boolean parsed = parseStandardFilter(safeValue);
                return parsed != null ? parsed : Boolean.TRUE;
            }
            if ("mirrorCuttingProduct".equals(field)) {
                Boolean parsed = parseMirrorFilter(safeValue);
                return parsed != null ? parsed : Boolean.FALSE;
            }
            return Boolean.parseBoolean(safeValue);
        }
        return safeValue;
    }

    private void refreshSearchText(BeanWrapper wrapper, List<AmountExcelColumnDto> columns) {
        List<String> parts = new ArrayList<>();
        for (AmountExcelColumnDto col : columns) {
            Object v = wrapper.getPropertyValue(col.field());
            String text = displayValue(col.field(), v);
            if (StringUtils.hasText(text)) {
                parts.add(text);
            }
        }
        wrapper.setPropertyValue("searchText", String.join(" ", parts));
    }

    private Map<String, Object> toMap(Object entity, List<AmountExcelColumnDto> columns) {
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(entity);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", wrapper.getPropertyValue("id"));
        for (AmountExcelColumnDto column : columns) {
            Object value = wrapper.getPropertyValue(column.field());
            map.put(column.field(), displayValue(column.field(), value));
        }
        return map;
    }

    private String displayValue(String field, Object value) {
        if (value == null) {
            return "";
        }
        if ("standard".equals(field)) {
            return Boolean.TRUE.equals(value) ? "규격" : "비규격";
        }
        if ("mirrorCuttingProduct".equals(field)) {
            return Boolean.TRUE.equals(value) ? "재단필요" : "";
        }
        return String.valueOf(value);
    }

    private Boolean parseStandardFilter(String value) {
        String compact = AmountTextNormalizer.compact(value);
        if (!StringUtils.hasText(compact)) {
            return null;
        }
        if (compact.contains("비규격") || compact.equals("false") || compact.equals("n") || compact.equals("no") || compact.equals("0")) {
            return Boolean.FALSE;
        }
        if (compact.contains("규격") || compact.equals("true") || compact.equals("y") || compact.equals("yes") || compact.equals("1") || compact.equals("o") || compact.equals("ㅇ")) {
            return Boolean.TRUE;
        }
        return null;
    }

    private Boolean parseMirrorFilter(String value) {
        String compact = AmountTextNormalizer.compact(value);
        if (!StringUtils.hasText(compact)) {
            return null;
        }
        if (compact.equals("x") || compact.equals("false") || compact.equals("n") || compact.equals("no") || compact.equals("0")
                || compact.contains("불필요") || compact.contains("필요없") || compact.contains("없음")) {
            return Boolean.FALSE;
        }
        if (compact.equals("ㅇ") || compact.equals("o") || compact.equals("y") || compact.equals("yes") || compact.equals("true")
                || compact.equals("1") || compact.contains("재단") || compact.contains("필요")) {
            return Boolean.TRUE;
        }
        return null;
    }
}
