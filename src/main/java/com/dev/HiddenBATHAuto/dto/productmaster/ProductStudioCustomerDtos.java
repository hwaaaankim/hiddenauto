package com.dev.HiddenBATHAuto.dto.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Customer browsing is separate from the management list's OR option filters. */
public final class ProductStudioCustomerDtos {
  private ProductStudioCustomerDtos() {}

  public record BrowseRequest(
      boolean nonStandard,
      Map<Long, String> selections,
      Map<Long, List<String>> filters,
      int page,
      int size,
      Long productId,
      String token) {}

  public record BrowseGroup(
      Long groupId,
      String key,
      String role,
      String label,
      String question,
      String guide,
      String control,
      List<CatalogOption> options,
      List<AssetView> assets) {}

  public record ProductCard(
      Long id,
      String productName,
      String catalogCode,
      String token,
      boolean nonStandard,
      String status,
      BigDecimal productionHours,
      BigDecimal unitPrice,
      int stock,
      List<AssetView> assets,
      List<String> tags) {}

  public record BrowseResult(
      BrowseGroup next,
      List<BrowseGroup> filters,
      List<ProductCard> products,
      long total,
      long scopeTotal,
      int page,
      int totalPages) {}

  public record CustomerGroup(
      Long id,
      String key,
      Labels labels,
      String role,
      boolean askQuestion,
      boolean priceImpact) {}

  public record CustomerDetail(
      String productName,
      String catalogCode,
      boolean nonStandard,
      String status,
      String description,
      int stock,
      BigDecimal productionHours,
      BigDecimal unitPrice,
      Long faqTopicId,
      List<AssetView> assets,
      List<AssetView> processAssets,
      List<CustomerGroup> groups,
      Process process,
      Map<String, Answer> fixedAnswers) {}
}
