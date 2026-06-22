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

    /**
     * 생산 리스트에서 재단 가능 아이콘 표시용.
     * 현재 등록된 대상은 클린/심플/소프트/코지/라운드 + 6xx + 여닫이 + 인도어 + 다리/벽걸이 + 도기/대리석입니다.
     */
    @Transactional(readOnly = true)
    public Map<Long, Boolean> buildCuttingEligibilityMap(List<Order> orders) {
        Map<Long, Boolean> result = new LinkedHashMap<>();

        if (orders == null || orders.isEmpty()) {
            return result;
        }

        for (Order order : orders) {
            if (order == null || order.getId() == null) {
                continue;
            }

            result.put(order.getId(), isCuttingAvailable(order));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public MaterialCuttingPageResponse buildCuttingPage(List<Long> orderIds, Member loginMember) {
        validateCuttingMember(loginMember);

        List<Long> distinctOrderIds = normalizeOrderIds(orderIds);

        if (distinctOrderIds.isEmpty()) {
            return new MaterialCuttingPageResponse(
                    "HINGED_600_CLEAN_SIMPLE_SOFT_COZY_ROUND_V1",
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
        String pageRuleVersion = "HINGED_600_CLEAN_SIMPLE_SOFT_COZY_ROUND_V1";

        for (Long orderId : distinctOrderIds) {
            Order order = orderMap.get(orderId);

            if (order == null) {
                continue;
            }

            MaterialCuttingParsedOptionsDto parsedOptions = optionParser.parse(order);

            if (!ruleRegistry.canResolve(order, parsedOptions)) {
                // 버튼/JS에서도 걸러지지만, URL 직접 호출 시에도 재단 불가 건은 제외합니다.
                continue;
            }

            List<String> warnings = buildWarnings(parsedOptions);

            if (!warnings.isEmpty()) {
                result.add(toOrderDto(order, parsedOptions, List.of(), warnings));
                continue;
            }

            MaterialCuttingRule rule = ruleRegistry.resolve(order, parsedOptions);
            pageRuleVersion = rule.getRuleKey();

            int orderQuantity = resolveOrderQuantity(order);
            List<MaterialCuttingPanelDto> panels = rule.calculate(parsedOptions, orderQuantity);

            result.add(toOrderDto(order, parsedOptions, panels, warnings));
        }

        return new MaterialCuttingPageResponse(
                pageRuleVersion,
                nowText(),
                result
        );
    }

    public boolean isCuttingAvailable(Order order) {
        if (order == null) {
            return false;
        }

        MaterialCuttingParsedOptionsDto parsedOptions = optionParser.parse(order);
        return ruleRegistry.canResolve(order, parsedOptions) && buildWarnings(parsedOptions).isEmpty();
    }

    private List<String> buildWarnings(MaterialCuttingParsedOptionsDto options) {
        List<String> warnings = new ArrayList<>();

        if (options == null) {
            warnings.add("옵션 정보를 해석하지 못했습니다.");
            return warnings;
        }

        if (options.widthMm() == null || options.depthMm() == null || options.heightMm() == null) {
            warnings.add("사이즈(W/D/H)를 찾지 못했습니다. '630(W) * 460(D) * 800(H)' 또는 '넓이: 630, 높이: 800, 깊이: 460' 형식으로 입력해 주세요.");
        }

        if (!options.sixHundredWidthTarget()) {
            warnings.add("현재 등록된 재단 공식은 W 6xx 장만 지원합니다. 제품명에 600장/630장 등이 있거나 W가 600~699mm여야 합니다.");
        }

        if ("UNKNOWN".equals(options.cuttingSeries())) {
            warnings.add("지원 시리즈가 아닙니다. 클린/심플/소프트/코지/라운드 시리즈만 현재 등록되어 있습니다.");
        }

        if (!"HINGED".equals(options.doorMode())) {
            warnings.add("현재는 문 형태가 여닫이인 건만 재단 가능합니다.");
        }

        if (!options.indoorDoor()) {
            warnings.add("현재 문의 제작 방식은 인도어 기준만 지원합니다.");
        }

        if ("UNKNOWN".equals(options.installType())) {
            warnings.add("다리형/벽걸이형 구분을 찾지 못했습니다. optionJson에 '다리형' 또는 '벽걸이형'을 입력해 주세요.");
        }

        if ("UNKNOWN".equals(options.topType())) {
            warnings.add("도기타입/대리석타입 구분을 찾지 못했습니다. optionJson에 '도기' 또는 '대리석'을 입력해 주세요.");
        }

        if ("MARBLE".equals(options.topType()) && "UNKNOWN".equals(options.marbleEdgeType())) {
            warnings.add("대리석타입은 마구리 면수/방향이 필요합니다. 예: '마구리: 3면(좌/우/전)' 또는 '마구리: 2면(좌/우)'.");
        }

        return warnings;
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

        if (productSeries.isBlank()) {
            productSeries = parsedOptions.cuttingSeriesLabel();
        }

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

        int quantity = resolveOrderQuantity(order);

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

    private int resolveOrderQuantity(Order order) {
        if (order == null) {
            return 1;
        }

        OrderItem item = order.getOrderItem();
        int quantity = item != null ? item.getQuantity() : order.getQuantity();
        return Math.max(quantity, 1);
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
