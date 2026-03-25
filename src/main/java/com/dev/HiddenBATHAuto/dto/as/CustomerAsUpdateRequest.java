package com.dev.HiddenBATHAuto.dto.as;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.dev.HiddenBATHAuto.enums.AsBillingTarget;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerAsUpdateRequest {

    private String customerName;

    private String zipCode;
    private String doName;
    private String siName;
    private String guName;

    private String roadAddress;
    private String detailAddress;

    private String onsiteContact;

    private String productName;
    private String productSize;
    private String productColor;
    private String productOptions;

    private String subject;
    private String reason;

    /** 납품일자 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate purchaseDate;

    /** 접수 담당자 정보 */
    private String applicantName;
    private String applicantPhone;
    private String applicantEmail;

    /** 비용 청구 주체 */
    private AsBillingTarget billingTarget;
}