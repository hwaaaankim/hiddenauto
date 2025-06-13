package com.dev.HiddenBATHAuto.model.task;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tb_product_mark")
@Data
public class ProductMark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 옵션 JSON (원본)
    @Lob
    private String optionJson;

    // 번역된 옵션 JSON (label 포함)
    @Lob
    private String localizedOptionJson;

    // 멤버 연관관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    @JsonBackReference
    private Member member;
}
