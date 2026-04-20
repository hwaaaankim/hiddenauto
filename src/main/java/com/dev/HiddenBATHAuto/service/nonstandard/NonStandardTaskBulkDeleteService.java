package com.dev.HiddenBATHAuto.service.nonstandard;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.dto.nonStandardList.NonStandardOrderDeleteResponse;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderImage;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;
import com.dev.HiddenBATHAuto.repository.order.OrderRepository;
import com.dev.HiddenBATHAuto.repository.order.TaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NonStandardTaskBulkDeleteService {

    private final OrderRepository orderRepository;
    private final TaskRepository taskRepository;
    private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;

    /**
     * 선택된 Order 들이 속한 Task 전체 삭제
     * - Task 삭제
     * - Task 하위 Order 전체 삭제
     * - OrderItem, OrderImage, OrderHistory, TaskHistory cascade 삭제
     * - DeliveryOrderIndex 별도 삭제
     * - 이미지 파일시스템 삭제(afterCommit)
     */
    public NonStandardOrderDeleteResponse deleteTasksByOrderIds(List<Long> rawOrderIds) {
        List<Long> orderIds = normalizeOrderIds(rawOrderIds);
        validateOrderIds(orderIds);

        List<Order> selectedOrders = orderRepository.findAllForBulkDeleteByIds(orderIds);
        if (selectedOrders.isEmpty()) {
            throw new IllegalArgumentException("삭제할 주문을 찾을 수 없습니다.");
        }

        List<Long> taskIds = selectedOrders.stream()
                .map(Order::getTask)
                .filter(Objects::nonNull)
                .map(Task::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (taskIds.isEmpty()) {
            throw new IllegalArgumentException("선택한 주문에 연결된 태스크가 없습니다.");
        }

        List<Order> allOrdersInTasks = orderRepository.findAllForBulkDeleteByTaskIds(taskIds);
        List<Long> allOrderIdsInTasks = allOrdersInTasks.stream()
                .map(Order::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        scheduleImageFileDeleteAfterCommit(allOrdersInTasks);

        if (!allOrderIdsInTasks.isEmpty()) {
            deliveryOrderIndexRepository.deleteByOrderIdIn(allOrderIdsInTasks);
        }

        List<Task> tasks = taskRepository.findAllById(taskIds);
        for (Task task : tasks) {
            taskRepository.delete(task);
        }
        taskRepository.flush();

        return NonStandardOrderDeleteResponse.builder()
                .success(true)
                .message("선택한 주문이 속한 태스크 " + tasks.size() + "건을 삭제했습니다. 하위 주문 "
                        + allOrderIdsInTasks.size()
                        + "건, 주문이미지/주문항목/이력/배송순서 인덱스도 함께 정리되었습니다.")
                .requestedOrderCount(orderIds.size())
                .deletedOrderCount(allOrderIdsInTasks.size())
                .deletedTaskCount(tasks.size())
                .build();
    }

    /**
     * 선택된 Order 만 삭제
     * - OrderImage, OrderItem, OrderHistory cascade 삭제
     * - DeliveryOrderIndex 별도 삭제
     * - 삭제 후 Task 에 남은 Order 가 0건이면 Task 자동 삭제
     * - 이미지 파일시스템 삭제(afterCommit)
     */
    public NonStandardOrderDeleteResponse deleteOrdersByOrderIds(List<Long> rawOrderIds) {
        List<Long> orderIds = normalizeOrderIds(rawOrderIds);
        validateOrderIds(orderIds);

        List<Order> orders = orderRepository.findAllForBulkDeleteByIds(orderIds);
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("삭제할 주문을 찾을 수 없습니다.");
        }

        Map<Long, Task> affectedTasks = new LinkedHashMap<>();
        for (Order order : orders) {
            if (order.getTask() != null && order.getTask().getId() != null) {
                affectedTasks.put(order.getTask().getId(), order.getTask());
            }
        }

        scheduleImageFileDeleteAfterCommit(orders);

        deliveryOrderIndexRepository.deleteByOrderIdIn(orderIds);

        for (Order order : orders) {
            orderRepository.delete(order);
        }
        orderRepository.flush();

        int autoDeletedTaskCount = 0;
        for (Long taskId : affectedTasks.keySet()) {
            long remainCount = orderRepository.countByTask_Id(taskId);
            if (remainCount == 0L) {
                taskRepository.findById(taskId).ifPresent(task -> {
                    taskRepository.delete(task);
                });
                autoDeletedTaskCount++;
            }
        }
        taskRepository.flush();

        String message;
        if (autoDeletedTaskCount > 0) {
            message = "선택한 주문 " + orders.size() + "건을 삭제했고, 주문이 0건이 된 태스크 "
                    + autoDeletedTaskCount + "건도 함께 삭제했습니다.";
        } else {
            message = "선택한 주문 " + orders.size() + "건을 삭제했습니다.";
        }

        return NonStandardOrderDeleteResponse.builder()
                .success(true)
                .message(message)
                .requestedOrderCount(orderIds.size())
                .deletedOrderCount(orders.size())
                .deletedTaskCount(autoDeletedTaskCount)
                .build();
    }

    private void validateOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new IllegalArgumentException("삭제할 주문을 하나 이상 선택해 주세요.");
        }
    }

    private List<Long> normalizeOrderIds(Collection<Long> rawOrderIds) {
        if (rawOrderIds == null) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(
                rawOrderIds.stream()
                        .filter(Objects::nonNull)
                        .toList()
        ));
    }

    private void scheduleImageFileDeleteAfterCommit(List<Order> orders) {
        List<String> filePaths = extractImagePaths(orders);
        if (filePaths.isEmpty()) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deletePhysicalFiles(filePaths);
                }
            });
        } else {
            deletePhysicalFiles(filePaths);
        }
    }

    private List<String> extractImagePaths(List<Order> orders) {
        List<String> filePaths = new ArrayList<>();
        if (orders == null || orders.isEmpty()) {
            return filePaths;
        }

        for (Order order : orders) {
            if (order.getOrderImages() == null || order.getOrderImages().isEmpty()) {
                continue;
            }

            for (OrderImage image : order.getOrderImages()) {
                if (image == null) {
                    continue;
                }
                if (StringUtils.hasText(image.getPath())) {
                    filePaths.add(image.getPath());
                }
            }
        }

        return filePaths.stream().distinct().toList();
    }

    private void deletePhysicalFiles(List<String> filePaths) {
        for (String filePath : filePaths) {
            if (!StringUtils.hasText(filePath)) {
                continue;
            }

            try {
                Files.deleteIfExists(Path.of(filePath));
            } catch (InvalidPathException e) {
                log.warn("주문 이미지 경로가 올바르지 않아 삭제를 건너뜁니다. path={}", filePath);
            } catch (Exception e) {
                log.warn("주문 이미지 파일 삭제 실패. path={}", filePath, e);
            }
        }
    }
}