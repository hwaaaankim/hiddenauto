package com.dev.HiddenBATHAuto.dto;

import java.util.List;
import java.util.stream.Collectors;

import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.Task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TaskDetailDTO {
    private String type; // "AS" or "TASK"
    private Long id;     // ✅ 추가: taskId 또는 asTaskId

    // AS 전용
    private String title;
    private String date;
    private String address;

    // 주문 전용
    private List<OrderSummaryDTO> orders;

    public static TaskDetailDTO fromAsTask(AsTask task) {
        return new TaskDetailDTO(
            "AS",
            task.getId(),  // ✅ AS task의 ID
            task.getProductName(),
            task.getRequestedAt().toLocalDate().toString(),
            task.getRoadAddress() + " " + task.getDetailAddress(),
            null
        );
    }

    public static TaskDetailDTO fromTask(Task task) {
        List<OrderSummaryDTO> orders = task.getOrders().stream()
            .map(OrderSummaryDTO::from)
            .collect(Collectors.toList());

        return new TaskDetailDTO(
            "TASK",
            task.getId(),  // ✅ 주문 task의 ID
            null,
            null,
            null,
            orders
        );
    }
}

