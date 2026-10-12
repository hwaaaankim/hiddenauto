package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductDrawingDtos.*;

import com.dev.HiddenBATHAuto.model.productmaster.ProductActual;
import com.dev.HiddenBATHAuto.model.productmaster.ProductDrawing;
import com.dev.HiddenBATHAuto.model.productmaster.ProductMaster;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductActualRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductDrawingRepository;
import com.dev.HiddenBATHAuto.repository.productmaster.ProductMasterRepository;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductDrawingService {
  private final ProductMasterRepository products;
  private final ProductActualRepository actuals;
  private final ProductDrawingRepository drawings;
  private final EntityManager entityManager;

  @Transactional(readOnly = true)
  public DrawingView get(String kind, Long id) {
    Target target = target(kind, id, false);
    return view(target, findDrawing(target));
  }

  /** Only explicit registration reaches this method. The editor's autosave is never persisted. */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public DrawingView save(String kind, Long id, SaveRequest request, String actor) {
    if (request == null) throw new IllegalArgumentException("등록할 도면 내용을 확인해 주세요.");
    if (request.expectedVersion() != null && request.expectedVersion() < 0)
      throw new IllegalArgumentException("도면 버전 정보를 다시 확인해 주세요.");
    if (actor == null || actor.isBlank() || actor.length() > 100)
      throw new IllegalArgumentException("도면을 등록할 사용자 정보를 확인해 주세요.");
    int pageCount = ProductDrawingXmlValidator.validate(request.xml());
    // Both target kinds lock the parent product, matching creation/stock/deletion lock ordering.
    // The parent already exists even when no drawing row exists, protecting the first save too.
    Target target = target(kind, id, true);
    ProductDrawing drawing = findDrawing(target);
    if (drawing == null) {
      if (request.expectedVersion() != null) throw conflict();
      drawing = new ProductDrawing();
      drawing.setProduct(target.actual() == null ? target.product() : null);
      drawing.setActual(target.actual());
      drawing.setCreatedBy(actor);
    } else if (!Objects.equals(request.expectedVersion(), drawing.getRowVersion())) {
      throw conflict();
    }
    drawing.setDocumentXml(request.xml());
    drawing.setPageCount(pageCount);
    drawing.setUpdatedBy(actor);
    // Flush before building the response so the client receives the incremented @Version.
    return view(target, drawings.saveAndFlush(drawing));
  }

  @Transactional(readOnly = true)
  public TemplatePage templates(String q, int page, int size) {
    if (page < 0 || page > 100_000 || size < 1 || size > 50)
      throw new IllegalArgumentException("도면 목록의 페이지와 조회 수를 확인해 주세요.");
    String query = q == null ? "" : q.trim();
    if (query.length() > 160)
      throw new IllegalArgumentException("도면 검색어는 160자 이하로 입력해 주세요.");
    String pattern = query.isEmpty() ? "" : "%" + query.toLowerCase(Locale.ROOT)
        .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    Long targetId = null;
    if (query.matches("[0-9]{1,19}")) {
      try { targetId = Long.valueOf(query); }
      catch (NumberFormatException ignored) { /* A large numeric code is still searchable text. */ }
    }
    var slice = drawings.findTemplates(pattern, targetId, PageRequest.of(page, size));
    var items = slice.getContent().stream().map(row -> {
      boolean standard = row.getStandardId() != null;
      return new TemplateView(
          standard ? "products" : "actuals",
          standard ? row.getStandardId() : row.getActualId(), row.getProductId(),
          label(row.getProductName(), standard ? row.getCatalogCode() : row.getActualCode()),
          row.getVersion(), row.getUpdatedAt(), row.getPageCount());
    }).toList();
    return new TemplatePage(items, slice.hasNext());
  }

  private Target target(String kind, Long id, boolean lock) {
    if (id == null || id <= 0) throw new IllegalArgumentException("도면 대상의 ID를 확인해 주세요.");
    if ("products".equals(kind)) {
      ProductMaster product = (lock ? products.findForUpdate(id) : products.findById(id))
          .orElseThrow(() -> new NoSuchElementException("규격 제품을 찾을 수 없습니다."));
      if (product.isNonStandard())
        throw new IllegalArgumentException("비규격 제품은 재고관리용 실 제품에 도면을 등록해 주세요.");
      return new Target(kind, id, product, null);
    }
    if (!"actuals".equals(kind))
      throw new IllegalArgumentException("도면 대상은 규격 제품 또는 비규격 실 제품이어야 합니다.");
    if (lock) {
      // Fetch only the parent ID first: do not place a possibly deleted/stale actual into the context.
      var parents = entityManager.createQuery(
          "select a.product.id from ProductActual a where a.id = :id", Long.class)
          .setParameter("id", id).getResultList();
      if (parents.isEmpty()) throw new NoSuchElementException("실 제품을 찾을 수 없습니다.");
      products.findForUpdate(parents.get(0))
          .orElseThrow(() -> new NoSuchElementException("실 제품의 원제품을 찾을 수 없습니다."));
    }
    ProductActual actual = actuals.findById(id)
        .orElseThrow(() -> new NoSuchElementException("실 제품을 찾을 수 없습니다."));
    ProductMaster product = actual.getProduct();
    if (!product.isNonStandard())
      throw new IllegalArgumentException("비규격 제품에 소속된 실 제품만 도면을 등록할 수 있습니다.");
    return new Target(kind, id, product, actual);
  }

  private ProductDrawing findDrawing(Target target) {
    return (target.actual() == null ? drawings.findByProductId(target.id())
        : drawings.findByActualId(target.id())).orElse(null);
  }

  private static DrawingView view(Target target, ProductDrawing drawing) {
    String code = target.actual() == null ? target.product().getCatalogCode() : target.actual().getCode();
    return new DrawingView(target.kind(), target.id(), target.product().getId(),
        label(target.product().getProductName(), code),
        drawing == null ? null : drawing.getDocumentXml(),
        drawing == null ? null : drawing.getRowVersion(),
        drawing == null ? null : drawing.getUpdatedAt(),
        drawing == null ? null : drawing.getUpdatedBy(),
        drawing == null ? 0 : drawing.getPageCount());
  }

  private static String label(String name, String code) {
    return name + (code == null || code.isBlank() ? "" : " · " + code);
  }

  private static IllegalStateException conflict() {
    return new IllegalStateException("다른 사용자가 먼저 도면을 등록·수정했습니다. 기존 작업을 내려받은 뒤 최신 도면을 다시 불러와 주세요.");
  }

  private record Target(String kind, Long id, ProductMaster product, ProductActual actual) {}
}
