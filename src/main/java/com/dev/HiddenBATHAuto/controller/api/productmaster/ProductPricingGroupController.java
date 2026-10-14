package com.dev.HiddenBATHAuto.controller.api.productmaster;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ApiResponse;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.GroupView;
import com.dev.HiddenBATHAuto.model.productmaster.ProductAttributeGroup;
import com.dev.HiddenBATHAuto.service.productmaster.ProductPricingService;
import com.dev.HiddenBATHAuto.service.productmaster.ProductStudioAttributeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.security.Principal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.bind.annotation.*;

/** Price participation can change independently of an immutable shared question definition. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/api/product-master/studio/groups")
@PreAuthorize("hasRole('ADMIN')")
public class ProductPricingGroupController {
  private final ProductStudioAttributeService attributes;
  private final ProductPricingService pricing;
  private final EntityManager entityManager;

  public record Edit(Long version, boolean priceImpact) {}
  public record State(Long groupId, long version, boolean priceImpact, boolean locked, String message) {}

  @GetMapping("/{id}/price-impact")
  @Transactional(readOnly = true)
  public ApiResponse<State> state(@PathVariable Long id) {
    ProductAttributeGroup g = attributes.require(id);
    boolean locked = pricing.groupParticipates(id);
    return ApiResponse.ok(new State(id, g.getRowVersion(), g.isPriceImpact(), locked,
        locked ? "등록된 가격계산 항에서 사용하는 그룹입니다. 연결된 가격 항을 모두 제거한 뒤 가격영향을 해제할 수 있습니다."
            : "제품에 사용 중이어도 가격영향 설정은 별도로 변경할 수 있습니다. 가격 항에 참여하면 해제가 잠깁니다."));
  }

  @PutMapping("/{id}/price-impact")
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public ApiResponse<GroupView> update(@PathVariable Long id, @RequestBody Edit edit, Principal actor) {
    if (edit == null) throw new IllegalArgumentException("가격영향 설정을 입력해 주세요.");
    ProductAttributeGroup g = attributes.require(id);
    entityManager.lock(g, LockModeType.PESSIMISTIC_WRITE);
    entityManager.refresh(g);
    ProductStudioAttributeService.version(edit.version(), g.getRowVersion());
    if (!g.isNonStandard()) throw new IllegalArgumentException("가격영향은 비규격 그룹에서만 설정합니다.");
    if (!edit.priceImpact()) pricing.assertGroupPriceImpactCanDisable(id);
    g.setPriceImpact(edit.priceImpact());
    g.setUpdatedBy(actor.getName());
    g.setUpdatedAt(LocalDateTime.now());
    entityManager.flush();
    return ApiResponse.ok(attributes.view(g));
  }
}
