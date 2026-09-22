package com.dev.HiddenBATHAuto.model.productmaster;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tb_pm_faq_topic")
@Getter
@Setter
public class ProductFaqTopic {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private long rowVersion;

  @Column(nullable = false, length = 160)
  private String title;

  @Column(length = 60)
  private String phone;

  @Column(length = 2000)
  private String link;

  @Column(name = "entries_json", nullable = false, columnDefinition = "LONGTEXT")
  private String entriesJson = "[]";

  @Column(nullable = false, length = 100)
  private String updatedBy;

  private java.time.LocalDateTime updatedAt = java.time.LocalDateTime.now();
}
