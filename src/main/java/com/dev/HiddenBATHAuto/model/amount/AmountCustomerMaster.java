package com.dev.HiddenBATHAuto.model.amount;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tb_amount_customer_master", indexes = {
        @Index(name = "idx_tb_amount_customer_master_code", columnList = "customer_code"),
        @Index(name = "idx_tb_amount_customer_master_name", columnList = "customer_name"),
        @Index(name = "idx_tb_amount_customer_master_updated", columnList = "updated_at")
})
public class AmountCustomerMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_code", length = 50)
    private String customerCode;
    @Column(name = "division", length = 30)
    private String division;
    @Column(name = "trade_type", length = 30)
    private String tradeType;
    @Column(name = "customer_name", length = 191)
    private String customerName;
    @Column(name = "business_name", length = 191)
    private String businessName;
    @Column(name = "business_no", length = 50)
    private String businessNo;
    @Lob
    @Column(name = "grade", columnDefinition = "TEXT")
    private String grade;
    @Lob
    @Column(name = "accounts_receivable", columnDefinition = "TEXT")
    private String accountsReceivable;
    @Lob
    @Column(name = "receivable", columnDefinition = "TEXT")
    private String receivable;
    @Lob
    @Column(name = "advance_payment", columnDefinition = "TEXT")
    private String advancePayment;
    @Lob
    @Column(name = "accounts_payable", columnDefinition = "TEXT")
    private String accountsPayable;
    @Lob
    @Column(name = "unpaid_amount", columnDefinition = "TEXT")
    private String unpaidAmount;
    @Lob
    @Column(name = "advance_received", columnDefinition = "TEXT")
    private String advanceReceived;
    @Lob
    @Column(name = "sub_business_no", columnDefinition = "TEXT")
    private String subBusinessNo;
    @Lob
    @Column(name = "corporation_no", columnDefinition = "TEXT")
    private String corporationNo;
    @Lob
    @Column(name = "ceo_name", columnDefinition = "TEXT")
    private String ceoName;
    @Lob
    @Column(name = "workplace_address1", columnDefinition = "TEXT")
    private String workplaceAddress1;
    @Lob
    @Column(name = "workplace_address2", columnDefinition = "TEXT")
    private String workplaceAddress2;
    @Lob
    @Column(name = "head_office_address1", columnDefinition = "TEXT")
    private String headOfficeAddress1;
    @Lob
    @Column(name = "head_office_address2", columnDefinition = "TEXT")
    private String headOfficeAddress2;
    @Lob
    @Column(name = "business_type", columnDefinition = "TEXT")
    private String businessType;
    @Lob
    @Column(name = "business_item", columnDefinition = "TEXT")
    private String businessItem;
    @Column(name = "telephone", length = 50)
    private String telephone;
    @Column(name = "fax", length = 50)
    private String fax;
    @Column(name = "mobile", length = 50)
    private String mobile;
    @Column(name = "actual_zip_code", length = 20)
    private String actualZipCode;
    @Lob
    @Column(name = "actual_address1", columnDefinition = "TEXT")
    private String actualAddress1;
    @Lob
    @Column(name = "actual_address2", columnDefinition = "TEXT")
    private String actualAddress2;
    @Lob
    @Column(name = "deposit_bank", columnDefinition = "TEXT")
    private String depositBank;
    @Lob
    @Column(name = "account_no", columnDefinition = "TEXT")
    private String accountNo;
    @Lob
    @Column(name = "account_holder", columnDefinition = "TEXT")
    private String accountHolder;
    @Lob
    @Column(name = "homepage", columnDefinition = "TEXT")
    private String homepage;
    @Lob
    @Column(name = "manager1_name", columnDefinition = "TEXT")
    private String manager1Name;
    @Lob
    @Column(name = "manager1_telephone", columnDefinition = "TEXT")
    private String manager1Telephone;
    @Lob
    @Column(name = "manager1_mobile", columnDefinition = "TEXT")
    private String manager1Mobile;
    @Lob
    @Column(name = "manager1_fax", columnDefinition = "TEXT")
    private String manager1Fax;
    @Lob
    @Column(name = "manager1_email", columnDefinition = "TEXT")
    private String manager1Email;
    @Lob
    @Column(name = "sms_send_yn", columnDefinition = "TEXT")
    private String smsSendYn;
    @Lob
    @Column(name = "fax_send_yn", columnDefinition = "TEXT")
    private String faxSendYn;
    @Lob
    @Column(name = "account_subject_code", columnDefinition = "TEXT")
    private String accountSubjectCode;
    @Lob
    @Column(name = "account_subject_name", columnDefinition = "TEXT")
    private String accountSubjectName;
    @Lob
    @Column(name = "category_name", columnDefinition = "TEXT")
    private String categoryName;
    @Lob
    @Column(name = "credit_account_subject_code", columnDefinition = "TEXT")
    private String creditAccountSubjectCode;
    @Lob
    @Column(name = "credit_account_subject_name", columnDefinition = "TEXT")
    private String creditAccountSubjectName;
    @Lob
    @Column(name = "transaction_category", columnDefinition = "TEXT")
    private String transactionCategory;
    @Lob
    @Column(name = "tax_type", columnDefinition = "TEXT")
    private String taxType;
    @Lob
    @Column(name = "sales_manager", columnDefinition = "TEXT")
    private String salesManager;
    @Lob
    @Column(name = "payment_type", columnDefinition = "TEXT")
    private String paymentType;
    @Lob
    @Column(name = "payment_book", columnDefinition = "TEXT")
    private String paymentBook;
    @Lob
    @Column(name = "dedicated_item_use_yn", columnDefinition = "TEXT")
    private String dedicatedItemUseYn;
    @Lob
    @Column(name = "transaction_item_use_yn", columnDefinition = "TEXT")
    private String transactionItemUseYn;
    @Lob
    @Column(name = "sales_price_type", columnDefinition = "TEXT")
    private String salesPriceType;
    @Lob
    @Column(name = "fixed_rate_yn", columnDefinition = "TEXT")
    private String fixedRateYn;
    @Lob
    @Column(name = "fixed_rate_percent", columnDefinition = "TEXT")
    private String fixedRatePercent;
    @Column(name = "use_status", length = 30)
    private String useStatus;
    @Lob
    @Column(name = "report_print_yn", columnDefinition = "TEXT")
    private String reportPrintYn;
    @Lob
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
    @Lob
    @Column(name = "manager2_name", columnDefinition = "TEXT")
    private String manager2Name;
    @Lob
    @Column(name = "manager2_telephone", columnDefinition = "TEXT")
    private String manager2Telephone;
    @Lob
    @Column(name = "manager2_mobile", columnDefinition = "TEXT")
    private String manager2Mobile;
    @Lob
    @Column(name = "manager2_fax", columnDefinition = "TEXT")
    private String manager2Fax;
    @Lob
    @Column(name = "manager2_email", columnDefinition = "TEXT")
    private String manager2Email;
    @Lob
    @Column(name = "manager3_name", columnDefinition = "TEXT")
    private String manager3Name;
    @Lob
    @Column(name = "manager3_telephone", columnDefinition = "TEXT")
    private String manager3Telephone;
    @Lob
    @Column(name = "manager3_mobile", columnDefinition = "TEXT")
    private String manager3Mobile;
    @Lob
    @Column(name = "manager3_fax", columnDefinition = "TEXT")
    private String manager3Fax;
    @Lob
    @Column(name = "manager3_email", columnDefinition = "TEXT")
    private String manager3Email;
    @Lob
    @Column(name = "manager4_name", columnDefinition = "TEXT")
    private String manager4Name;
    @Lob
    @Column(name = "manager4_telephone", columnDefinition = "TEXT")
    private String manager4Telephone;
    @Lob
    @Column(name = "manager4_mobile", columnDefinition = "TEXT")
    private String manager4Mobile;
    @Lob
    @Column(name = "manager4_fax", columnDefinition = "TEXT")
    private String manager4Fax;
    @Lob
    @Column(name = "manager4_email", columnDefinition = "TEXT")
    private String manager4Email;
    @Lob
    @Column(name = "manager5_name", columnDefinition = "TEXT")
    private String manager5Name;
    @Lob
    @Column(name = "manager5_telephone", columnDefinition = "TEXT")
    private String manager5Telephone;
    @Lob
    @Column(name = "manager5_mobile", columnDefinition = "TEXT")
    private String manager5Mobile;
    @Lob
    @Column(name = "manager5_fax", columnDefinition = "TEXT")
    private String manager5Fax;
    @Lob
    @Column(name = "manager5_email", columnDefinition = "TEXT")
    private String manager5Email;
    @Lob
    @Column(name = "search_text", columnDefinition = "TEXT")
    private String searchText;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
