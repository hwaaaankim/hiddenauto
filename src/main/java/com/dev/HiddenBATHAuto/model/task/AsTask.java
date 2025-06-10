package com.dev.HiddenBATHAuto.model.task;

import java.time.LocalDateTime;
import java.util.List;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.Team;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "tb_as_task")
@Data
public class AsTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Member requestedBy;
    
    private String subject;
    // 우편번호
    private String zipCode;

    // 행정구역
    private String doName;   // ex: 경기도
    private String siName;   // ex: 용인시
    private String guName;   // ex: 수지구

    // 주소
    private String roadAddress;     // ex: 경기도 용인시 수지구 죽전로 55
    private String detailAddress;   // ex: 302동 1502호
    
    private String reason;
    private int price;
    private String asComment;
    // 신규 필드 추가
    private String productName;
    private String productSize;
    private String productColor;
    private String productOptions;   // JSON 문자열로 저장하거나 단일 문자열로 처리
    private String onsiteContact;
    
    
    @Enumerated(EnumType.STRING)
    private AsStatus status;

    @ManyToOne
    private Team assignedTeam;

    @ManyToOne
    private Member assignedHandler;

    private LocalDateTime requestedAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
    private LocalDateTime asProcessDate;

    @OneToMany(mappedBy = "asTask", cascade = CascadeType.ALL)
    private List<AsHistory> historyLogs;

    @JsonIgnore
    @OneToMany(mappedBy = "asTask", cascade = CascadeType.ALL)
    private List<AsImage> images; // 모든 이미지 (type 구분 포함)

    // type = "REQUEST" 인 이미지만 반환
    public List<AsImage> getRequestImages() {
        if (images == null) return List.of();
        return images.stream()
                     .filter(img -> "REQUEST".equalsIgnoreCase(img.getType()))
                     .toList();
    }

    // type = "RESULT" 인 이미지만 반환
    public List<AsImage> getResultImages() {
        if (images == null) return List.of();
        return images.stream()
                     .filter(img -> "RESULT".equalsIgnoreCase(img.getType()))
                     .toList();
    }
}
