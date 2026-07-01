package com.dev.HiddenBATHAuto.service.productOrderAdd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderAddRequest;
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderAddSaveResponse;
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderCreateRequest;
import com.dev.HiddenBATHAuto.dto.productOrderAdd.ProductOrderOptionEntryRequest;
import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.MemberRole;
import com.dev.HiddenBATHAuto.model.auth.TeamCategory;
import com.dev.HiddenBATHAuto.model.caculate.DeliveryMethod;
import com.dev.HiddenBATHAuto.model.standard.StandardCategory;
import com.dev.HiddenBATHAuto.model.standard.StandardProductSeries;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderImage;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.model.task.TaskStatus;
import com.dev.HiddenBATHAuto.repository.auth.CompanyRepository;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;
import com.dev.HiddenBATHAuto.repository.auth.TeamCategoryRepository;
import com.dev.HiddenBATHAuto.repository.caculate.DeliveryMethodRepository;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;
import com.dev.HiddenBATHAuto.repository.standard.StandardCategoryRepository;
import com.dev.HiddenBATHAuto.repository.standard.StandardProductSeriesRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductOrderAddCommandService {

    private static final Long PRODUCTION_TEAM_ID = 2L;
    private static final Long DELIVERY_TEAM_ID = 3L;
    private static final Long DEFAULT_PRODUCTION_TEAM_CATEGORY_ID = 1L;
    private static final Long DEFAULT_FALLBACK_TEAM_ID = 1L;

    private static final String NON_STANDARD_EXCLUDED_CATEGORY_CUTTING = "재단";
    private static final String NON_STANDARD_EXCLUDED_CATEGORY_MIRROR_CUTTING = "재단(거울)";

    private static final String MANAGEMENT_UPLOAD_TYPE = "MANAGEMENT";
    private static final String NO_STANDARD_SERIES_NAME = "중분류 없음";

    private static final int ZIP_CODE_MAX_LENGTH = 20;
    private static final Pattern KOREA_ZONE_CODE_PATTERN = Pattern.compile("\\b\\d{5}\\b");

    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final TeamCategoryRepository teamCategoryRepository;
    private final StandardCategoryRepository standardCategoryRepository;
    private final StandardProductSeriesRepository standardProductSeriesRepository;
    private final TaskRepository taskRepository;
    private final OrderRepository orderRepository;
    private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;
    private final DeliveryMethodRepository deliveryMethodRepository;
    private final DeliveryHandlerAutoAssignService deliveryHandlerAutoAssignService;
    private final MirrorCuttingProductMatcher mirrorCuttingProductMatcher;
    private final ObjectMapper objectMapper;

    @Value("${spring.upload.path}")
    private String uploadPath;

    public ProductOrderAddSaveResponse create(
            ProductOrderAddRequest request,
            MultiValueMap<String, MultipartFile> fileMap
    ) {
        if (request == null) {
            throw new IllegalArgumentException("발주 요청 정보가 없습니다.");
        }

        if (request.getOrders() == null || request.getOrders().isEmpty()) {
            throw new IllegalArgumentException("최소 1개의 주문이 필요합니다.");
        }

        if (request.getPreferredDeliveryDate() == null) {
            throw new IllegalArgumentException("배송 희망일을 선택해 주세요.");
        }

        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("선택한 업체를 찾을 수 없습니다."));

        Member requestedBy = memberRepository
                .findCompanyMembersByRole(
                        company.getId(),
                        MemberRole.CUSTOMER_REPRESENTATIVE,
                        PageRequest.of(0, 1)
                )
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("해당 업체에 CUSTOMER_REPRESENTATIVE 멤버가 없습니다."));

        DeliveryMethod deliveryMethod = deliveryMethodRepository.findById(request.getDeliveryMethodId())
                .orElseThrow(() -> new IllegalArgumentException("선택한 배송수단을 찾을 수 없습니다."));

        boolean siteDelivery = isSiteDeliveryMethod(deliveryMethod);
        boolean autoAssignTargetMethod = isDeliveryAutoAssignTargetMethod(deliveryMethod);

        validateCommonDeliveryAddress(request);

        if (siteDelivery) {
            validateSiteDeliveryAddress(request);
        }

        validateMoney(request.getPackingCost(), "포장비");
        validateMoney(request.getDeliveryCost(), "운임비");

        Member deliveryHandler = resolveDeliveryHandler(
                request,
                autoAssignTargetMethod,
                siteDelivery
        );

        LocalDateTime now = LocalDateTime.now();
        LocalDate deliveryDate = request.getPreferredDeliveryDate();

        Task task = new Task();
        task.setRequestedBy(requestedBy);
        task.setStatus(TaskStatus.REQUESTED);
        task.setTotalPrice(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task = taskRepository.save(task);

        int ordersTotalAmount = 0;

        boolean hasDeliveryHandler = deliveryHandler != null;
        int nextDeliveryOrderIndex = 0;

        if (hasDeliveryHandler) {
            nextDeliveryOrderIndex = deliveryOrderIndexRepository
                    .findMaxOrderIndexByDeliveryHandlerAndDeliveryDate(
                            deliveryHandler.getId(),
                            deliveryDate
                    )
                    .orElse(0) + 1;
        }

        for (int i = 0; i < request.getOrders().size(); i++) {
            ProductOrderCreateRequest orderRequest = request.getOrders().get(i);

            validateOrderMoney(orderRequest, i + 1);

            ResolvedOrderMeta resolved = resolveOrderMeta(orderRequest);
            LinkedHashMap<String, String> optionMap = buildOptionMap(orderRequest, resolved);

            boolean mirrorCuttingProduct = mirrorCuttingProductMatcher.isMirrorCuttingProduct(
                    orderRequest.getMirrorCuttingProduct(),
                    orderRequest.getProductName(),
                    resolved.categoryName(),
                    resolved.seriesName(),
                    optionMap
            );

            Order order = new Order();
            order.setTask(task);
            order.setStandard(Boolean.TRUE.equals(orderRequest.getStandard()));
            order.setMirrorCuttingProduct(mirrorCuttingProduct);
            order.setProductCategory(resolved.productCategory());
            order.setAssignedProductionTeam(null);

            order.setDeliveryMethod(deliveryMethod);
            order.setPackingCost(request.getPackingCost());
            order.setDeliveryCost(request.getDeliveryCost());

            if (hasDeliveryHandler) {
                order.setAssignedDeliveryHandler(deliveryHandler);
                order.setAssignedDeliveryTeam(deliveryHandler.getTeamCategory());
            } else {
                order.setAssignedDeliveryHandler(null);
                order.setAssignedDeliveryTeam(null);
            }

            applyCommonDeliveryAddress(order, request);

            if (siteDelivery) {
                applySiteDeliveryAddress(order, request);
            } else {
                clearSiteDeliveryAddress(order);
            }

            applyCommonOrdererInfo(order, request);

            order.setPreferredDeliveryDate(deliveryDate.atStartOfDay());
            order.setProductCost(orderRequest.getProductCost());
            order.setQuantity(orderRequest.getQuantity());
            order.setSupplyPrice(orderRequest.getSupplyPrice());
            order.setTotalAmount(orderRequest.getTotalAmount());
            order.setOrderComment(trimToNull(orderRequest.getOrderComment()));
            order.setAdminMemo(trimToNull(orderRequest.getAdminMemo()));
            order.setStatus(OrderStatus.REQUESTED);
            order.setCreatedAt(now);
            order.setUpdatedAt(now);

            OrderItem orderItem = new OrderItem();
            orderItem.setProductName(resolveProductName(optionMap, resolved));
            orderItem.setQuantity(orderRequest.getQuantity());
            orderItem.setOptionJson(toJson(optionMap));

            order.setOrderItem(orderItem);

            order = orderRepository.save(order);

            if (hasDeliveryHandler) {
                registerDeliveryOrderIndex(
                        order,
                        deliveryHandler,
                        deliveryDate,
                        nextDeliveryOrderIndex
                );
                nextDeliveryOrderIndex++;
            }

            List<MultipartFile> files = fileMap == null
                    ? null
                    : fileMap.get("orderFiles_" + i);

            saveOrderFiles(files, task.getId(), order);

            ordersTotalAmount += order.getTotalAmount();
        }

        task.setTotalPrice(
                ordersTotalAmount
                        + request.getPackingCost()
                        + request.getDeliveryCost()
        );
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);

        return new ProductOrderAddSaveResponse(
                true,
                "발주가 등록되었습니다.",
                task.getId()
        );
    }

    private Member resolveDeliveryHandler(
            ProductOrderAddRequest request,
            boolean autoAssignTargetMethod,
            boolean siteDelivery
    ) {
        Long requestedDeliveryHandlerId = normalizePositiveId(request.getDeliveryHandlerId());

        if (requestedDeliveryHandlerId != null) {
            return memberRepository.findByIdAndTeam_Id(requestedDeliveryHandlerId, DELIVERY_TEAM_ID)
                    .orElseThrow(() -> new IllegalArgumentException("배송 담당자는 팀 ID 3 소속 멤버만 선택할 수 있습니다."));
        }

        if (!autoAssignTargetMethod) {
            return null;
        }

        DeliveryAddressForAssignment assignmentAddress = siteDelivery
                ? new DeliveryAddressForAssignment(
                        request.getSiteDoName(),
                        request.getSiteSiName(),
                        request.getSiteGuName()
                )
                : new DeliveryAddressForAssignment(
                        request.getDoName(),
                        request.getSiName(),
                        request.getGuName()
                );

        return deliveryHandlerAutoAssignService
                .findRandomDeliveryHandler(
                        assignmentAddress.doName(),
                        assignmentAddress.siName(),
                        assignmentAddress.guName()
                )
                .orElse(null);
    }

    private boolean isDeliveryAutoAssignTargetMethod(DeliveryMethod deliveryMethod) {
        String methodName = normalizeDeliveryMethodName(deliveryMethod);

        return methodName.contains("직배송")
                || methodName.contains("현장배송")
                || methodName.contains("화물");
    }

    private boolean isSiteDeliveryMethod(DeliveryMethod deliveryMethod) {
        return normalizeDeliveryMethodName(deliveryMethod).contains("현장배송");
    }

    private String normalizeDeliveryMethodName(DeliveryMethod deliveryMethod) {
        String methodName = trimToNull(deliveryMethod == null ? null : deliveryMethod.getMethodName());

        if (methodName == null) {
            return "";
        }

        return methodName.replace(" ", "");
    }

    private void registerDeliveryOrderIndex(
            Order order,
            Member deliveryHandler,
            LocalDate deliveryDate,
            int orderIndex
    ) {
        if (order == null || deliveryHandler == null || deliveryDate == null) {
            return;
        }

        DeliveryOrderIndex deliveryOrderIndex = new DeliveryOrderIndex();
        deliveryOrderIndex.setDeliveryHandler(deliveryHandler);
        deliveryOrderIndex.setOrder(order);
        deliveryOrderIndex.setDeliveryDate(deliveryDate);
        deliveryOrderIndex.setOrderIndex(orderIndex);

        deliveryOrderIndexRepository.save(deliveryOrderIndex);
    }

    private void validateCommonDeliveryAddress(ProductOrderAddRequest request) {
        normalizeZipCodeRequired(request.getZipCode(), "우편번호");
        normalizeRequired(request.getDoName(), "도/시");
        normalizeRequired(request.getRoadAddress(), "도로명 주소");
    }

    private void validateSiteDeliveryAddress(ProductOrderAddRequest request) {
        normalizeZipCodeRequired(request.getSiteZipCode(), "현장주소 우편번호");
        normalizeRequired(request.getSiteDoName(), "현장주소 도/시");
        normalizeRequired(request.getSiteRoadAddress(), "현장 도로명 주소");
    }

    private void applyCommonDeliveryAddress(Order order, ProductOrderAddRequest request) {
        order.setZipCode(normalizeZipCodeRequired(request.getZipCode(), "우편번호"));
        order.setDoName(normalizeRequired(request.getDoName(), "도/시"));
        order.setSiName(trimToNull(request.getSiName()));
        order.setGuName(trimToNull(request.getGuName()));
        order.setRoadAddress(normalizeRequired(request.getRoadAddress(), "도로명 주소"));
        order.setDetailAddress(trimToNull(request.getDetailAddress()));
    }

    private void applySiteDeliveryAddress(Order order, ProductOrderAddRequest request) {
        order.setSiteZipCode(normalizeZipCodeRequired(request.getSiteZipCode(), "현장주소 우편번호"));
        order.setSiteDoName(normalizeRequired(request.getSiteDoName(), "현장주소 도/시"));
        order.setSiteSiName(trimToNull(request.getSiteSiName()));
        order.setSiteGuName(trimToNull(request.getSiteGuName()));
        order.setSiteRoadAddress(normalizeRequired(request.getSiteRoadAddress(), "현장 도로명 주소"));
        order.setSiteDetailAddress(trimToNull(request.getSiteDetailAddress()));
    }

    private void clearSiteDeliveryAddress(Order order) {
        order.setSiteZipCode(null);
        order.setSiteDoName(null);
        order.setSiteSiName(null);
        order.setSiteGuName(null);
        order.setSiteRoadAddress(null);
        order.setSiteDetailAddress(null);
    }

    private void applyCommonOrdererInfo(Order order, ProductOrderAddRequest request) {
        order.setOrdererName(trimToNull(request.getOrdererName()));
        order.setOrdererPhone(trimToNull(request.getOrdererPhone()));
    }

    private ResolvedOrderMeta resolveOrderMeta(ProductOrderCreateRequest request) {
        boolean standard = Boolean.TRUE.equals(request.getStandard());

        if (standard) {
            if (request.getStandardCategoryId() == null) {
                throw new IllegalArgumentException("규격 주문은 대분류를 선택해야 합니다.");
            }

            StandardCategory category = standardCategoryRepository
                    .findById(request.getStandardCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("선택한 규격 대분류를 찾을 수 없습니다."));

            Long standardProductSeriesId = normalizePositiveId(request.getStandardProductSeriesId());

            StandardProductSeries series = null;

            if (standardProductSeriesId != null) {
                series = standardProductSeriesRepository
                        .findByIdAndCategory_Id(
                                standardProductSeriesId,
                                request.getStandardCategoryId()
                        )
                        .orElseThrow(() -> new IllegalArgumentException("선택한 규격 중분류를 찾을 수 없습니다."));
            }

            TeamCategory productCategory = resolveProductionCategoryForStandardCategory(category.getName());

            return new ResolvedOrderMeta(
                    productCategory,
                    category.getName(),
                    series == null ? null : series.getName(),
                    series == null ? null : series.getId()
            );
        }

        if (request.getProductionCategoryId() == null) {
            throw new IllegalArgumentException("비규격 주문은 생산팀 분류를 선택해야 합니다.");
        }

        TeamCategory productCategory = teamCategoryRepository
                .findByIdAndTeam_Id(request.getProductionCategoryId(), PRODUCTION_TEAM_ID)
                .orElseThrow(() -> new IllegalArgumentException("선택한 생산팀 분류를 찾을 수 없습니다."));

        if (isExcludedNonStandardProductionCategory(productCategory)) {
            throw new IllegalArgumentException("비규격 주문에서는 재단 또는 재단(거울) 생산팀 분류를 선택할 수 없습니다.");
        }

        return new ResolvedOrderMeta(
                productCategory,
                productCategory.getName(),
                null,
                null
        );
    }

    private TeamCategory resolveProductionCategoryForStandardCategory(String standardCategoryName) {
        if (standardCategoryName == null || standardCategoryName.trim().isBlank()) {
            throw new IllegalArgumentException("규격 대분류명이 비어 있습니다.");
        }

        String normalizedName = standardCategoryName.trim();

        return teamCategoryRepository
                .findFirstByTeam_IdAndNameIgnoreCase(PRODUCTION_TEAM_ID, normalizedName)
                .or(() -> teamCategoryRepository.findByIdAndTeam_Id(
                        DEFAULT_PRODUCTION_TEAM_CATEGORY_ID,
                        DEFAULT_FALLBACK_TEAM_ID
                ))
                .orElseThrow(() -> new IllegalArgumentException(
                        "규격 대분류명 '" + normalizedName
                                + "' 와 일치하는 생산팀 카테고리가 없고, 기본 TeamCategory(id=1, team.id=1)도 찾을 수 없습니다."
                ));
    }

    private LinkedHashMap<String, String> buildOptionMap(
            ProductOrderCreateRequest request,
            ResolvedOrderMeta resolved
    ) {
        LinkedHashMap<String, String> optionMap = new LinkedHashMap<>();

        optionMap.put("카테고리", resolved.categoryName());

        if (Boolean.TRUE.equals(request.getStandard())) {
            optionMap.put(
                    "제품시리즈",
                    resolved.seriesName() == null || resolved.seriesName().isBlank()
                            ? NO_STANDARD_SERIES_NAME
                            : resolved.seriesName()
            );

            optionMap.put(
                    "제품시리즈ID",
                    resolved.seriesId() == null
                            ? ""
                            : String.valueOf(resolved.seriesId())
            );
        }

        optionMap.put("제품명", normalizeRequired(request.getProductName(), "제품명"));
        optionMap.put("사이즈", normalizeRequired(request.getProductSize(), "사이즈"));
        optionMap.put("색상", normalizeRequired(request.getProductColor(), "색상"));

        int optionNo = 1;

        if (request.getOptionEntries() != null) {
            for (ProductOrderOptionEntryRequest entry : request.getOptionEntries()) {
                if (entry == null) {
                    continue;
                }

                String answer = trimToNull(entry.getAnswer());

                if (answer == null) {
                    continue;
                }

                String key = optionNo == 1
                        ? "옵션"
                        : "옵션" + optionNo;

                while (optionMap.containsKey(key)) {
                    optionNo++;
                    key = optionNo == 1
                            ? "옵션"
                            : "옵션" + optionNo;
                }

                optionMap.put(key, answer);
                optionNo++;
            }
        }

        return optionMap;
    }

    private void validateOrderMoney(ProductOrderCreateRequest request, int orderNo) {
        if (request == null) {
            throw new IllegalArgumentException("주문 " + orderNo + ": 주문 정보가 없습니다.");
        }

        if (request.getProductCost() < 0) {
            throw new IllegalArgumentException("주문 " + orderNo + ": 제품단가는 0 이상이어야 합니다.");
        }

        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException("주문 " + orderNo + ": 수량은 1 이상이어야 합니다.");
        }

        if (request.getSupplyPrice() < 0) {
            throw new IllegalArgumentException("주문 " + orderNo + ": 공급가는 0 이상이어야 합니다.");
        }

        if (request.getTotalAmount() < 0) {
            throw new IllegalArgumentException("주문 " + orderNo + ": 총액은 0 이상이어야 합니다.");
        }
    }

    private void validateMoney(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + "은(는) 0 이상이어야 합니다.");
        }
    }

    private Long normalizePositiveId(Long id) {
        if (id == null || id <= 0) {
            return null;
        }

        return id;
    }

    private String normalizeZipCodeRequired(String value, String fieldName) {
        String trimmed = normalizeRequired(value, fieldName);

        Matcher matcher = KOREA_ZONE_CODE_PATTERN.matcher(trimmed);

        if (matcher.find()) {
            return matcher.group();
        }

        if (trimmed.length() <= ZIP_CODE_MAX_LENGTH) {
            return trimmed;
        }

        throw new IllegalArgumentException(
                fieldName + "가 너무 깁니다. 주소검색으로 우편번호를 다시 선택해 주세요. 입력값: " + trimmed
        );
    }

    private String resolveProductName(
            LinkedHashMap<String, String> optionMap,
            ResolvedOrderMeta resolved
    ) {
        String productName = optionMap.get("제품명");

        if (productName != null && !productName.isBlank()) {
            return productName.trim();
        }

        if (resolved.seriesName() != null && !resolved.seriesName().isBlank()) {
            return resolved.seriesName();
        }

        return resolved.categoryName();
    }

    private void saveOrderFiles(
            List<MultipartFile> files,
            Long taskId,
            Order order
    ) {
        if (files == null || files.isEmpty()) {
            return;
        }

        String dateFolder = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

        Path uploadDir = Paths.get(
                normalizeDir(uploadPath),
                "order",
                "management",
                String.valueOf(taskId),
                String.valueOf(order.getId()),
                dateFolder
        );

        try {
            Files.createDirectories(uploadDir);

            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }

                String originalFilename = StringUtils.cleanPath(
                        Objects.requireNonNullElse(file.getOriginalFilename(), "file")
                );

                String extension = getExtension(originalFilename);
                String savedFilename = UUID.randomUUID()
                        + (extension.isBlank() ? "" : "." + extension);

                Path targetPath = uploadDir.resolve(savedFilename);
                file.transferTo(targetPath);

                OrderImage orderImage = new OrderImage();
                orderImage.setType(MANAGEMENT_UPLOAD_TYPE);
                orderImage.setFilename(originalFilename);
                orderImage.setPath(targetPath.toString().replace("\\", "/"));
                orderImage.setUrl(
                        "/upload/order/management/"
                                + taskId
                                + "/"
                                + order.getId()
                                + "/"
                                + dateFolder
                                + "/"
                                + savedFilename
                );
                orderImage.setUploadedAt(LocalDateTime.now());

                order.addOrderImage(orderImage);
            }

            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);

        } catch (IOException e) {
            throw new IllegalArgumentException("파일 저장 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private String toJson(LinkedHashMap<String, String> optionMap) {
        try {
            return objectMapper.writeValueAsString(optionMap);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("옵션 JSON 생성에 실패했습니다.");
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 비어 있을 수 없습니다.");
        }

        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean isExcludedNonStandardProductionCategory(TeamCategory category) {
        if (category == null) {
            return true;
        }

        String normalizedName = normalizeProductionCategoryName(category.getName());

        return NON_STANDARD_EXCLUDED_CATEGORY_CUTTING.equals(normalizedName)
                || NON_STANDARD_EXCLUDED_CATEGORY_MIRROR_CUTTING.equals(normalizedName);
    }

    private String normalizeProductionCategoryName(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().replaceAll("\\s+", "");
    }

    private String normalizeDir(String dir) {
        if (!StringUtils.hasText(dir)) {
            return dir;
        }

        String normalized = dir.replace("\\", "/").trim();

        String userHome = System.getProperty("user.home");

        if (StringUtils.hasText(userHome)) {
            userHome = userHome.replace("\\", "/");
            normalized = normalized.replace("${user.home}", userHome);

            if (normalized.equals("~")) {
                normalized = userHome;
            } else if (normalized.startsWith("~/")) {
                normalized = userHome + normalized.substring(1);
            }
        }

        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }

        return normalized;
    }

    private String getExtension(String filename) {
        int index = filename.lastIndexOf('.');

        if (index < 0 || index == filename.length() - 1) {
            return "";
        }

        return filename.substring(index + 1);
    }

    private record DeliveryAddressForAssignment(
            String doName,
            String siName,
            String guName
    ) {
    }

    private record ResolvedOrderMeta(
            TeamCategory productCategory,
            String categoryName,
            String seriesName,
            Long seriesId
    ) {
    }
}