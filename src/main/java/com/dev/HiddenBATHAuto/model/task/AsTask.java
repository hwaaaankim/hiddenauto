package com.dev.HiddenBATHAuto.model.task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.dev.HiddenBATHAuto.enums.AsBillingTarget;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.auth.Team;
import com.dev.HiddenBATHAuto.model.task.as.AsVideo;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Member requestedBy;

    /** 고객이 남기는 제목(요청 내용 요약) */
    private String subject;

    /** 고객 성함 - AS 신청 시 입력 */
    private String customerName;

    /** 우편번호 */
    private String zipCode;

    /** 행정구역 */
    private String doName;
    private String siName;
    private String guName;

    /** 주소 */
    private String roadAddress;
    private String detailAddress;

    /** 증상 상세 설명 */
    private String reason;

    /** 0이면 미정으로 간주 */
    private int price;

    private String asComment;

    /** 관리자 내부 메모 */
    private String adminMemo;

    /** 제품 정보 */
    private String productName;
    private String productSize;
    private String productColor;

    /** JSON 문자열 또는 단일 문자열 */
    private String productOptions;

    /** 현장 연락처 */
    private String onsiteContact;

    /** 담당자용 메모 */
    @Column(columnDefinition = "TEXT")
    private String handlerMemo;

    /** 방문예정시간 */
    private LocalTime visitPlannedTime;

    @Enumerated(EnumType.STRING)
    private AsStatus status;

    @ManyToOne
    private Team assignedTeam;

    @ManyToOne
    private Member assignedHandler;

    /** =========================
     * 신규 추가 필드
     * ========================= */
    private LocalDate purchaseDate;

    @Column(length = 100)
    private String applicantName;

    @Column(length = 30)
    private String applicantPhone;

    @Column(length = 150)
    private String applicantEmail;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AsBillingTarget billingTarget;

    @Column(name = "payment_collected", nullable = false)
    private boolean paymentCollected = false;
    
    private LocalDateTime requestedAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
    private LocalDateTime asProcessDate;

    @OneToMany(mappedBy = "asTask", cascade = CascadeType.ALL)
    private List<AsHistory> historyLogs = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "asTask", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AsImage> images = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "asTask", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AsVideo> videos = new ArrayList<>();

    /** type = REQUEST 인 이미지만 반환 */
    public List<AsImage> getRequestImages() {
        if (images == null || images.isEmpty()) return Collections.emptyList();
        return images.stream()
                .filter(img -> img != null && "REQUEST".equalsIgnoreCase(img.getType()))
                .collect(Collectors.toList());
    }

    /** type = RESULT 인 이미지만 반환 */
    public List<AsImage> getResultImages() {
        if (images == null || images.isEmpty()) return Collections.emptyList();
        return images.stream()
                .filter(img -> img != null && "RESULT".equalsIgnoreCase(img.getType()))
                .collect(Collectors.toList());
    }

    /** type = REQUEST 인 동영상만 반환 */
    public List<AsVideo> getRequestVideos() {
        if (videos == null || videos.isEmpty()) return Collections.emptyList();
        return videos.stream()
                .filter(v -> v != null && "REQUEST".equalsIgnoreCase(v.getType()))
                .collect(Collectors.toList());
    }

    /** type = RESULT 인 동영상만 반환 */
    public List<AsVideo> getResultVideos() {
        if (videos == null || videos.isEmpty()) return Collections.emptyList();
        return videos.stream()
                .filter(v -> v != null && "RESULT".equalsIgnoreCase(v.getType()))
                .collect(Collectors.toList());
    }

    /** 처리상태 한글 라벨 */
    public String getStatusLabelKr() {
        if (status == null) return "-";
        return status.getLabelKr();
    }

    public String getSubjectSafe() {
        if (subject == null) return "-";
        String s = subject.trim();
        return s.isEmpty() ? "-" : s;
    }

    public String getCustomerNameSafe() {
        if (customerName == null) return "-";
        String s = customerName.trim();
        return s.isEmpty() ? "-" : s;
    }

    public String getAdminMemoSafe() {
        if (adminMemo == null) return "-";
        String s = adminMemo.trim();
        return s.isEmpty() ? "-" : s;
    }

    public String getHandlerMemoSafe() {
        if (handlerMemo == null) return "-";
        String s = handlerMemo.trim();
        return s.isEmpty() ? "-" : s;
    }

    public String getRequestedCompanyNameSafe() {
        if (requestedBy == null) return "-";
        if (requestedBy.getCompany() == null) return "-";

        String companyName = requestedBy.getCompany().getCompanyName();
        if (companyName == null) return "-";

        String s = companyName.trim();
        return s.isEmpty() ? "-" : s;
    }

    public String getApplicantNameSafe() {
        if (applicantName == null) return "-";
        String s = applicantName.trim();
        return s.isEmpty() ? "-" : s;
    }

    public String getApplicantPhoneSafe() {
        if (applicantPhone == null) return "-";
        String s = applicantPhone.trim();
        return s.isEmpty() ? "-" : s;
    }

    public String getApplicantEmailSafe() {
        if (applicantEmail == null) return "-";
        String s = applicantEmail.trim();
        return s.isEmpty() ? "-" : s;
    }

    public String getBillingTargetLabelSafe() {
        if (billingTarget == null) return "-";
        return billingTarget.getLabelKr();
    }

    public String getVisitPlannedTimeText() {
        if (visitPlannedTime == null) return "-";
        return visitPlannedTime.format(TIME_FORMATTER);
    }

    public String getCustomerStatusText() {
        if (status == null) return "신청중";

        switch (status) {
            case COMPLETED:
                return "신청완료";
            case CANCELED:
                return "취소";
            case REQUESTED:
            case IN_PROGRESS:
            default:
                return "신청중";
        }
    }

    public String getCustomerStatusBadgeClass() {
        if (status == null) return "bg-warning color-white";

        switch (status) {
            case COMPLETED:
                return "bg-success color-white";
            case CANCELED:
                return "bg-danger color-white";
            case REQUESTED:
            case IN_PROGRESS:
            default:
                return "bg-warning color-white";
        }
    }
    
    public String getPaymentCollectedLabelKr() {
        return paymentCollected ? "수납완료" : "미수납";
    }
    
    public String getProductNameSafe() {
        if (productName == null) return "-";
        String s = productName.trim();
        return s.isEmpty() ? "-" : s;
    }

    public String getOnsiteContactSafe() {
        if (onsiteContact == null) return "-";
        String s = onsiteContact.trim();
        return s.isEmpty() ? "-" : s;
    }
}