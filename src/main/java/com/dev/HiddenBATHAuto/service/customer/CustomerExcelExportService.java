package com.dev.HiddenBATHAuto.service.customer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.dto.customer.CustomerPageDtos.AsListRow;
import com.dev.HiddenBATHAuto.dto.customer.CustomerPageDtos.TaskOrderSummary;
import com.dev.HiddenBATHAuto.dto.customer.CustomerPageDtos.TaskListRow;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.utils.SimpleXlsxWriter;

@Service
public class CustomerExcelExportService {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	public byte[] buildAsListWorkbook(List<AsListRow> rows, List<String> filterDescriptions) {
		List<String> headers = List.of("AS ID", "계정 신청자", "고객명", "현장 연락처", "제품정보", "AS 증상 제목", "AS 담당사원", "담당자 연락처",
				"신청일", "방문예정일", "처리일", "금액", "유/무상", "상태", "신청자명", "신청자 연락처", "신청자 이메일", "납품일자", "비용 청구 주체", "주소",
				"상세 증상");

		List<List<String>> data = new ArrayList<>();
		for (AsListRow row : rows) {
			AsTask task = row.getAsTask();
			data.add(List.of(text(task.getId()), text(row.getRequesterName()), text(task.getCustomerName()),
					text(task.getOnsiteContact()), text(row.getProductInfo()), text(task.getSubject()),
					text(row.getHandlerName()), text(row.getHandlerContact()),
					formatDateTimeDate(task.getRequestedAt()),
					formatScheduled(row.getScheduledDate(), task.getVisitPlannedTime()),
					formatDateTimeDate(task.getAsProcessDate()), formatMoney(task.getPrice()),
					task.getPrice() > 0 ? "유상" : "무상", task.getStatus() != null ? task.getStatus().getLabelKr() : "-",
					text(task.getApplicantName()), text(task.getApplicantPhone()), text(task.getApplicantEmail()),
					formatDate(task.getPurchaseDate()),
					task.getBillingTarget() != null ? task.getBillingTarget().getLabelKr() : "-",
					joinAddress(task.getRoadAddress(), task.getDetailAddress()), text(task.getReason())));
		}

		double[] widths = { 10, 14, 14, 16, 34, 28, 14, 16, 13, 19, 13, 15, 10, 12, 14, 16, 25, 13, 16, 42, 48 };

		return SimpleXlsxWriter.write("AS 목록", "AS 신청 목록", filterDescriptions, headers, data, widths);
	}

	public byte[] buildTaskListWorkbook(List<TaskListRow> rows, List<String> filterDescriptions) {
		List<String> headers = List.of("Task ID", "신청자", "주문자명", "총 오더수", "카테고리 구성", "배송수단", "배송지", "발주일", "배송예정일",
				"단가(VAT 제외)", "제품합계(VAT 포함)", "포장비(Task당 1회)", "배송비(Task당 1회)", "최종합계", "오더 상태", "배송담당자");

		List<List<String>> data = new ArrayList<>();
		for (TaskListRow row : rows) {
			data.add(List.of(text(row.getTask().getId()) + (row.isMixedDeliveryDetails() ? " *" : ""), text(row.getRequesterName()), text(row.getOrdererName()),
					row.getOrderCount() + "건", categorySummary(row.getOrderSummaries(), row.isSearchFiltered()),
					orderShipping(row.getOrderSummaries(), false), orderShipping(row.getOrderSummaries(), true),
					formatDateTimeDate(row.getTask().getCreatedAt()), orderDeliveryDates(row.getOrderSummaries()),
					formatMoney(row.getSupplyPrice()), formatMoney(row.getVatIncludedTotalPrice()),
					formatMoney(row.getPackingCost()), formatMoney(row.getDeliveryCost()),
					formatMoney(row.getGrandTotalPrice()), text(row.getStatusLabel()),
					text(row.getDeliveryHandlerName())));
		}

		double[] widths = { 11, 14, 14, 11, 80, 15, 46, 13, 30, 18, 18, 17, 17, 18, 15, 16 };

		List<String> notes = new ArrayList<>(filterDescriptions == null ? List.of() : filterDescriptions);
		if (rows.stream().anyMatch(TaskListRow::isSearchFiltered)) notes.add("검색된 주문 묶음의 전체 제품과 전체 합계입니다. 제품별 [검색 일치]/[함께 주문] 표시를 확인해 주세요.");
		notes.add("배송예정일은 제품 정보와 같은 순서로 Order별 표시됩니다. 미정은 날짜가 지정되지 않은 제품입니다.");
		if (rows.stream().anyMatch(TaskListRow::isMixedDeliveryDetails)) {
			notes.add("* 표시된 주문에는 배송일·배송주소·배송수단이 다른 제품이 포함되어 있습니다.");
		}
		return SimpleXlsxWriter.write("발주 목록", "고객 발주 목록", notes, headers, data, widths, true);
	}

    private String orderShipping(List<TaskOrderSummary> items, boolean address) {
        return items.stream().map(item -> "Order #" + item.getOrderId() + " : "
            + (address ? item.getDeliveryAddress() : item.getDeliveryMethodName()))
            .collect(java.util.stream.Collectors.joining("\n"));
    }

	private String orderDeliveryDates(List<TaskOrderSummary> summaries) {
		if (summaries == null || summaries.isEmpty()) {
			return "-";
		}
		List<String> lines = new ArrayList<>();
		for (TaskOrderSummary item : summaries) {
			if (item != null) {
				lines.add("Order #" + text(item.getOrderId()) + " : "
						+ (item.getDeliveryDate() == null ? "미정" : formatDateTimeDate(item.getDeliveryDate())));
			}
		}
		return lines.isEmpty() ? "-" : String.join("\n", lines);
	}

	private String categorySummary(List<TaskOrderSummary> summaries, boolean filtered) {
		if (summaries == null || summaries.isEmpty()) {
			return "-";
		}

		List<String> lines = new ArrayList<>();

		for (TaskOrderSummary item : summaries) {
			if (item == null) {
				continue;
			}

			List<String> parts = new ArrayList<>();
			parts.add((filtered ? (item.isSearchMatch() ? "[검색 일치] " : "[함께 주문] ") : "") + "제품명 : " + text(item.getProductName()));
			parts.add("사이즈 : " + text(item.getSize()));
			parts.add("색상 : " + text(item.getColor()));
			parts.add("수량 : " + item.getQuantity() + "개");

			lines.add(String.join(" / ", parts));
		}

		return lines.isEmpty() ? "-" : String.join("\n", lines);
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
