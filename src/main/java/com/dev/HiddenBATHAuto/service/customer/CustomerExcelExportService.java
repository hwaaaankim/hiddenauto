package com.dev.HiddenBATHAuto.service.customer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.dto.customer.CustomerPageDtos.AsListRow;
import com.dev.HiddenBATHAuto.dto.customer.CustomerPageDtos.CategoryCount;
import com.dev.HiddenBATHAuto.dto.customer.CustomerPageDtos.TaskListRow;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.utils.SimpleXlsxWriter;

@Service
public class CustomerExcelExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public byte[] buildAsListWorkbook(List<AsListRow> rows, List<String> filterDescriptions) {
        List<String> headers = List.of(
                "AS ID",
                "고객명",
                "현장 연락처",
                "제품정보",
                "AS 증상 제목",
                "AS 담당사원",
                "담당자 연락처",
                "신청일",
                "방문예정일",
                "처리일",
                "금액",
                "유/무상",
                "상태",
                "신청자명",
                "신청자 연락처",
                "신청자 이메일",
                "납품일자",
                "비용 청구 주체",
                "주소",
                "상세 증상"
        );

        List<List<String>> data = new ArrayList<>();
        for (AsListRow row : rows) {
            AsTask task = row.getAsTask();
            data.add(List.of(
                    text(task.getId()),
                    text(task.getCustomerName()),
                    text(task.getOnsiteContact()),
                    text(row.getProductInfo()),
                    text(task.getSubject()),
                    text(row.getHandlerName()),
                    text(row.getHandlerContact()),
                    formatDateTimeDate(task.getRequestedAt()),
                    formatScheduled(row.getScheduledDate(), task.getVisitPlannedTime()),
                    formatDateTimeDate(task.getAsProcessDate()),
                    formatMoney(task.getPrice()),
                    task.getPrice() > 0 ? "유상" : "무상",
                    task.getStatus() != null ? task.getStatus().getLabelKr() : "-",
                    text(task.getApplicantName()),
                    text(task.getApplicantPhone()),
                    text(task.getApplicantEmail()),
                    formatDate(task.getPurchaseDate()),
                    task.getBillingTarget() != null ? task.getBillingTarget().getLabelKr() : "-",
                    joinAddress(task.getRoadAddress(), task.getDetailAddress()),
                    text(task.getReason())
            ));
        }

        double[] widths = {
                10, 14, 16, 34, 28, 14, 16, 13, 19, 13,
                15, 10, 12, 14, 16, 25, 13, 16, 42, 48
        };

        return SimpleXlsxWriter.write(
                "AS 목록",
                "AS 신청 목록",
                filterDescriptions,
                headers,
                data,
                widths);
    }

    public byte[] buildTaskListWorkbook(List<TaskListRow> rows, List<String> filterDescriptions) {
        List<String> headers = List.of(
                "Task ID",
                "주문자명",
                "주문자 연락처",
                "총 오더수",
                "카테고리 구성",
                "배송수단",
                "배송지",
                "발주일",
                "배송예정일",
                "단가(VAT 제외)",
                "합계(VAT 포함)",
                "오더 상태",
                "담당자"
        );

        List<List<String>> data = new ArrayList<>();
        for (TaskListRow row : rows) {
            data.add(List.of(
                    text(row.getTask().getId()),
                    text(row.getOrdererName()),
                    text(row.getOrdererPhone()),
                    row.getOrderCount() + "건",
                    categorySummary(row.getCategoryCounts()),
                    text(row.getDeliveryMethodName()),
                    text(row.getDeliveryAddress()),
                    formatDateTimeDate(row.getTask().getCreatedAt()),
                    formatDateTimeDate(row.getDeliveryDate()),
                    formatMoney(row.getSupplyPrice()),
                    formatMoney(row.getVatIncludedTotalPrice()),
                    text(row.getStatusLabel()),
                    text(row.getManagerName())
            ));
        }

        double[] widths = { 11, 14, 16, 11, 34, 15, 46, 13, 13, 18, 18, 15, 14 };

        return SimpleXlsxWriter.write(
                "발주 목록",
                "고객 발주 목록",
                filterDescriptions,
                headers,
                data,
                widths);
    }

    private String categorySummary(List<CategoryCount> counts) {
        if (counts == null || counts.isEmpty()) {
            return "-";
        }

        return counts.stream()
                .map(item -> item.getName() + " " + item.getCount() + "개")
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");
    }

    private String formatScheduled(LocalDate date, LocalTime time) {
        if (date == null) {
            return "-";
        }
        if (time == null) {
            return date.format(DATE);
        }
        return date.format(DATE) + " " + time.format(TIME);
    }

    private String formatDateTimeDate(LocalDateTime dateTime) {
        return dateTime == null ? "-" : dateTime.toLocalDate().format(DATE);
    }

    private String formatDate(LocalDate date) {
        return date == null ? "-" : date.format(DATE);
    }

    private String formatMoney(long value) {
        return String.format("%,d원", value);
    }

    private String joinAddress(String road, String detail) {
        String left = StringUtils.hasText(road) ? road.trim() : "";
        String right = StringUtils.hasText(detail) ? detail.trim() : "";
        String joined = (left + " " + right).trim();
        return joined.isEmpty() ? "-" : joined;
    }

    private String text(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "-" : text;
    }
}
