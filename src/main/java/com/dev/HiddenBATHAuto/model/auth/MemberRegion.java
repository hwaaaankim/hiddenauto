package com.dev.HiddenBATHAuto.model.auth;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tb_member_region")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Province province;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    private City city; // optional: 시 선택 안할 수도 있음

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    private District district; // optional: 구 선택 안할 수도 있음

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    @JsonBackReference("member-memberRegion")   
    private Member member;
}
