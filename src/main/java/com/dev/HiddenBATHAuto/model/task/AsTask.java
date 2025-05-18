package com.dev.HiddenBATHAuto.model.task;

import java.time.LocalDateTime;
import java.util.List;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.Team;

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
    
    @Enumerated(EnumType.STRING)
    private AsStatus status;

    @ManyToOne
    private Team assignedTeam;

    @ManyToOne
    private Member assignedHandler;

    private LocalDateTime requestedAt = LocalDateTime.now();
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "asTask", cascade = CascadeType.ALL)
    private List<AsHistory> historyLogs;

    @OneToMany(mappedBy = "asTask", cascade = CascadeType.ALL)
    private List<AsImage> requestImages; // 고객 요청 시 업로드된 이미지

    @OneToMany(mappedBy = "asTask", cascade = CascadeType.ALL)
    private List<AsImage> resultImages; // AS 완료 후 업로드 이미지
}
