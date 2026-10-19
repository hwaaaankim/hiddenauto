package com.dev.HiddenBATHAuto.model.task.audit;

import com.dev.HiddenBATHAuto.model.task.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 검색 판정에 필요한 고객 정보만 보존합니다. 고객 API에는 이 객체를 직접 반환하지 않습니다. */
@Data @NoArgsConstructor
public class CustomerOrderSearchSnapshot {
    private static final ObjectMapper JSON = new ObjectMapper();
    private Long taskId, orderId, deliveryMethodId;
    private String deliveryMethodName, deliveryAddress;
    private String taskDate, orderDate, deliveryDate, productName, category, status;
    private String requesterName, ordererName, ordererPhone, province, city, district;
    public static CustomerOrderSearchSnapshot from(Order o) {
        var s = new CustomerOrderSearchSnapshot();
        s.orderId=o.getId(); s.taskId=o.getTask()==null?null:o.getTask().getId();
        s.taskDate=o.getTask()==null||o.getTask().getCreatedAt()==null?null:o.getTask().getCreatedAt().toLocalDate().toString();
        s.orderDate=o.getCreatedAt()==null?s.taskDate:o.getCreatedAt().toLocalDate().toString();
        s.deliveryDate=o.getPreferredDeliveryDate()==null?null:o.getPreferredDeliveryDate().toLocalDate().toString();
        s.productName=o.getOrderItem()==null?null:o.getOrderItem().getProductName();
        s.category=o.getProductCategory()==null?"미분류":o.getProductCategory().getName();
        try { var options=JSON.readTree(o.getOrderItem()==null?"{}":o.getOrderItem().getOptionJson());
            if(options!=null&&options.hasNonNull("카테고리")&&!options.get("카테고리").asText().isBlank()) s.category=options.get("카테고리").asText().trim();
        } catch(Exception ignored) { /* 기존 옵션 데이터가 비어 있으면 지정된 카테고리 사용 */ }
        s.status=o.getStatus()==null?null:o.getStatus().name();
        s.deliveryMethodId=o.getDeliveryMethod()==null?null:o.getDeliveryMethod().getId();
        s.deliveryMethodName=o.getDeliveryMethod()==null?null:o.getDeliveryMethod().getMethodName();
        s.deliveryAddress=o.getActualDeliveryAddressDisplay();
        var m=o.getTask()==null?null:o.getTask().getRequestedBy();
        s.requesterName=m==null?null:m.getName()!=null&&!m.getName().isBlank()?m.getName():m.getUsername();
        s.ordererName=o.getOrdererName();s.ordererPhone=o.getOrdererPhone();
        boolean site=o.hasMeaningfulSiteDeliveryAddress();
        s.province=site?o.getSiteDoName():o.getDoName();s.city=site?o.getSiteSiName():o.getSiName();s.district=site?o.getSiteGuName():o.getGuName();
        return s;
    }
    public String json() { try { return JSON.writeValueAsString(this); } catch(Exception e) { throw new IllegalStateException("발주 검색 정보 보존 실패",e); } }
    public static CustomerOrderSearchSnapshot read(String json) {
        if(json==null||json.isBlank()) return null;
        try { return JSON.readValue(json,CustomerOrderSearchSnapshot.class); } catch(Exception ignored) { return null; }
    }
    public CustomerOrderSearchSnapshot copy() { return read(json()); }
}
