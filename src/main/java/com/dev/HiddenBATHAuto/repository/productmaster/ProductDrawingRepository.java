package com.dev.HiddenBATHAuto.repository.productmaster;

import com.dev.HiddenBATHAuto.model.productmaster.ProductDrawing;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductDrawingRepository extends JpaRepository<ProductDrawing, Long> {
  Optional<ProductDrawing> findByProductId(Long productId);

  Optional<ProductDrawing> findByActualId(Long actualId);

  // Scalar projection avoids loading the potentially large document XML into the picker.
  @Query("""
      select p.id as standardId, a.id as actualId,
             coalesce(p.id, ap.id) as productId,
             coalesce(p.productName, ap.productName) as productName,
             p.catalogCode as catalogCode, a.code as actualCode,
             d.rowVersion as version, d.updatedAt as updatedAt, d.pageCount as pageCount
      from ProductDrawing d
      left join d.product p
      left join d.actual a
      left join a.product ap
      where ((p.id is not null and p.nonStandard = false)
          or (a.id is not null and ap.nonStandard = true))
        and (:pattern = '' or lower(coalesce(p.productName, ap.productName)) like :pattern escape '!'
          or lower(coalesce(p.catalogCode, ap.catalogCode)) like :pattern escape '!'
          or lower(coalesce(p.productCode, ap.productCode)) like :pattern escape '!'
          or lower(a.code) like :pattern escape '!'
          or (:targetId is not null and (p.id = :targetId or a.id = :targetId or ap.id = :targetId)))
      order by d.updatedAt desc, d.id desc
      """)
  Slice<TemplateProjection> findTemplates(@Param("pattern") String pattern,
      @Param("targetId") Long targetId, Pageable pageable);

  interface TemplateProjection {
    Long getStandardId();
    Long getActualId();
    Long getProductId();
    String getProductName();
    String getCatalogCode();
    String getActualCode();
    Long getVersion();
    LocalDateTime getUpdatedAt();
    Integer getPageCount();
  }
}
