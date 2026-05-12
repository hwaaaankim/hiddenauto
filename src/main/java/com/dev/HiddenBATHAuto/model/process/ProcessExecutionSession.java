package com.dev.HiddenBATHAuto.model.process;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.dev.HiddenBATHAuto.enums.process.ProcessExecutionStatus;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "tb_process_execution_session",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_process_execution_session_key", columnNames = "session_key")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ProcessExecutionSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_key", nullable = false, length = 80)
    private String sessionKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_id", nullable = false)
    private ProcessDefinition process;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProcessExecutionStatus status = ProcessExecutionStatus.IN_PROGRESS;

    @Column(name = "current_unit_key", length = 80)
    private String currentUnitKey;

    @Column(name = "actor_type", length = 30)
    private String actorType;

    @Column(name = "actor_member_id")
    private Long actorMemberId;

    @Column(name = "actor_name", length = 100)
    private String actorName;

    @Column(name = "actor_phone", length = 30)
    private String actorPhone;

    @Lob
    @Column(name = "deferred_unit_keys_json")
    private String deferredUnitKeysJson;

    @Column(name = "calculated_price_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal calculatedPriceAmount = BigDecimal.ZERO;

    @Lob
    @Column(name = "price_result_json")
    private String priceResultJson;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @BatchSize(size = 50)
    private List<ProcessExecutionAnswer> answers = new ArrayList<>();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void complete() {
        this.status = ProcessExecutionStatus.COMPLETED;
        this.currentUnitKey = null;
        this.completedAt = LocalDateTime.now();
    }

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