package com.dev.HiddenBATHAuto.orderExcelUpload.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.dev.HiddenBATHAuto.orderExcelUpload.service.OrderExcelExportService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** Export the currently filtered orders in the order-upload worksheet format. */
@Controller
@RequestMapping("/admin/order-excel-upload")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class OrderExcelExportController {
    private final OrderExcelExportService exportService;

    @GetMapping("/download")
    public void download(@RequestParam(required = false) String keyword,
                         @RequestParam(required = false) String orderIdFrom,
                         @RequestParam(required = false) String orderIdTo,
                         @RequestParam(required = false) String orderId,
                         @RequestParam(required = false) String productName,
                         @RequestParam(required = false, defaultValue = "all") String dateCriteria,
                         @RequestParam(required = false) String startDate,
                         @RequestParam(required = false) String endDate,
                         @RequestParam(required = false, defaultValue = "all") String productCategoryId,
                         @RequestParam(required = false, defaultValue = "all") String orderStatus,
                         @RequestParam(required = false, defaultValue = "all") String standard,
                         @RequestParam(required = false, defaultValue = "orderDate") String sortField,
                         @RequestParam(required = false, defaultValue = "desc") String sortDir,
                         HttpServletResponse response) throws IOException {
        try {
            exportService.download(keyword, orderIdFrom, orderIdTo, orderId, productName,
                    dateCriteria, startDate, endDate, productCategoryId, orderStatus, standard,
                    sortField, sortDir, response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // Generation/validation finishes before setting download headers or writing bytes.
            // Return the order-specific correction message if a stored value cannot be represented.
            if (response.isCommitted()) throw ex;
            response.reset();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("text/plain;charset=UTF-8");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("발주등록용 엑셀을 만들 수 없습니다.\n\n" + ex.getMessage());
        }
    }
}
