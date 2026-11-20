package com.dev.HiddenBATHAuto.storefront.delivery;

import com.dev.HiddenBATHAuto.repository.storefront.StoreDeliveryMethodRepository;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "hiddenbath.storefront.delivery-admin-enabled", havingValue = "true")
@Controller
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/admin/store-delivery-methods")
@RequiredArgsConstructor
public class StoreDeliveryAdminController {
    private static final String TOKEN = StoreDeliveryAdminController.class.getName() + ".token";
    private static final String REDIRECT = "redirect:/admin/store-delivery-methods";
    private final StoreDeliveryMethodRepository repository;
    private final StoreDeliveryMethodService service;

    @GetMapping
    public String page(Model model, HttpSession session) {
        synchronized (session) {
            if (session.getAttribute(TOKEN) == null) {
                session.setAttribute(TOKEN, UUID.randomUUID().toString());
            }
            model.addAttribute("formToken", session.getAttribute(TOKEN));
        }
        model.addAttribute("methods", repository.findAllByOrderByIdAsc());
        return "administration/storefront/deliveryMethods";
    }

    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id, @RequestParam(required = false) Long version,
            @RequestParam String name, @RequestParam int deliveryFee, @RequestParam int packingFee,
            @RequestParam(defaultValue = "") String description, @RequestParam String formToken,
            HttpSession session, RedirectAttributes redirect) {
        verify(session, formToken);
        try {
            service.save(id, version, name, deliveryFee, packingFee, description);
            redirect.addFlashAttribute("success", "배송수단을 저장했습니다.");
        } catch (DataIntegrityViolationException e) {
            redirect.addFlashAttribute("error", "같은 이름의 배송수단이 이미 있습니다.");
        } catch (OptimisticLockingFailureException e) {
            redirect.addFlashAttribute("error", "배송수단이 변경되었습니다. 최신 내용을 확인해 주세요.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, @RequestParam Long version, @RequestParam String formToken,
            HttpSession session, RedirectAttributes redirect) {
        verify(session, formToken);
        try {
            service.delete(id, version);
            redirect.addFlashAttribute("success", "배송수단을 삭제했습니다.");
        } catch (OptimisticLockingFailureException e) {
            redirect.addFlashAttribute("error", "배송수단이 변경되었습니다. 최신 내용을 확인해 주세요.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return REDIRECT;
    }

    private static void verify(HttpSession session, String supplied) {
        if (!(session.getAttribute(TOKEN) instanceof String expected)
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
