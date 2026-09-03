package com.dev.HiddenBATHAuto.service.productmaster;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.dev.HiddenBATHAuto.enums.productmaster.ProductAttributeRole;
import com.dev.HiddenBATHAuto.enums.productmaster.ProductMasterStatus;
import com.dev.HiddenBATHAuto.model.productmaster.ProductComponent;
import com.dev.HiddenBATHAuto.model.productmaster.ProductMaster;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

final class ProductMasterSpecifications {

    private ProductMasterSpecifications() {
    }

    static Specification<ProductMaster> byCriteria(
            String keyword,
            ProductMasterStatus status,
            String stockStatus,
            List<AttributeFilter> attributeFilters,
            List<NumberAttributeFilter> numberAttributeFilters,
            List<TextAttributeFilter> textAttributeFilters,
            DimensionFilter dimensionFilter
    ) {
        return (root, query, criteriaBuilder) -> {
            java.util.ArrayList<Predicate> predicates = new java.util.ArrayList<>();

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("productName")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("catalogCode")), pattern, '\\'),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("productCode")), pattern, '\\')
                ));
            }

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (stockStatus != null && !stockStatus.isBlank()) {
                switch (stockStatus.trim().toUpperCase(Locale.ROOT)) {
                    case "OUT_OF_STOCK" -> predicates.add(criteriaBuilder.lessThanOrEqualTo(root.<Integer>get("currentStock"), 0));
                    case "LOW_STOCK" -> predicates.add(criteriaBuilder.and(
                            criteriaBuilder.greaterThan(root.<Integer>get("currentStock"), 0),
                            criteriaBuilder.lessThanOrEqualTo(
                                    root.<Integer>get("currentStock"),
                                    root.<Integer>get("safetyStock")
                            )
                    ));
                    case "IN_STOCK" -> predicates.add(criteriaBuilder.greaterThan(
                            root.<Integer>get("currentStock"),
                            root.<Integer>get("safetyStock")
                    ));
                    default -> throw new IllegalArgumentException("지원하지 않는 재고 필터입니다.");
                }
            }

            if (attributeFilters != null) {
                for (AttributeFilter filter : attributeFilters) {
                    Subquery<Integer> subquery = query.subquery(Integer.class);
                    Root<ProductComponent> component = subquery.from(ProductComponent.class);
                    subquery.select(criteriaBuilder.literal(1));
                    subquery.where(
                            criteriaBuilder.equal(component.get("product"), root),
                            criteriaBuilder.equal(component.get("group").get("id"), filter.groupId()),
                            criteriaBuilder.equal(component.get("value").get("id"), filter.valueId())
                    );
                    predicates.add(criteriaBuilder.exists(subquery));
                }
            }

            if (numberAttributeFilters != null) {
                for (NumberAttributeFilter filter : numberAttributeFilters) {
                    Subquery<Integer> subquery = query.subquery(Integer.class);
                    Root<ProductComponent> component = subquery.from(ProductComponent.class);
                    java.util.ArrayList<Predicate> numberPredicates = new java.util.ArrayList<>();
                    numberPredicates.add(criteriaBuilder.equal(component.get("product"), root));
                    numberPredicates.add(criteriaBuilder.equal(component.get("group").get("id"), filter.groupId()));
                    if (filter.minimum() != null) {
                        numberPredicates.add(criteriaBuilder.greaterThanOrEqualTo(
                                component.<BigDecimal>get("numericValue"), filter.minimum()
                        ));
                    }
                    if (filter.maximum() != null) {
                        numberPredicates.add(criteriaBuilder.lessThanOrEqualTo(
                                component.<BigDecimal>get("numericValue"), filter.maximum()
                        ));
                    }
                    subquery.select(criteriaBuilder.literal(1));
                    subquery.where(numberPredicates.toArray(Predicate[]::new));
                    predicates.add(criteriaBuilder.exists(subquery));
                }
            }

            if (textAttributeFilters != null) {
                for (TextAttributeFilter filter : textAttributeFilters) {
                    Subquery<Integer> subquery = query.subquery(Integer.class);
                    Root<ProductComponent> component = subquery.from(ProductComponent.class);
                    String pattern = "%" + escapeLike(filter.keyword().toLowerCase(Locale.ROOT)) + "%";
                    subquery.select(criteriaBuilder.literal(1));
                    subquery.where(
                            criteriaBuilder.equal(component.get("product"), root),
                            criteriaBuilder.equal(component.get("group").get("id"), filter.groupId()),
                            criteriaBuilder.like(criteriaBuilder.lower(component.<String>get("textValue")), pattern, '\\')
                    );
                    predicates.add(criteriaBuilder.exists(subquery));
                }
            }

            if (dimensionFilter != null && dimensionFilter.hasAny()) {
                Subquery<Integer> subquery = query.subquery(Integer.class);
                Root<ProductComponent> component = subquery.from(ProductComponent.class);
                java.util.ArrayList<Predicate> dimensionPredicates = new java.util.ArrayList<>();
                dimensionPredicates.add(criteriaBuilder.equal(component.get("product"), root));
                dimensionPredicates.add(criteriaBuilder.equal(
                        component.get("group").get("systemRole"),
                        ProductAttributeRole.SIZE
                ));
                addMinimum(criteriaBuilder, dimensionPredicates, component, "widthMm", dimensionFilter.widthMin());
                addMaximum(criteriaBuilder, dimensionPredicates, component, "widthMm", dimensionFilter.widthMax());
                addMinimum(criteriaBuilder, dimensionPredicates, component, "depthMm", dimensionFilter.depthMin());
                addMaximum(criteriaBuilder, dimensionPredicates, component, "depthMm", dimensionFilter.depthMax());
                addMinimum(criteriaBuilder, dimensionPredicates, component, "heightMm", dimensionFilter.heightMin());
                addMaximum(criteriaBuilder, dimensionPredicates, component, "heightMm", dimensionFilter.heightMax());
                subquery.select(criteriaBuilder.literal(1));
                subquery.where(dimensionPredicates.toArray(Predicate[]::new));
                predicates.add(criteriaBuilder.exists(subquery));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addMinimum(
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            List<Predicate> predicates,
            Root<ProductComponent> component,
            String field,
            Integer value
    ) {
        if (value != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(component.<Integer>get(field), value));
        }
    }

    private static void addMaximum(
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            List<Predicate> predicates,
            Root<ProductComponent> component,
            String field,
            Integer value
    ) {
        if (value != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(component.<Integer>get(field), value));
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    record AttributeFilter(Long groupId, Long valueId) {
    }

    record NumberAttributeFilter(Long groupId, BigDecimal minimum, BigDecimal maximum) {
    }

    record TextAttributeFilter(Long groupId, String keyword) {
    }

    record DimensionFilter(
            Integer widthMin,
            Integer widthMax,
            Integer depthMin,
            Integer depthMax,
            Integer heightMin,
            Integer heightMax
    ) {
        boolean hasAny() {
            return widthMin != null || widthMax != null
                    || depthMin != null || depthMax != null
                    || heightMin != null || heightMax != null;
        }
    }
}
