package com.dev.HiddenBATHAuto.service.order;

import java.util.*;

/** The same Order-level keyword condition is shared by the list, cards and exports. */
public record ManagementOrderKeyword(String pattern, Set<String> fields, boolean and) {
    public static final List<String> FIELD_ORDER = List.of("company", "requester", "product", "address");
    public static ManagementOrderKeyword of(String keyword, String selected, String match) {
        Set<String> fields = new LinkedHashSet<>();
        if (selected == null) fields.addAll(List.of("company", "requester"));
        else for (String field : selected.split(",")) {
            String key = field.trim();
            if (key.isEmpty()) continue;
            if (!FIELD_ORDER.contains(key)) throw new IllegalArgumentException("지원하지 않는 키워드 검색 항목입니다: " + key);
            fields.add(key);
        }
        String mode = match == null || match.isBlank() ? "OR" : match.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("AND", "OR").contains(mode)) throw new IllegalArgumentException("키워드 검색 방식은 OR 또는 AND로 선택해 주세요.");
        String value = keyword == null ? "" : keyword.trim();
        if (!value.isEmpty() && fields.isEmpty()) throw new IllegalArgumentException("키워드를 검색할 항목을 한 개 이상 선택해 주세요.");
        String pattern = value.isEmpty() ? null : "%" + value.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return new ManagementOrderKeyword(pattern, Collections.unmodifiableSet(fields), mode.equals("AND"));
    }
    public String csv() { return String.join(",", FIELD_ORDER.stream().filter(fields::contains).toList()); }
    public boolean company() { return fields.contains("company"); }
    public boolean requester() { return fields.contains("requester"); }
    public boolean product() { return fields.contains("product"); }
    public boolean address() { return fields.contains("address"); }
    /** Search badges use every address component searched in JPQL, including legacy short roads. */
    public static String addressText(com.dev.HiddenBATHAuto.model.task.Order order) {
        if (order == null) return "";
        boolean site = order.hasMeaningfulSiteDeliveryAddress();
        String[] parts = site
            ? new String[]{order.getSiteZipCode(), order.getSiteDoName(), order.getSiteSiName(), order.getSiteGuName(), order.getSiteRoadAddress(), order.getSiteJibunAddress(), order.getSiteDetailAddress()}
            : new String[]{order.getZipCode(), order.getDoName(), order.getSiName(), order.getGuName(), order.getRoadAddress(), order.getJibunAddress(), order.getDetailAddress()};
        return Arrays.stream(parts).map(com.dev.HiddenBATHAuto.utils.DeliveryAddressNormalizationUtil::cleanAddressComponent)
            .filter(p -> !p.isBlank()).collect(java.util.stream.Collectors.joining(" "));
    }

    // Site presence follows Order.hasMeaningfulSiteDeliveryAddress(), regardless of method name.
    public static final String HAS_SITE = "(trim(replace(replace(replace(replace(replace(replace(lower(coalesce(o.siteDoName,'')),'－','-'),'—','-'),'–','-'),'−','-'),'-',''),' ',' ')) not in ('','없음','null','미입력','해당없음','해당 없음') or trim(replace(replace(replace(replace(replace(replace(lower(coalesce(o.siteSiName,'')),'－','-'),'—','-'),'–','-'),'−','-'),'-',''),' ',' ')) not in ('','없음','null','미입력','해당없음','해당 없음') or trim(replace(replace(replace(replace(replace(replace(lower(coalesce(o.siteGuName,'')),'－','-'),'—','-'),'–','-'),'−','-'),'-',''),' ',' ')) not in ('','없음','null','미입력','해당없음','해당 없음') or trim(replace(replace(replace(replace(replace(replace(lower(coalesce(o.siteRoadAddress,'')),'－','-'),'—','-'),'–','-'),'−','-'),'-',''),' ',' ')) not in ('','없음','null','미입력','해당없음','해당 없음') or trim(replace(replace(replace(replace(replace(replace(lower(coalesce(o.siteDetailAddress,'')),'－','-'),'—','-'),'–','-'),'−','-'),'-',''),' ',' ')) not in ('','없음','null','미입력','해당없음','해당 없음'))";
    public static final String ADDRESS = "lower(case when " + HAS_SITE + " then concat(coalesce(o.siteZipCode,''),' ',coalesce(o.siteDoName,''),' ',coalesce(o.siteSiName,''),' ',coalesce(o.siteGuName,''),' ',coalesce(o.siteRoadAddress,''),' ',coalesce(o.siteJibunAddress,''),' ',coalesce(o.siteDetailAddress,'')) else concat(coalesce(o.zipCode,''),' ',coalesce(o.doName,''),' ',coalesce(o.siName,''),' ',coalesce(o.guName,''),' ',coalesce(o.roadAddress,''),' ',coalesce(o.jibunAddress,''),' ',coalesce(o.detailAddress,'')) end) like :keywordPattern escape '!'";
    private static final String COMPANY = "lower(coalesce(company.companyName,'')) like :keywordPattern escape '!'";
    private static final String REQUESTER = "lower(coalesce(requester.name,'')) like :keywordPattern escape '!'";
    private static final String PRODUCT = "lower(coalesce(filterItem.productName,'')) like :keywordPattern escape '!'";
    public static final String QUERY = """
        select o from Order o
        left join o.orderItem filterItem
        left join o.task t
        left join t.requestedBy requester
        left join requester.company company
        left join o.productCategory category
        left join o.deliveryMethod filterMethod
        where (:orderIdFrom is null or o.id >= :orderIdFrom)
          and (:orderIdTo is null or o.id <= :orderIdTo)
          and (:productName is null or lower(filterItem.productName) like lower(concat('%',:productName,'%')))
          and (:keywordPattern is null or
        """ + "(:keywordAnd = false and ((:searchCompany = true and " + COMPANY + ") or (:searchRequester = true and " + REQUESTER + ") or (:searchProduct = true and " + PRODUCT + ") or (:searchAddress = true and " + ADDRESS + "))) or "
        + "(:keywordAnd = true and (:searchCompany = false or " + COMPANY + ") and (:searchRequester = false or " + REQUESTER + ") and (:searchProduct = false or " + PRODUCT + ") and (:searchAddress = false or " + ADDRESS + "))) " + """
          and (:productCategoryId is null or category.id = :productCategoryId)
          and (:deliveryMethodId is null or filterMethod.id = :deliveryMethodId)
          and (:mirrorCuttingOnly = false or o.mirrorCuttingProduct = true)
          and (:status is null or o.status = :status)
          and (:standard is null or o.standard = :standard)
          and ((:dateCriteria = 'order' and (:startDateTime is null or o.createdAt >= :startDateTime) and (:endDateTime is null or o.createdAt <= :endDateTime))
            or (:dateCriteria = 'delivery' and (:startDateTime is null or o.preferredDeliveryDate >= :startDateTime) and (:endDateTime is null or o.preferredDeliveryDate <= :endDateTime))
            or :dateCriteria = 'all')
        """;
}
