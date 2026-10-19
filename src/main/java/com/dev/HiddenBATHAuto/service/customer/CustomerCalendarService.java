package com.dev.HiddenBATHAuto.service.customer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import com.dev.HiddenBATHAuto.dto.calendar.CalendarTaskDetailDTO;
import com.dev.HiddenBATHAuto.dto.customer.CustomerOrderChange;
import com.dev.HiddenBATHAuto.enums.CalendarDateBasis;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.repository.order.CustomerOrderQueryRepository;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class CustomerCalendarService {
    private final CustomerOrderQueryRepository repository;
    private final CustomerListViewService display;
    private final CustomerOrderChangeService changes;

    public List<Order> orders(Long companyId, CalendarDateBasis basis, LocalDate start, LocalDate end) {
        return repository.findCalendarOrders(companyId, basis == CalendarDateBasis.PROCESS,
            start == null ? null : start.atStartOfDay(), end == null ? null : end.atStartOfDay());
    }
    public List<Order> completedOrders(Long companyId, LocalDate start, LocalDate end) {
        return repository.findCompletedOrders(companyId, com.dev.HiddenBATHAuto.model.task.OrderStatus.DELIVERY_DONE,
            start.atStartOfDay(), end.atStartOfDay());
    }
    public LocalDate date(Order order, CalendarDateBasis basis) {
        LocalDateTime value = basis == CalendarDateBasis.PROCESS ? order.getPreferredDeliveryDate()
            : order.getCreatedAt() != null ? order.getCreatedAt() : order.getTask().getCreatedAt();
        return value == null ? null : value.toLocalDate();
    }
    /** 과거 날짜의 설명용 발주만 반환합니다. 현재 발주 건수/금액에는 포함하지 않습니다. */
    public Map<LocalDate, Set<Long>> historyDates(List<CustomerOrderChange> notices, CalendarDateBasis basis,
            LocalDate start, LocalDate end) {
        Map<LocalDate,Set<Long>> result = new TreeMap<>();
        for (CustomerOrderChange c : notices) {
            LocalDate day = basis == CalendarDateBasis.PROCESS ? c.getPreviousDate()
                : c.isDeleted() ? c.getRequestedDate() : null;
            if (day == null || (!c.isDeleted() && day.equals(c.getCurrentDate()))
                    || (start != null && day.isBefore(start)) || (end != null && !day.isBefore(end))) continue;
            result.computeIfAbsent(day, key -> new LinkedHashSet<>()).add(c.getOrderId());
        }
        return result;
    }
    public Map<LocalDate, Set<Long>> historyDates(Long companyId, CalendarDateBasis basis, LocalDate start, LocalDate end) {
        return historyDates(changes.forCalendar(companyId, basis, start, end, List.of()), basis, start, end);
    }
    public List<CalendarTaskDetailDTO> details(Long companyId, CalendarDateBasis basis, LocalDate target) {
        List<Order> matching = orders(companyId, basis, target, target.plusDays(1));
        var taskIds = matching.stream().map(o -> o.getTask().getId()).distinct().toList();
        List<CustomerOrderChange> notices = changes.forCalendar(companyId, basis, target, target.plusDays(1), taskIds);
        Map<Long,List<Order>> allTaskOrders = taskIds.isEmpty() ? Map.of() : repository.findTaskDeliveryOrders(companyId, taskIds).stream()
            .collect(Collectors.groupingBy(o -> o.getTask().getId()));
        Set<Long> historyIds = historyDates(notices, basis, target, target.plusDays(1)).getOrDefault(target, Set.of());
        Map<Long,CalendarTaskDetailDTO> groups = new LinkedHashMap<>();
        for (Order order : matching) {
            CalendarTaskDetailDTO task = groups.computeIfAbsent(order.getTask().getId(), id -> newTask(id,target));
            var summary = display.buildTaskOrderSummary(order);
            var brief = new CalendarTaskDetailDTO.OrderBriefDTO();
            brief.setOrderId(order.getId()); brief.setCreatedAt(summary.getCreatedAt() == null ? null : summary.getCreatedAt().toLocalDate().toString());
            brief.setPreferredDeliveryDate(summary.getDeliveryDate() == null ? null : summary.getDeliveryDate().toLocalDate().toString());
            brief.setAddress(summary.getDeliveryAddress()); brief.setDeliveryMethodName(summary.getDeliveryMethodName());
            brief.setProductName(summary.getProductName()); brief.setProductSize(summary.getSize()); brief.setProductColor(summary.getColor());
            brief.setCategoryName(summary.getCategoryName()); brief.setQuantity(summary.getQuantity()); brief.setPrice(order.getProductCost());
            brief.setAdminMemo(order.getAdminMemo());
            brief.setDeliveryHandlerVisible(com.dev.HiddenBATHAuto.service.order.DeliveryMethodAssignmentPolicy.isDirectOrSite(order.getDeliveryMethod()));
            brief.setDeliveryHandlerName(summary.getDeliveryHandlerName()); brief.setDeliveryHandlerContact(summary.getDeliveryHandlerContact());
            task.getOrders().add(brief);
            // 별표만 전체 Task의 배송 정보를 비교합니다. 날짜별 응답/집계는 matching만 사용합니다.
            task.setMixedDeliveryDetails(allTaskOrders.getOrDefault(order.getTask().getId(), List.of()).stream().map(o -> Arrays.asList(
                o.getPreferredDeliveryDate() == null ? null : o.getPreferredDeliveryDate().toLocalDate(),
                display.buildEffectiveDeliveryAddress(o), o.getDeliveryMethod() == null ? null : o.getDeliveryMethod().getMethodName()))
                .distinct().count() > 1);
        }
        for (CustomerOrderChange c : notices) if (historyIds.contains(c.getOrderId())) {
            var dto = groups.computeIfAbsent(c.getTaskId(), id -> newTask(id,target));
            dto.setTaskAvailable(c.isTaskAvailable());
        }
        for (var task : groups.values()) {
            task.setHistoryOnly(task.getOrders().isEmpty());
            task.setChanges(notices.stream().filter(c -> Objects.equals(c.getTaskId(),task.getId()))
                .filter(c -> !task.isHistoryOnly() || historyIds.contains(c.getOrderId())).toList());
        }
        return new ArrayList<>(groups.values());
    }
    private CalendarTaskDetailDTO newTask(Long id, LocalDate day) {
        var dto = new CalendarTaskDetailDTO(); dto.setType("TASK"); dto.setId(id);
        dto.setTitle("TASK_"+id); dto.setDate(day.toString()); return dto;
    }
}
