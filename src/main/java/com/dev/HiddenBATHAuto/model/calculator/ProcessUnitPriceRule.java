package com.dev.HiddenBATHAuto.model.calculator;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.enums.calculator.ProcessPriceRuleType;
import com.dev.HiddenBATHAuto.model.process.ProcessUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "tb_process_unit_price_rule",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_process_unit_price_rule_key",
                        columnNames = {"unit_id", "rule_key"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProcessUnitPriceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 가격 규칙이 연결된 UNIT.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private ProcessUnit unit;

    @Column(name = "rule_key", nullable = false, length = 80)
    private String ruleKey;

    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 50)
    private ProcessPriceRuleType ruleType;

    @Column(name = "enabled_yn", nullable = false)
    private boolean enabledYn = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * 타입별 상세 가격 규칙 JSON.
     */
    @Lob
    @Column(name = "rule_json", nullable = false)
    private String ruleJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}