package com.dev.HiddenBATHAuto.dto.customer;

import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;

/** 안내 건수는 필드 수가 아닌 제품(Order) 수입니다. */
@Getter
public class CustomerOrderChangeGroup {
    private Long taskId, orderId;
    private String productName;
    private boolean taskAvailable, deleted;
    private final List<CustomerOrderChange> changes = new ArrayList<>();
    public static List<CustomerOrderChangeGroup> group(List<CustomerOrderChange> values) {
        Map<String,CustomerOrderChangeGroup> groups = new LinkedHashMap<>();
        if(values==null) return List.of();
        for(var c:values) {
            String key=c.getTaskId()+":"+c.getOrderId();
            var g=groups.computeIfAbsent(key,k->{var v=new CustomerOrderChangeGroup();v.taskId=c.getTaskId();v.orderId=c.getOrderId();v.productName=c.getProductName();v.taskAvailable=c.isTaskAvailable();v.deleted=c.isDeleted();return v;});
            if(g.productName==null&&c.getProductName()!=null)g.productName=c.getProductName();
            g.changes.add(c);
        }
        return new ArrayList<>(groups.values());
    }
    public String getProductLabel(){return "Order #"+orderId+(productName==null?"":" · "+shorten(productName,40));}
    public String getSummary(){
        if(deleted) return "삭제된 제품";
        var labels=new LinkedHashSet<String>();
        var seen=new HashSet<String>();
        for(var c:changes) if(seen.add(c.getField())) labels.add(c.getCompactChange());
        return labels.stream().limit(3).collect(Collectors.joining(" · "));
    }
    public List<CustomerOrderChange> getRecentChanges(){return changes.subList(0,Math.min(3,changes.size()));}
    public List<CustomerOrderChange> getOlderChanges(){return changes.subList(Math.min(3,changes.size()),changes.size());}
    public static String tooltip(List<CustomerOrderChange> values){
        var groups=group(values);
        String result=groups.stream().limit(3).map(g->g.getProductLabel()+": "+g.getSummary()).collect(Collectors.joining("\n"));
        return shorten(result,240)+(groups.size()>3?"\n외 "+(groups.size()-3)+"개 제품 · 눌러서 확인":"");
    }
    private static String shorten(String s,int limit){return s.length()<=limit?s:s.substring(0,limit-1)+"…";}
}
