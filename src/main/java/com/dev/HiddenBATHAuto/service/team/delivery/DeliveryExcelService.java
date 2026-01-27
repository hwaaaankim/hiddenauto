package com.dev.HiddenBATHAuto.service.team.delivery;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
import com.dev.HiddenBATHAuto.utils.OrderItemOptionJsonUtil;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DeliveryExcelService {

    private final DeliveryOrderIndexRepository deliveryOrderIndexRepository;

    public byte[] buildExcel(Long handlerId, LocalDate deliveryDate, List<Long> orderedOrderIds) {

        List<DeliveryOrderIndex> list = deliveryOrderIndexRepository
                .findAllByHandlerAndDateForTaskGrouping(handlerId, deliveryDate);

        Map<Long, DeliveryOrderIndex> map = new HashMap<>();
        for (DeliveryOrderIndex doi : list) {
            if (doi == null || doi.getOrder() == null || doi.getOrder().getId() == null) continue;
            map.put(doi.getOrder().getId(), doi);
        }

        List<DeliveryOrderIndex> ordered = new ArrayList<>();
        for (Long id : orderedOrderIds) {
            if (id == null) continue;
            DeliveryOrderIndex doi = map.get(id);
            if (doi != null) ordered.add(doi);
        }

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("배송리스트");

            // A4 프린트 설정
            PrintSetup ps = sheet.getPrintSetup();
            ps.setPaperSize(PrintSetup.A4_PAPERSIZE);
            sheet.setFitToPage(true);
            ps.setFitWidth((short) 1);
            ps.setFitHeight((short) 0);
            sheet.setHorizontallyCenter(true);

            // Column widths: 업체명 / 주문자 / 업체 연락처 / 배송지 주소 / 제품내용(50% 폭)
            sheet.setColumnWidth(0, 18 * 256);
            sheet.setColumnWidth(1, 12 * 256);
            sheet.setColumnWidth(2, 16 * 256);
            sheet.setColumnWidth(3, 30 * 256);
            sheet.setColumnWidth(4, 60 * 256);

            // styles
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            setAllBorders(headerStyle);

            Font bodyFont = wb.createFont();
            bodyFont.setFontHeightInPoints((short) 10);

            CellStyle bodyStyle = wb.createCellStyle();
            bodyStyle.setFont(bodyFont);
            bodyStyle.setAlignment(HorizontalAlignment.LEFT);
            bodyStyle.setVerticalAlignment(VerticalAlignment.TOP);
            bodyStyle.setWrapText(true);
            setAllBorders(bodyStyle);

            // header
            int r = 0;
            Row hr = sheet.createRow(r++);
            hr.setHeightInPoints(22);

            createCell(hr, 0, "업체명", headerStyle);
            createCell(hr, 1, "주문자", headerStyle);
            createCell(hr, 2, "업체 연락처", headerStyle);
            createCell(hr, 3, "배송지 주소(우편번호 포함)", headerStyle);
            createCell(hr, 4, "주문 제품 내용", headerStyle);

            // body rows
            for (DeliveryOrderIndex doi : ordered) {
                Order order = doi.getOrder();
                if (order == null) continue;

                Task task = order.getTask();
                Member requester = (task != null ? task.getRequestedBy() : null);
                Company company = (requester != null ? requester.getCompany() : null);

                String companyName = company != null ? safe(company.getCompanyName()) : "";
                String requesterName = requester != null ? safe(requester.getName()) : "";

                // 업체 연락처: company에 필드가 없어서 requester 우선 사용
                String contact = "";
                if (requester != null) {
                    String p1 = safe(requester.getPhone());
                    String p2 = safe(requester.getTelephone());
                    contact = !isBlank(p1) ? p1 : (!isBlank(p2) ? p2 : "");
                }

                // 배송지 주소(우편번호+도로명+상세)
                String deliveryAddr;
                {
                    String zip = safe(order.getZipCode());
                    String road = safe(order.getRoadAddress());
                    String detail = safe(order.getDetailAddress());

                    StringBuilder sb = new StringBuilder();
                    if (!isBlank(zip)) sb.append("(").append(zip).append(") ");
                    if (!isBlank(road)) sb.append(road);
                    if (!isBlank(detail)) sb.append("\n").append(detail);
                    deliveryAddr = sb.toString().trim();
                }

                // 제품 내용(optionJson 예쁘게)
                String productText = "";
                OrderItem item = order.getOrderItem();
                if (item != null) {
                    OrderItemOptionJsonUtil.enrich(item);

                    String t = safe(item.getFormattedOptionText());
                    if (!isBlank(t)) {
                        productText = t.replace(" / ", "\n").replace(" | ", "\n");
                    } else {
                        productText = safe(item.getProductName());
                    }
                }

                Row row = sheet.createRow(r++);
                row.setHeightInPoints(52);

                createCell(row, 0, companyName, bodyStyle);
                createCell(row, 1, requesterName, bodyStyle);
                createCell(row, 2, contact, bodyStyle);
                createCell(row, 3, deliveryAddr, bodyStyle);
                createCell(row, 4, productText, bodyStyle);
            }

            // ✅ 출력용 여백 (POI 5.2.3+ : PageMargin 사용, deprecated 없음)
            applyPrintMargins(sheet);

            wb.write(bos);
            return bos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("엑셀 생성 실패: " + e.getMessage(), e);
        }
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

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}