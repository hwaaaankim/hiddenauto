package com.dev.HiddenBATHAuto.service.delivery;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.delivery.DeliveryManagerRowDto;
import com.dev.HiddenBATHAuto.dto.delivery.DeliveryManagerSearchCondition;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryManagerService {

    private static final String DELIVERY_TEAM_NAME = "배송팀";

    private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;
    private final DeliveryMethodRepository deliveryMethodRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    public Page<DeliveryManagerRowDto> getMyDeliveryManagerPage(
            Member loginMember,
            DeliveryManagerSearchCondition condition
    ) {
        validateDeliveryTeamMember(loginMember);

        DeliveryManagerSearchCondition safeCondition = normalizeCondition(condition);

        LocalDateTime fromDateTime = safeCondition.getFromDate() != null
                ? safeCondition.getFromDate().atStartOfDay()
                : null;

        LocalDateTime toDateTime = safeCondition.getToDate() != null
                ? safeCondition.getToDate().plusDays(1).atStartOfDay()
                : null;

        List<OrderStatus> statuses = List.of(
                OrderStatus.CONFIRMED,
                OrderStatus.PRODUCTION_DONE,
                OrderStatus.DISPATCH_DONE,
                OrderStatus.DELIVERY_DONE
        );

        List<DeliveryOrderIndex> indexes = deliveryOrderIndexRepository.findDeliveryManagerBaseRows(
                loginMember.getId(),
                statuses,
                fromDateTime,
                toDateTime
        );

        RepresentativeLookup representativeLookup = buildRepresentativeLookup(indexes);

        List<DeliveryManagerRowDto> rows = indexes.stream()
                .filter(index -> index != null && index.getOrder() != null)
                .map(index -> toRowDto(index, representativeLookup))
                .filter(row -> matchesDeliveryMethod(row, safeCondition.getDeliveryMethodId()))
                .filter(row -> matchesSearch(row, safeCondition.getSearchType(), safeCondition.getKeyword()))
                .collect(Collectors.toCollection(ArrayList::new));

        sortRows(rows, safeCondition.getSortKey(), safeCondition.getSortDir());

        int total = rows.size();
        int page = safeCondition.getPage();
        int size = safeCondition.getSize();

        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);

        List<DeliveryManagerRowDto> pageContent = rows.subList(fromIndex, toIndex);

        return new PageImpl<>(
                pageContent,
                PageRequest.of(page, size),
                total
        );
    }

    public List<DeliveryMethod> getDeliveryMethodsForFilter() {
        return deliveryMethodRepository.findAllByOrderByMethodNameAsc();
    }

    private DeliveryManagerSearchCondition normalizeCondition(DeliveryManagerSearchCondition condition) {
        int page = condition != null ? condition.getPage() : 0;
        int size = condition != null ? condition.getSize() : 10;

        if (page < 0) {
            page = 0;
        }

        if (size != 10 && size != 30 && size != 50 && size != 70 && size != 100) {
            size = 10;
        }

        String searchType = safeText(condition != null ? condition.getSearchType() : null);
        String keyword = safeText(condition != null ? condition.getKeyword() : null);

        String sortKey = safeText(condition != null ? condition.getSortKey() : null);
        String sortDir = safeText(condition != null ? condition.getSortDir() : null).toUpperCase(Locale.ROOT);

        if (sortKey.isBlank()) {
            sortKey = "orderIndex";
        }

        if (!"ASC".equals(sortDir) && !"DESC".equals(sortDir)) {
            sortDir = "ASC";
        }

        return DeliveryManagerSearchCondition.builder()
                .page(page)
                .size(size)
                .searchType(searchType)
                .keyword(keyword)
                .deliveryMethodId(condition != null ? condition.getDeliveryMethodId() : null)
                .fromDate(condition != null ? condition.getFromDate() : null)
                .toDate(condition != null ? condition.getToDate() : null)
                .sortKey(sortKey)
                .sortDir(sortDir)
                .build();
    }

    private RepresentativeLookup buildRepresentativeLookup(List<DeliveryOrderIndex> indexes) {
        if (indexes == null || indexes.isEmpty()) {
            return new RepresentativeLookup(Map.of(), Map.of());
        }

        List<Long> companyIds = indexes.stream()
                .map(DeliveryOrderIndex::getOrder)
                .filter(Objects::nonNull)
                .map(this::resolveCompany)
                .filter(Objects::nonNull)
                .map(Company::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (companyIds.isEmpty()) {
            return new RepresentativeLookup(Map.of(), Map.of());
        }

        List<Member> representatives = memberRepository.findByCompany_IdInAndRoleAndEnabledTrueOrderByNameAsc(
                companyIds,
                MemberRole.CUSTOMER_REPRESENTATIVE
        );

        Map<Long, List<String>> nameMap = new LinkedHashMap<>();
        Map<Long, List<String>> phoneMap = new LinkedHashMap<>();

        for (Member member : representatives) {
            if (member == null || member.getCompany() == null || member.getCompany().getId() == null) {
                continue;
            }

            Long companyId = member.getCompany().getId();

            String name = safeText(member.getName());
            String phone = safeText(member.getPhone());

            if (!name.isBlank()) {
                nameMap.computeIfAbsent(companyId, key -> new ArrayList<>()).add(name);
            }

            if (!phone.isBlank()) {
                phoneMap.computeIfAbsent(companyId, key -> new ArrayList<>()).add(phone);
            }
        }

        Map<Long, String> joinedNameMap = joinMapValues(nameMap);
        Map<Long, String> joinedPhoneMap = joinMapValues(phoneMap);

        return new RepresentativeLookup(joinedNameMap, joinedPhoneMap);
    }

    private Map<Long, String> joinMapValues(Map<Long, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> result = new LinkedHashMap<>();

        for (Map.Entry<Long, List<String>> entry : source.entrySet()) {
            List<String> values = entry.getValue() != null ? entry.getValue() : List.of();

            String joined = values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .collect(Collectors.joining(", "));

            result.put(entry.getKey(), joined);
        }

        return result;
    }

    private DeliveryManagerRowDto toRowDto(
            DeliveryOrderIndex index,
            RepresentativeLookup representativeLookup
    ) {
        Order order = index.getOrder();
        OrderItem item = order.getOrderItem();

        Map<String, Object> optionMap = parseOptionJson(item != null ? item.getOptionJson() : null);

        String productName = firstNonBlank(
                pickFirstValue(optionMap, List.of("제품", "제품명", "product", "productName", "Product", "ProductName")),
                item != null ? item.getProductName() : null
        );

        String size = firstNonBlank(
                pickFirstValue(optionMap, List.of("사이즈", "규격", "size", "Size", "제품사이즈", "productSize", "ProductSize")),
                "-"
        );

        String category = firstNonBlank(
                pickFirstValue(optionMap, List.of("카테고리", "category", "Category")),
                order.getProductCategory() != null ? order.getProductCategory().getName() : null
        );

        Company company = resolveCompany(order);
        Long companyId = company != null ? company.getId() : null;

        String companyName = company != null ? company.getCompanyName() : "-";

        String representativeName = companyId != null
                ? representativeLookup.nameMap().getOrDefault(companyId, "")
                : "";

        String representativePhone = companyId != null
                ? representativeLookup.phoneMap().getOrDefault(companyId, "")
                : "";

        DeliveryMethod deliveryMethod = order.getDeliveryMethod();

        Long deliveryMethodId = deliveryMethod != null ? deliveryMethod.getId() : null;
        String deliveryMethodName = deliveryMethod != null ? deliveryMethod.getMethodName() : "-";

        OrderStatus status = order.getStatus();

        return DeliveryManagerRowDto.builder()
                .orderId(order.getId())
                .taskId(order.getTask() != null ? order.getTask().getId() : null)
                .companyName(valueOrDash(companyName))
                .representativeName(valueOrDash(representativeName))
                .representativePhone(valueOrDash(representativePhone))
                .ordererName(valueOrDash(order.getOrdererName()))
                .ordererPhone(valueOrDash(order.getOrdererPhone()))
                .productName(valueOrDash(productName))
                .productSize(valueOrDash(size))
                .quantity(order.getQuantity())
                .deliveryMethodId(deliveryMethodId)
                .deliveryMethodName(valueOrDash(deliveryMethodName))
                .deliveryAddress(valueOrDash(buildDisplayAddress(order)))
                .preferredDeliveryDate(order.getPreferredDeliveryDate())
                .categoryName(valueOrDash(category))
                .statusName(status != null ? status.name() : "")
                .statusLabel(status != null ? status.getLabel() : "-")
                .orderIndex(index.getOrderIndex())
                .build();
    }

    private boolean matchesDeliveryMethod(DeliveryManagerRowDto row, Long deliveryMethodId) {
        if (deliveryMethodId == null) {
            return true;
        }

        return row != null
                && row.getDeliveryMethodId() != null
                && deliveryMethodId.equals(row.getDeliveryMethodId());
    }

    private boolean matchesSearch(DeliveryManagerRowDto row, String searchType, String keyword) {
        String kw = safeText(keyword).toLowerCase(Locale.ROOT);

        if (kw.isBlank()) {
            return true;
        }

        String type = safeText(searchType);

        if ("companyName".equals(type)) {
            return contains(row.getCompanyName(), kw);
        }

        if ("ordererName".equals(type)) {
            return contains(row.getOrdererName(), kw);
        }

        if ("ordererPhone".equals(type)) {
            return contains(row.getOrdererPhone(), kw);
        }

        if ("representativeName".equals(type)) {
            return contains(row.getRepresentativeName(), kw);
        }

        if ("representativePhone".equals(type)) {
            return contains(row.getRepresentativePhone(), kw);
        }

        if ("orderId".equals(type)) {
            return row.getOrderId() != null && String.valueOf(row.getOrderId()).contains(kw);
        }

        return contains(row.getCompanyName(), kw)
                || contains(row.getOrdererName(), kw)
                || contains(row.getOrdererPhone(), kw)
                || contains(row.getRepresentativeName(), kw)
                || contains(row.getRepresentativePhone(), kw)
                || contains(row.getProductName(), kw)
                || contains(row.getProductSize(), kw)
                || contains(row.getCategoryName(), kw)
                || contains(row.getDeliveryMethodName(), kw)
                || contains(row.getDeliveryAddress(), kw)
                || (row.getOrderId() != null && String.valueOf(row.getOrderId()).contains(kw));
    }

    private void sortRows(List<DeliveryManagerRowDto> rows, String sortKey, String sortDir) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        String key = safeText(sortKey);
        boolean desc = "DESC".equalsIgnoreCase(sortDir);

        Comparator<DeliveryManagerRowDto> comparator;

        switch (key) {
            case "orderId" -> comparator = Comparator.comparing(
                    DeliveryManagerRowDto::getOrderId,
                    Comparator.nullsLast(Long::compareTo)
            );

            case "companyName" -> comparator = Comparator.comparing(
                    row -> safeText(row.getCompanyName()),
                    Comparator.nullsLast(String::compareTo)
            );

            case "productName" -> comparator = Comparator.comparing(
                    row -> safeText(row.getProductName()),
                    Comparator.nullsLast(String::compareTo)
            );

            case "deliveryMethodName" -> comparator = Comparator.comparing(
                    row -> safeText(row.getDeliveryMethodName()),
                    Comparator.nullsLast(String::compareTo)
            );

            case "address" -> comparator = Comparator.comparing(
                    row -> safeText(row.getDeliveryAddress()),
                    Comparator.nullsLast(String::compareTo)
            );

            case "preferredDeliveryDate" -> comparator = Comparator.comparing(
                    DeliveryManagerRowDto::getPreferredDeliveryDate,
                    Comparator.nullsLast(LocalDateTime::compareTo)
            );

            case "categoryName" -> comparator = Comparator.comparing(
                    row -> safeText(row.getCategoryName()),
                    Comparator.nullsLast(String::compareTo)
            );

            case "orderIndex" -> comparator = Comparator.comparingInt(DeliveryManagerRowDto::getOrderIndex)
                    .thenComparing(
                            DeliveryManagerRowDto::getOrderId,
                            Comparator.nullsLast(Long::compareTo)
                    );

            default -> comparator = Comparator.comparingInt(DeliveryManagerRowDto::getOrderIndex)
                    .thenComparing(
                            DeliveryManagerRowDto::getOrderId,
                            Comparator.nullsLast(Long::compareTo)
                    );
        }

        if (desc) {
            comparator = comparator.reversed();
        }

        rows.sort(comparator);
    }

    private Map<String, Object> parseOptionJson(String optionJson) {
        if (optionJson == null || optionJson.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.readValue(
                    optionJson,
                    new TypeReference<LinkedHashMap<String, Object>>() {}
            );
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String pickFirstValue(Map<String, Object> map, Collection<String> keys) {
        if (map == null || map.isEmpty() || keys == null || keys.isEmpty()) {
            return "";
        }

        for (String key : keys) {
            Object value = map.get(key);
            String text = flattenValue(value);

            if (!text.isBlank()) {
                return text;
            }
        }

        return "";
    }

    private String flattenValue(Object value) {
        if (value == null) {
            return "";
        }

        if (value instanceof Map<?, ?> mapValue) {
            return mapValue.entrySet()
                    .stream()
                    .map(entry -> {
                        String key = safeText(entry.getKey());
                        String val = flattenValue(entry.getValue());

                        if (key.isBlank()) {
                            return val;
                        }

                        if (val.isBlank()) {
                            return key;
                        }

                        return key + ": " + val;
                    })
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining(" / "));
        }

        if (value instanceof List<?> listValue) {
            return listValue.stream()
                    .map(this::flattenValue)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining(" / "));
        }

        return safeText(value);
    }

    private Company resolveCompany(Order order) {
        if (order == null
                || order.getTask() == null
                || order.getTask().getRequestedBy() == null) {
            return null;
        }

        return order.getTask().getRequestedBy().getCompany();
    }

    private String buildDisplayAddress(Order order) {
        if (order == null) {
            return "";
        }

        if (isSiteDelivery(order)) {
            String siteAddress = buildSiteAddress(order);

            if (!siteAddress.isBlank()) {
                return siteAddress;
            }
        }

        return buildBasicAddress(order);
    }

    private String buildBasicAddress(Order order) {
        if (order == null) {
            return "";
        }

        List<String> parts = new ArrayList<>();

        addIfNotBlank(parts, order.getZipCode() != null ? "(" + order.getZipCode() + ")" : "");
        addIfNotBlank(parts, order.getDoName());
        addIfNotBlank(parts, order.getSiName());
        addIfNotBlank(parts, order.getGuName());
        addIfNotBlank(parts, order.getRoadAddress());
        addIfNotBlank(parts, order.getDetailAddress());

        return String.join(" ", parts);
    }

    private String buildSiteAddress(Order order) {
        if (order == null) {
            return "";
        }

        List<String> parts = new ArrayList<>();

        addIfNotBlank(parts, order.getSiteZipCode() != null ? "(" + order.getSiteZipCode() + ")" : "");
        addIfNotBlank(parts, order.getSiteDoName());
        addIfNotBlank(parts, order.getSiteSiName());
        addIfNotBlank(parts, order.getSiteGuName());
        addIfNotBlank(parts, order.getSiteRoadAddress());
        addIfNotBlank(parts, order.getSiteDetailAddress());

        return String.join(" ", parts);
    }

    private boolean isSiteDelivery(Order order) {
        if (order == null || order.getDeliveryMethod() == null) {
            return false;
        }

        String methodName = safeText(order.getDeliveryMethod().getMethodName()).replaceAll("\\s+", "");

        return "현장배송".equals(methodName);
    }

    private void addIfNotBlank(List<String> list, String value) {
        String text = safeText(value);

        if (!text.isBlank()) {
            list.add(text);
        }
    }

    private boolean contains(String source, String keywordLowerCase) {
        return safeText(source).toLowerCase(Locale.ROOT).contains(keywordLowerCase);
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return "";
        }

        for (String value : values) {
            String text = safeText(value);

            if (!text.isBlank() && !"-".equals(text)) {
                return text;
            }
        }

        return "";
    }

    private String valueOrDash(String value) {
        String text = safeText(value);
        return text.isBlank() ? "-" : text;
    }

    private String safeText(Object value) {
        if (value == null) {
            return "";
        }

        return String.valueOf(value)
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private void validateDeliveryTeamMember(Member member) {
        if (member == null || member.getTeam() == null || !DELIVERY_TEAM_NAME.equals(member.getTeam().getName())) {
            throw new AccessDeniedException("배송팀만 접근할 수 있습니다.");
        }
    }

    private record RepresentativeLookup(
            Map<Long, String> nameMap,
            Map<Long, String> phoneMap
    ) {
    }
}