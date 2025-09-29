package com.dev.HiddenBATHAuto.controller.page;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.service.manager.PopupManagerService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/management")
@RequiredArgsConstructor
public class PopupManagerController {

    private final PopupManagerService popupManagerService;

    /** 팝업 관리 페이지 (리스트 기반, ✅ 노출순서 기준) */
    @GetMapping("/popupManager")
    public String popupManager(Model model) {
        model.addAttribute("popups", popupManagerService.listAllDesc());
        return "administration/management/popup/popupManager";
    }

    /** 신규 등록 */
    @PostMapping(value = "/popupInsert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String popupInsert(@RequestParam("image") MultipartFile image,
                              @RequestParam(value = "linkEnabled", defaultValue = "false") boolean linkEnabled,
                              @RequestParam(value = "linkUrl", required = false) String linkUrl,
                              @RequestParam("startAt") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startAt,
                              @RequestParam("endAt")   @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endAt) throws Exception {

        popupManagerService.insert(image, linkEnabled, linkUrl, startAt, endAt);
        return "redirect:/management/popupManager";
    }

    /* ====== 순서 업데이트 DTO ====== */
    public record PopupOrderUpdateRequest(List<Long> ids) {}

    /** ✅ 순서 업데이트 (AJAX; JSON 바디: { "ids": [3,5,2,...] }) */
    @PostMapping(path = "/updatePopupIndex", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Void> updatePopupIndex(@RequestBody PopupOrderUpdateRequest req) {
        popupManagerService.updateOrderByIds(req.ids());
        return ResponseEntity.ok().build();
    }
    
    /** 수정 (이미지 교체 포함 가능) */
    @PostMapping(value = "/popupUpdate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String popupUpdate(@RequestParam("id") Long id,
                              @RequestParam(value = "image", required = false) MultipartFile image,
                              @RequestParam(value = "linkEnabled", defaultValue = "false") boolean linkEnabled,
                              @RequestParam(value = "linkUrl", required = false) String linkUrl,
                              @RequestParam("startAt") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startAt,
                              @RequestParam("endAt")   @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime endAt) throws Exception {

        popupManagerService.update(id, image, linkEnabled, linkUrl, startAt, endAt);
        return "redirect:/management/popupManager";
    }

    /** 삭제 */
    @PostMapping("/popupDelete")
    public String popupDelete(@RequestParam("id") Long id) {
        popupManagerService.delete(id);
        return "redirect:/management/popupManager";
    }

    /* 필요 시 AJAX용 JSON 엔드포인트도 곁들일 수 있습니다.
    @DeleteMapping("/popup/{id}")
    public ResponseEntity<Void> deleteJson(@PathVariable Long id) {
        popupManagerService.delete(id);
        return ResponseEntity.ok().build();
    }
    */
}
