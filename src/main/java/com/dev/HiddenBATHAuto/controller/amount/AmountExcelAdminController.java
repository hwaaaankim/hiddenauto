package com.dev.HiddenBATHAuto.controller.amount;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.dev.HiddenBATHAuto.dto.amount.AmountCellUpdateRequest;
import com.dev.HiddenBATHAuto.dto.amount.AmountGridResponse;
import com.dev.HiddenBATHAuto.service.amount.AmountExcelColumnDefinition;
import com.dev.HiddenBATHAuto.service.amount.AmountExcelImportService;
import com.dev.HiddenBATHAuto.service.amount.AmountMasterGridService;
import com.dev.HiddenBATHAuto.service.amount.AmountSalesVoucherExportService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/amount-excel")
@RequiredArgsConstructor
public class AmountExcelAdminController {

    private final AmountExcelImportService importService;
    private final AmountMasterGridService gridService;
    private final AmountSalesVoucherExportService salesVoucherExportService;

    @GetMapping("/items")
    public String itemPage(Model model) {
        model.addAttribute("masterType", "items");
        model.addAttribute("pageTitle", "품목_얼마에요 관리");
        model.addAttribute("uploadUrl", "/admin/amount-excel/items/upload");
        model.addAttribute("syncUploadUrl", "/admin/amount-excel/items/sync-upload");
        model.addAttribute("apiUrl", "/admin/amount-excel/api/items");
        model.addAttribute("columns", AmountExcelColumnDefinition.ITEM_COLUMNS);
        return "administration/admin/amount/amountItemMaster";
    }

    @GetMapping("/customers")
    public String customerPage(Model model) {
        model.addAttribute("masterType", "customers");
        model.addAttribute("pageTitle", "거래처_얼마에요 관리");
        model.addAttribute("uploadUrl", "/admin/amount-excel/customers/upload");
        model.addAttribute("apiUrl", "/admin/amount-excel/api/customers");
        model.addAttribute("columns", AmountExcelColumnDefinition.CUSTOMER_COLUMNS);
        return "administration/admin/amount/amountCustomerMaster";
    }

    @PostMapping("/items/upload")
    public String uploadItems(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            var result = importService.replaceItems(file);
            redirectAttributes.addFlashAttribute("message", result.message() + " 저장 " + result.savedCount() + "건");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/admin/amount-excel/items";
    }

    @PostMapping("/items/sync-upload")
    public String syncItems(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            var result = importService.syncItems(file);
            redirectAttributes.addFlashAttribute("message", result.message());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/admin/amount-excel/items";
    }

    @PostMapping("/customers/upload")
    public String uploadCustomers(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            var result = importService.replaceCustomers(file);
            redirectAttributes.addFlashAttribute("message", result.message() + " 저장 " + result.savedCount() + "건");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", e.getMessage());
        }
        return "redirect:/admin/amount-excel/customers";
    }

    @GetMapping("/api/items")
    @ResponseBody
    public AmountGridResponse itemRows(@RequestParam Map<String, String> params,
                                       @RequestParam(value = "offset", defaultValue = "0") int offset,
                                       @RequestParam(value = "limit", defaultValue = "50") Integer limit,
                                       @RequestParam(value = "sortField", required = false) String sortField,
                                       @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir) {
        return gridService.listItems(offset, limit, sortField, sortDir, params);
    }

    @GetMapping("/api/customers")
    @ResponseBody
    public AmountGridResponse customerRows(@RequestParam Map<String, String> params,
                                           @RequestParam(value = "offset", defaultValue = "0") int offset,
                                           @RequestParam(value = "limit", defaultValue = "50") Integer limit,
                                           @RequestParam(value = "sortField", required = false) String sortField,
                                           @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir) {
        return gridService.listCustomers(offset, limit, sortField, sortDir, params);
    }

    @PatchMapping("/api/items/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateItem(@PathVariable Long id,
                                                          @RequestBody AmountCellUpdateRequest request) {
        return ResponseEntity.ok(gridService.updateItem(id, request));
    }

    @PatchMapping("/api/customers/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateCustomer(@PathVariable Long id,
                                                              @RequestBody AmountCellUpdateRequest request) {
        return ResponseEntity.ok(gridService.updateCustomer(id, request));
    }

    @GetMapping("/sales-voucher-download")
    public void downloadSalesVoucher(@RequestParam(required = false) String keyword,
                                     @RequestParam(required = false, defaultValue = "all") String dateCriteria,
                                     @RequestParam(required = false) String startDate,
                                     @RequestParam(required = false) String endDate,
                                     @RequestParam(required = false, defaultValue = "all") String productCategoryId,
                                     @RequestParam(required = false, defaultValue = "all") String orderStatus,
                                     @RequestParam(required = false, defaultValue = "all") String standard,
                                     @RequestParam(required = false, defaultValue = "orderDate") String sortField,
                                     @RequestParam(required = false, defaultValue = "desc") String sortDir,
                                     HttpServletResponse response) throws IOException {
        salesVoucherExportService.downloadSalesVoucher(keyword, dateCriteria, startDate, endDate,
                productCategoryId, orderStatus, standard, sortField, sortDir, response);
    }
}
