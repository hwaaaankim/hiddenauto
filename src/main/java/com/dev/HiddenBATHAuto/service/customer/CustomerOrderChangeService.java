package com.dev.HiddenBATHAuto.service.customer;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import com.dev.HiddenBATHAuto.dto.customer.CustomerOrderChange;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeEvent;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeField;
import com.dev.HiddenBATHAuto.repository.order.OrderChangeEventRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class CustomerOrderChangeService {
    private final OrderChangeEventRepository repository;
    private final TaskRepository taskRepository;
    private static final Map<String,String> LABELS = Map.of(
        "preferredDeliveryDate", "출고일(배송희망일)", "deliveryAddress", "배송주소",
        "siteAddress", "현장 배송주소", "deliveryMethod", "배송수단", "deleted", "발주 삭제");

    public List<CustomerOrderChange> forTasks(Long companyId, Collection<Long> taskIds) {
        if (companyId == null || taskIds == null || taskIds.isEmpty()) return List.of();
        return project(repository.findCustomerHistory(companyId, false, taskIds));
    }
    public List<CustomerOrderChange> movedOutsideDeliveryFilter(Long companyId,
            com.dev.HiddenBATHAuto.dto.customer.CustomerPageDtos.TaskListFilter filter) {
        if (!"delivery".equalsIgnoreCase(filter.getDateType()) || (filter.getStartDate() == null && filter.getEndDate() == null)) return List.of();
        LocalDate start = filter.getStartDate(), end = filter.getEndDate();
        LocalDate exclusive = end == null || end.equals(LocalDate.MAX) ? null : end.plusDays(1);
        return forCalendar(companyId, com.dev.HiddenBATHAuto.enums.CalendarDateBasis.PROCESS, start, exclusive, List.of()).stream()
            .filter(c -> c.getPreviousDate() != null
                && (start == null || !c.getPreviousDate().isBefore(start)) && (end == null || !c.getPreviousDate().isAfter(end))
                && (c.isDeleted() || c.getCurrentDate() == null
                    || (start != null && c.getCurrentDate().isBefore(start)) || (end != null && c.getCurrentDate().isAfter(end))))
            .toList();
    }
    public List<CustomerOrderChange> recentDeletedTasks(Long companyId) {
        var ids = repository.findRecentlyDeletedCustomerTaskIds(companyId, org.springframework.data.domain.PageRequest.of(0,30));
        return forTasks(companyId, ids).stream()
            .filter(c -> c.isDeleted() && !c.isTaskAvailable() && "deleted".equals(c.getField())).limit(30).toList();
    }
    public List<CustomerOrderChange> forCalendar(Long companyId) {
        if (companyId == null) return List.of();
        return project(repository.findCustomerHistory(companyId, true, List.of(-1L)));
    }
    public List<CustomerOrderChange> forCalendar(Long companyId, com.dev.HiddenBATHAuto.enums.CalendarDateBasis basis,
            LocalDate start, LocalDate end, Collection<Long> currentTaskIds) {
        if (start == null || end == null) return forCalendar(companyId);
        Set<Long> ids = new LinkedHashSet<>(currentTaskIds);
        ids.addAll(repository.findCustomerCalendarTaskIds(companyId,
            basis == com.dev.HiddenBATHAuto.enums.CalendarDateBasis.PROCESS,
            start.atStartOfDay(), end.atStartOfDay(), start.toString(), end.toString()));
        return forTasks(companyId, ids);
    }
    /** 공개 필드는 명시적으로 허용합니다. 원본 감사 이벤트/summary/actor는 반환하지 않습니다. */
    List<CustomerOrderChange> project(List<OrderChangeEvent> events) {
        if (events == null || events.isEmpty()) return List.of();
        Map<Long,List<OrderChangeEvent>> grouped = events.stream()
            .collect(Collectors.groupingBy(OrderChangeEvent::resolveOrderId, LinkedHashMap::new, Collectors.toList()));
        Set<Long> liveTasks = taskRepository.findAllById(events.stream().map(OrderChangeEvent::resolveTaskId)
            .filter(Objects::nonNull).distinct().toList()).stream().map(t -> t.getId()).collect(Collectors.toSet());
        // 신청 업체는 Task 공통정보이므로 한 Order에서 변경해도 형제 Order의 과거 이력까지 경계를 적용합니다.
        Map<Long, Long> companyTransferByTask = new HashMap<>();
        for (OrderChangeEvent event : events) {
            if (has(event, "requesterCompany") && event.resolveTaskId() != null && event.getId() != null)
                companyTransferByTask.merge(event.resolveTaskId(), event.getId(), Math::max);
        }
        List<CustomerOrderChange> result = new ArrayList<>();
        for (List<OrderChangeEvent> history : grouped.values()) {
            history.sort(Comparator.comparing(OrderChangeEvent::getId, Comparator.nullsFirst(Comparator.naturalOrder())));
            // 고객사가 바뀐 오더는 이전 고객사 주소 등 과거 값을 새 고객사에 공개하지 않습니다.
            int from = 0;
            for (int i=0; i<history.size(); i++) if (has(history.get(i), "requesterCompany")) from = i+1;
            Order current = history.stream().map(OrderChangeEvent::getOrder).filter(Objects::nonNull).findFirst().orElse(null);
            boolean deleted = current == null && history.stream().anyMatch(e -> has(e,"deleted"));
            LocalDate currentDate = current != null && current.getPreferredDeliveryDate() != null
                    ? current.getPreferredDeliveryDate().toLocalDate() : null;
            LocalDate lastKnown = null;
            for (int i=from; i<history.size(); i++) {
                OrderChangeEvent event = history.get(i);
                Long companyTransfer = companyTransferByTask.get(event.resolveTaskId());
                if (companyTransfer != null && (event.getId() == null || event.getId() <= companyTransfer)) continue;
                if (event.getCustomerDeliveryDateSnapshot() != null) lastKnown = event.getCustomerDeliveryDateSnapshot().toLocalDate();
                for (OrderChangeField f : event.getFields()) if ("preferredDeliveryDate".equals(f.getFieldKey())) lastKnown = date(f.getAfterValue());
                boolean registration = has(event, "registration") || (event.getOperationCode() != null && event.getOperationCode().contains("REGISTER"));
                var savedBefore = com.dev.HiddenBATHAuto.model.task.audit.CustomerOrderSearchSnapshot.read(event.getCustomerSearchBeforeSnapshot());
                var savedAfter = com.dev.HiddenBATHAuto.model.task.audit.CustomerOrderSearchSnapshot.read(event.getCustomerSearchAfterSnapshot());
                boolean addressHandled = false;
                // 등록은 변경 안내가 아닙니다. 내부 기본/현장 주소 대신 고객의 실제 배송지를 비교합니다.
                for (OrderChangeField f : event.getFields()) {
                    String key = f.getFieldKey();
                    if (!LABELS.containsKey(key) || registration) continue;
                    String before = display(key, f.getBeforeValue()), after = display(key, f.getAfterValue());
                    if (("deliveryAddress".equals(key) || "siteAddress".equals(key))
                            && savedBefore != null && savedAfter != null
                            && savedBefore.getDeliveryAddress() != null && savedAfter.getDeliveryAddress() != null) {
                        if (addressHandled) continue;
                        addressHandled = true;
                        key = "deliveryAddress";
                        before = display(key, savedBefore.getDeliveryAddress());
                        after = display(key, savedAfter.getDeliveryAddress());
                    }
                    if (Objects.equals(before,after)) continue;
                    LocalDate requested = event.getCustomerOrderCreatedAtSnapshot() != null ? event.getCustomerOrderCreatedAtSnapshot().toLocalDate()
                        : current != null && current.getCreatedAt() != null ? current.getCreatedAt().toLocalDate()
                        : current != null && current.getTask() != null && current.getTask().getCreatedAt() != null ? current.getTask().getCreatedAt().toLocalDate() : null;
                    String product = event.getCustomerProductNameSnapshot();
                    if (product == null && current != null && current.getOrderItem() != null) product = current.getOrderItem().getProductName();
                    result.add(CustomerOrderChange.builder().taskId(event.resolveTaskId()).orderId(event.resolveOrderId())
                        .beforeSearch(beforeSearch(event)).currentSearch(current == null ? null : com.dev.HiddenBATHAuto.model.task.audit.CustomerOrderSearchSnapshot.from(current))
                        .productName(product).field(key).label(LABELS.get(key)).beforeValue(before).afterValue(after)
                        .changedAt(event.getCreatedAt()).previousDate("preferredDeliveryDate".equals(key) ? date(f.getBeforeValue()) : "deleted".equals(key) ? lastKnown : null)
                        .currentDate(currentDate).requestedDate(requested).deleted(deleted)
                        .taskAvailable(liveTasks.contains(event.resolveTaskId())).build());
                }
            }
        }
        result.sort(Comparator.comparing(CustomerOrderChange::getChangedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }
    private com.dev.HiddenBATHAuto.model.task.audit.CustomerOrderSearchSnapshot beforeSearch(OrderChangeEvent e) {
        var before = com.dev.HiddenBATHAuto.model.task.audit.CustomerOrderSearchSnapshot.read(e.getCustomerSearchBeforeSnapshot());
        if (before != null) return before;
        before = com.dev.HiddenBATHAuto.model.task.audit.CustomerOrderSearchSnapshot.read(e.getCustomerSearchAfterSnapshot());
        if (before == null) {
            before = new com.dev.HiddenBATHAuto.model.task.audit.CustomerOrderSearchSnapshot();
            before.setTaskId(e.resolveTaskId()); before.setOrderId(e.resolveOrderId());
            before.setProductName(e.getCustomerProductNameSnapshot());
            if(e.getCustomerDeliveryDateSnapshot()!=null) before.setDeliveryDate(e.getCustomerDeliveryDateSnapshot().toLocalDate().toString());
            // 신청일은 Order 접수일과 Task 접수일을 혼동하지 않습니다. 기록 없는 Task 접수일은 미확인입니다.
        }
        for (var f : e.getFields()) {
            String value = f.getBeforeValue();
            if(value!=null&&(value.isBlank()||"-".equals(value)))value=null;
            switch(f.getFieldKey()) {
                case "preferredDeliveryDate" -> before.setDeliveryDate(date(value)==null?null:date(value).toString());
                case "deliveryAddress", "siteAddress" -> { before.setProvince(null);before.setCity(null);before.setDistrict(null); }
                case "productName" -> before.setProductName(value);
                case "ordererName" -> before.setOrdererName(value);
                case "ordererPhone" -> before.setOrdererPhone(value);
                case "requesterMember" -> before.setRequesterName(null);
                case "productCategory", "optionJson" -> before.setCategory(null);
                case "status" -> { String old = value; before.setStatus(java.util.Arrays.stream(com.dev.HiddenBATHAuto.model.task.OrderStatus.values())
                    .filter(x -> x.name().equals(old)||x.getLabel().equals(old)).map(Enum::name).findFirst().orElse(null)); }
                default -> { }
            }
        }
        return before;
    }

    private boolean has(OrderChangeEvent e, String key) { return e.getFields().stream().anyMatch(f -> key.equals(f.getFieldKey())); }
    private boolean empty(String value) { return value == null || value.isBlank() || "-".equals(value.trim()); }
    private LocalDate date(String value) {
        if (value == null || value.length() < 10) return null;
        try { return LocalDate.parse(value.substring(0,10)); } catch (RuntimeException ignored) { return null; }
    }
    private String display(String key, String value) {
        if (empty(value)) return "미정";
        if ("preferredDeliveryDate".equals(key)) { LocalDate d = date(value); return d == null ? "미정" : d.toString(); }
        if ("deliveryMethod".equals(key)) return value.replaceFirst("^\\d+\\s*/\\s*", "");
        return value.trim();
    }
}
