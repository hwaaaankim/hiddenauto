package com.dev.HiddenBATHAuto.service.production;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingOrderDto;
import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingPageResponse;
import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingPanelDto;
import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingParsedOptionsDto;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.repository.production.MaterialCuttingRuleRegistry;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MaterialCuttingService {

    private static final Long LOWER_CABINET_CATEGORY_ID = 2L;

    private final OrderRepository orderRepository;
    private final MaterialCuttingOptionParser optionParser;
    private final MaterialCuttingRuleRegistry ruleRegistry;

    @Transactional(readOnly = true)
    public MaterialCuttingPageResponse buildCuttingPage(List<Long> orderIds, Member loginMember) {
        validateCuttingMember(loginMember);

        List<Long> distinctOrderIds = normalizeOrderIds(orderIds);

        if (distinctOrderIds.isEmpty()) {
            return new MaterialCuttingPageResponse(
                    "LOWER_CABINET_V1",
                    nowText(),
                    List.of()
            );
        }

        List<Order> orders = orderRepository.findAllForProductionOverviewByIds(distinctOrderIds);

        Map<Long, Order> orderMap = new LinkedHashMap<>();

        for (Order order : orders) {
            orderMap.put(order.getId(), order);
        }

        List<MaterialCuttingOrderDto> result = new ArrayList<>();
        String pageRuleVersion = "LOWER_CABINET_V1";

        for (Long orderId : distinctOrderIds) {
            Order order = orderMap.get(orderId);

            if (order == null) {
                throw new IllegalArgumentException("주문 없음: " + orderId);
            }

            validateCuttingOrder(order);

            MaterialCuttingParsedOptionsDto parsedOptions = optionParser.parse(order);
            List<String> warnings = new ArrayList<>();

            if (parsedOptions.widthMm() == null
                    || parsedOptions.depthMm() == null
                    || parsedOptions.heightMm() == null) {
                warnings.add("사이즈(W/D/H)를 찾지 못했습니다. optionJson 또는 비고에 '1200(W) * 500(D) * 800(H)' 형식으로 입력해 주세요.");
            }

            MaterialCuttingRule rule = ruleRegistry.resolve(order, parsedOptions);
            pageRuleVersion = rule.getRuleKey();

            List<MaterialCuttingPanelDto> panels = warnings.isEmpty()
                    ? rule.calculate(parsedOptions)
                    : List.of();

            result.add(toOrderDto(order, parsedOptions, panels, warnings));
        }

        return new MaterialCuttingPageResponse(
                pageRuleVersion,
                nowText(),
                result
        );
    }

    private MaterialCuttingOrderDto toOrderDto(
            Order order,
            MaterialCuttingParsedOptionsDto parsedOptions,
            List<MaterialCuttingPanelDto> panels,
            List<String> warnings
    ) {
        OrderItem item = order.getOrderItem();
        Map<String, String> options = parsedOptions.sourceOptions();

        String companyName = resolveCompanyName(order);
        String categoryName = order.getProductCategory() != null ? safeText(order.getProductCategory().getName()) : "-";

        String productSeries = pickFirstValue(options, List.of(
                "제품시리즈",
                "시리즈",
                "series",
                "Series",
                "productSeries",
                "ProductSeries"
        ));

        String productName = pickFirstValue(options, List.of(
                "제품",
                "제품명",
                "product",
                "Product",
                "productName",
                "ProductName"
        ));

        if (productName.isBlank() && item != null) {
            productName = safeText(item.getProductName());
        }

        int quantity = item != null ? item.getQuantity() : order.getQuantity();

        return new MaterialCuttingOrderDto(
                order.getId(),
                valueOrDash(companyName),
                valueOrDash(categoryName),
                valueOrDash(productSeries),
                valueOrDash(productName),
                buildProductOptionText(options),
                quantity,
                valueOrDash(order.getAdminMemo()),
                valueOrDash(order.getOrderComment()),
                parsedOptions,
                panels,
                warnings
        );
    }

    private void validateCuttingMember(Member member) {
        if (member == null || member.getTeam() == null || !"생산팀".equals(member.getTeam().getName())) {
            throw new AccessDeniedException("접근 불가: 생산팀만 접근 가능합니다.");
        }

        TeamCategory teamCategory = member.getTeamCategory();

        if (teamCategory == null) {
            throw new AccessDeniedException("자재재단 권한이 없습니다. 팀 카테고리 정보가 없습니다.");
        }

        if (!isLowerCabinetCategory(teamCategory)) {
            throw new AccessDeniedException("자재재단은 하부장 생산 직원만 접근 가능합니다.");
        }
    }

    private void validateCuttingOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("주문 정보가 없습니다.");
        }

        if (!isLowerCabinetCategory(order.getProductCategory())) {
            throw new AccessDeniedException("하부장 발주만 자재재단을 출력할 수 있습니다.");
        }
    }

    private boolean isLowerCabinetCategory(TeamCategory category) {
        if (category == null) {
            return false;
        }

        return Objects.equals(category.getId(), LOWER_CABINET_CATEGORY_ID)
                || "하부장".equals(category.getName());
    }

    private List<Long> normalizeOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> distinctIds = new LinkedHashSet<>();

        for (Long orderId : orderIds) {
            if (orderId != null) {
                distinctIds.add(orderId);
            }
        }

        return new ArrayList<>(distinctIds);
    }

    private String resolveCompanyName(Order order) {
        try {
            if (order != null
                    && order.getTask() != null
                    && order.getTask().getRequestedBy() != null
                    && order.getTask().getRequestedBy().getCompany() != null
                    && order.getTask().getRequestedBy().getCompany().getCompanyName() != null) {
                return order.getTask().getRequestedBy().getCompany().getCompanyName();
            }
        } catch (Exception ignore) {
            return "";
        }

        return "";
    }

    private String buildProductOptionText(Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return "-";
        }

        List<String> tokens = new ArrayList<>();

        for (Map.Entry<String, String> entry : options.entrySet()) {
            String key = safeText(entry.getKey());
            String value = safeText(entry.getValue());

            if (key.isBlank() || value.isBlank()) {
                continue;
            }

            tokens.add(key + ": " + value);
        }

        if (tokens.isEmpty()) {
            return "-";
        }

        return String.join(" / ", tokens);
    }

    private String pickFirstValue(Map<String, String> map, List<String> keys) {
        if (map == null || map.isEmpty() || keys == null) {
            return "";
        }

        for (String key : keys) {
            String value = safeText(map.get(key));

            if (!value.isBlank()) {
                return value;
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

    private String nowText() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}