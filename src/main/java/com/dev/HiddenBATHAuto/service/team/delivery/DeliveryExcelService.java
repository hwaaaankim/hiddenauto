package com.dev.HiddenBATHAuto.service.team.delivery;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.model.auth.Company;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.DeliveryOrderIndex;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.repository.order.DeliveryOrderIndexRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DeliveryExcelService {

    private static final String SITE_DELIVERY_METHOD_NAME = "현장배송";

    private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;
    private final ObjectMapper objectMapper;

    public byte[] buildExcel(
            Long handlerId,
            String handlerName,
            LocalDate fromDate,
            LocalDate toDate,
            List<Long> orderedOrderIds
    ) {
        List<DeliveryOrderIndex> fetchedIndexes = deliveryOrderIndexRepository
                .findAllByHandlerAndOrderIdsForDeliveryExcel(handlerId, orderedOrderIds);

        Map<Long, DeliveryOrderIndex> indexMap = new LinkedHashMap<>();

        for (DeliveryOrderIndex doi : fetchedIndexes) {
            if (doi == null || doi.getOrder() == null || doi.getOrder().getId() == null) {
                continue;
            }

            indexMap.put(doi.getOrder().getId(), doi);
        }

        List<DeliveryOrderIndex> ordered = new ArrayList<>();

        for (Long orderId : orderedOrderIds) {
            if (orderId == null) {
                continue;
            }

            DeliveryOrderIndex doi = indexMap.get(orderId);

            if (doi != null) {
                ordered.add(doi);
            }
        }

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("배송리스트");

            PrintSetup ps = sheet.getPrintSetup();
            ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
            ps.setLandscape(true);
            ps.setFitWidth((short) 1);
            ps.setFitHeight((short) 0);

            sheet.setFitToPage(true);
            sheet.setHorizontallyCenter(true);
            sheet.setAutobreaks(true);

            sheet.setColumnWidth(0, 18 * 256); // 거래처명
            sheet.setColumnWidth(1, 22 * 256); // 품목
            sheet.setColumnWidth(2, 20 * 256); // 규격
            sheet.setColumnWidth(3, 7 * 256);  // 수량
            sheet.setColumnWidth(4, 28 * 256); // 비고
            sheet.setColumnWidth(5, 10 * 256); // 단위
            sheet.setColumnWidth(6, 12 * 256); // 배송수단
            sheet.setColumnWidth(7, 12 * 256); // 담당자
            sheet.setColumnWidth(8, 36 * 256); // 주소

            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);

            CellStyle titleStyle = wb.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            titleStyle.setWrapText(true);

            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 10);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            setAllBorders(headerStyle);

            Font bodyFont = wb.createFont();
            bodyFont.setFontHeightInPoints((short) 9);

            CellStyle bodyStyle = wb.createCellStyle();
            bodyStyle.setFont(bodyFont);
            bodyStyle.setAlignment(HorizontalAlignment.LEFT);
            bodyStyle.setVerticalAlignment(VerticalAlignment.TOP);
            bodyStyle.setWrapText(true);
            setAllBorders(bodyStyle);

            CellStyle centerBodyStyle = wb.createCellStyle();
            centerBodyStyle.cloneStyleFrom(bodyStyle);
            centerBodyStyle.setAlignment(HorizontalAlignment.CENTER);

            int r = 0;

            Row titleRow = sheet.createRow(r++);
            titleRow.setHeightInPoints(24);

            createCell(
                    titleRow,
                    0,
                    "배송리스트  " + resolveDateLabel(fromDate, toDate) + "  /  담당자: " + valueOrDash(handlerName),
                    titleStyle
            );

            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

            Row headerRow = sheet.createRow(r++);
            headerRow.setHeightInPoints(22);

            createCell(headerRow, 0, "거래처명", headerStyle);
            createCell(headerRow, 1, "품목", headerStyle);
            createCell(headerRow, 2, "규격(사이즈)", headerStyle);
            createCell(headerRow, 3, "수량", headerStyle);
            createCell(headerRow, 4, "비고", headerStyle);
            createCell(headerRow, 5, "단위", headerStyle);
            createCell(headerRow, 6, "배송수단", headerStyle);
            createCell(headerRow, 7, "담당자", headerStyle);
            createCell(headerRow, 8, "주소", headerStyle);

            for (DeliveryOrderIndex doi : ordered) {
                Order order = doi.getOrder();

                if (order == null) {
                    continue;
                }

                OrderItem item = order.getOrderItem();
                Map<String, Object> optionMap = parseOptionJsonToMap(item != null ? item.getOptionJson() : null);

                Task task = order.getTask();
                Member requester = task != null ? task.getRequestedBy() : null;
                Company company = requester != null ? requester.getCompany() : null;

                String companyName = company != null ? safe(company.getCompanyName()) : "";

                String productName = firstNonBlank(
                        pickFirstValue(optionMap, List.of("제품명", "제품", "productName", "ProductName", "product", "Product")),
                        item != null ? item.getProductName() : null
                );

                String size = firstNonBlank(
                        pickFirstValue(optionMap, List.of("사이즈", "규격", "size", "Size", "productSize", "ProductSize", "제품사이즈")),
                        ""
                );

                String category = firstNonBlank(
                        pickFirstValue(optionMap, List.of("카테고리", "category", "Category")),
                        order.getProductCategory() != null ? order.getProductCategory().getName() : null
                );

                String deliveryMethodName = order.getDeliveryMethod() != null
                        ? safe(order.getDeliveryMethod().getMethodName())
                        : "";

                String address = buildExcelAddress(order);

                Row row = sheet.createRow(r++);
                row.setHeightInPoints(46);

                createCell(row, 0, valueOrDash(companyName), bodyStyle);
                createCell(row, 1, valueOrDash(productName), bodyStyle);
                createCell(row, 2, valueOrDash(size), bodyStyle);
                createCell(row, 3, String.valueOf(order.getQuantity()), centerBodyStyle);
                createCell(row, 4, valueOrDash(order.getAdminMemo()), bodyStyle);
                createCell(row, 5, valueOrDash(category), centerBodyStyle);
                createCell(row, 6, valueOrDash(deliveryMethodName), centerBodyStyle);
                createCell(row, 7, valueOrDash(handlerName), centerBodyStyle);
                createCell(row, 8, valueOrDash(address), bodyStyle);
            }

            sheet.setRepeatingRows(new CellRangeAddress(1, 1, 0, 8));
            applyPrintMargins(sheet);

            wb.write(bos);
            return bos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("엑셀 생성 실패: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> parseOptionJsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String pickFirstValue(Map<String, Object> map, List<String> keys) {
        if (map == null || map.isEmpty() || keys == null) {
            return "";
        }

        for (String key : keys) {
            if (key == null) {
                continue;
            }

            Object value = map.get(key);
            String text = safeObject(value);

            if (!text.isBlank()) {
                return text;
            }
        }

        return "";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            String text = safe(value);

            if (!text.isBlank() && !"-".equals(text)) {
                return text;
            }
        }

        return "";
    }

    private String safeObject(Object value) {
        if (value == null) {
            return "";
        }

        if (value instanceof Map<?, ?> mapValue) {
            List<String> parts = new ArrayList<>();

            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                String k = safeObject(entry.getKey());
                String v = safeObject(entry.getValue());

                if (!k.isBlank() && !v.isBlank()) {
                    parts.add(k + ": " + v);
                } else if (!v.isBlank()) {
                    parts.add(v);
                }
            }

            return String.join(" / ", parts).trim();
        }

        if (value instanceof List<?> listValue) {
            List<String> parts = new ArrayList<>();

            for (Object item : listValue) {
                String text = safeObject(item);

                if (!text.isBlank()) {
                    parts.add(text);
                }
            }

            return String.join(" / ", parts).trim();
        }

        return safe(String.valueOf(value));
    }

    private String buildExcelAddress(Order order) {
        if (order == null) {
            return "";
        }

        if (isSiteDelivery(order)) {
            String siteAddress = buildSiteAddress(order);

            if (!siteAddress.isBlank()) {
                return siteAddress;
            }
        }

        return buildBasicAddress(order);
    }

    private String buildBasicAddress(Order order) {
        if (order == null) {
            return "";
        }

        List<String> parts = new ArrayList<>();

        addIfNotBlank(parts, order.getZipCode() != null ? "(" + order.getZipCode() + ")" : "");
        addIfNotBlank(parts, order.getDoName());
        addIfNotBlank(parts, order.getSiName());
        addIfNotBlank(parts, order.getGuName());
        addIfNotBlank(parts, order.getRoadAddress());
        addIfNotBlank(parts, order.getDetailAddress());

        return String.join(" ", parts);
    }

    private String buildSiteAddress(Order order) {
        if (order == null) {
            return "";
        }

        List<String> parts = new ArrayList<>();

        addIfNotBlank(parts, order.getSiteZipCode() != null ? "(" + order.getSiteZipCode() + ")" : "");
        addIfNotBlank(parts, order.getSiteDoName());
        addIfNotBlank(parts, order.getSiteSiName());
        addIfNotBlank(parts, order.getSiteGuName());
        addIfNotBlank(parts, order.getSiteRoadAddress());
        addIfNotBlank(parts, order.getSiteDetailAddress());

        return String.join(" ", parts);
    }

    private boolean isSiteDelivery(Order order) {
        if (order == null || order.getDeliveryMethod() == null) {
            return false;
        }

        String methodName = safe(order.getDeliveryMethod().getMethodName()).replaceAll("\\s+", "");

        return SITE_DELIVERY_METHOD_NAME.equals(methodName);
    }

    private void addIfNotBlank(List<String> list, String value) {
        String text = safe(value);

        if (!text.isBlank()) {
            list.add(text);
        }
    }

    private String resolveDateLabel(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null && toDate != null) {
            if (fromDate.equals(toDate)) {
                return String.valueOf(fromDate);
            }

            return fromDate + " ~ " + toDate;
        }

        if (fromDate != null) {
            return String.valueOf(fromDate);
        }

        if (toDate != null) {
            return String.valueOf(toDate);
        }

        return "-";
    }

    private static void createCell(Row row, int col, String val, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellStyle(style);
        cell.setCellValue(val == null ? "" : val);
    }

    private static void setAllBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private static void applyPrintMargins(XSSFSheet sheet) {
        sheet.setMargin(PageMargin.LEFT, 0.25);
        sheet.setMargin(PageMargin.RIGHT, 0.25);
        sheet.setMargin(PageMargin.TOP, 0.35);
        sheet.setMargin(PageMargin.BOTTOM, 0.35);
    }

    private static String valueOrDash(String s) {
        String text = safe(s);
        return text.isBlank() ? "-" : text;
    }

    private static String safe(Object value) {
        return value == null
                ? ""
                : String.valueOf(value)
                        .replace("\r", " ")
                        .replace("\t", " ")
                        .trim();
    }
}