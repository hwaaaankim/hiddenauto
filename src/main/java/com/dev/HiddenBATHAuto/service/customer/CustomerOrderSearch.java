package com.dev.HiddenBATHAuto.service.customer;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import com.dev.HiddenBATHAuto.dto.customer.CustomerPageDtos.TaskListFilter;
import com.dev.HiddenBATHAuto.model.task.audit.CustomerOrderSearchSnapshot;

/** 현재 발주와 과거 검색 이력을 동일한 AND 조건으로 판정합니다. 모르는 과거 값은 일치로 추정하지 않습니다. */
public final class CustomerOrderSearch {
    private CustomerOrderSearch() {}
    public static boolean active(TaskListFilter f) {
        return text(f.getKeyword())!=null||f.getStartDate()!=null||f.getEndDate()!=null||f.getProvinceId()!=null||f.getCityId()!=null||f.getDistrictId()!=null||selected(f.getStatus())||selected(f.getCategory());
    }
    public static boolean matches(CustomerOrderSearchSnapshot s, TaskListFilter f, String province, String city, String district) {
        if(s==null) return false;
        if(f.getStartDate()!=null&&f.getEndDate()!=null&&f.getStartDate().isAfter(f.getEndDate())) return false;
        if(f.getStartDate()!=null||f.getEndDate()!=null) {
            LocalDate d=date("delivery".equalsIgnoreCase(f.getDateType())?s.getDeliveryDate():s.getTaskDate());
            if(d==null||(f.getStartDate()!=null&&d.isBefore(f.getStartDate()))||(f.getEndDate()!=null&&d.isAfter(f.getEndDate()))) return false;
        }
        if(selected(f.getStatus())&&!f.getStatus().equalsIgnoreCase(s.getStatus())) return false;
        if(selected(f.getCategory())&&!f.getCategory().trim().equals(s.getCategory())) return false;
        if(f.getProvinceId()!=null&&(province==null||!Objects.equals(region(province),region(s.getProvince())))) return false;
        if(f.getCityId()!=null&&(city==null||!Objects.equals(region(city),region(s.getCity())))) return false;
        if(f.getDistrictId()!=null&&(district==null||!Objects.equals(region(district),region(s.getDistrict())))) return false;
        String q=text(f.getKeyword());if(q==null) return true;
        String type=f.getTextType()==null?"ordererName":f.getTextType();
        return switch(type) {
            case "taskId" -> id(s.getTaskId(),q);
            case "orderId" -> id(s.getOrderId(),q);
            case "productName" -> contains(s.getProductName(),q);
            case "requesterName" -> contains(s.getRequesterName(),q);
            case "ordererPhone" -> !digits(q).isEmpty()&&s.getOrdererPhone()!=null&&digits(s.getOrdererPhone()).contains(digits(q));
            default -> contains(s.getOrdererName(),q);
        };
    }
    private static boolean id(Long id,String q) {try {return id!=null&&id.equals(Long.valueOf(digits(q)));}catch(NumberFormatException e){return false;}}
    private static String digits(String s){return s.replaceAll("[^0-9]","");}
    private static boolean contains(String s,String q){return s!=null&&s.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT));}
    private static boolean selected(String s){return text(s)!=null&&!"all".equalsIgnoreCase(s.trim());}
    private static String text(String s){return s==null||s.isBlank()?null:s.trim();}
    private static String region(String s) {
        if(text(s)==null) return null;
        s=com.dev.HiddenBATHAuto.utils.KoreanAdministrativeRegionNormalizer.canonicalProvinceName(s);
        return s.trim().replaceAll("\\s+","").replaceFirst("(특별자치도|특별자치시|특별시|광역시|자치도|자치시|자치구|자치군|도|시|군|구)$","");
    }
    public static LocalDate date(String s){try{return s==null?null:LocalDate.parse(s.substring(0,10));}catch(Exception e){return null;}}
}
